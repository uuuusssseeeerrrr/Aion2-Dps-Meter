package com.tbread.webview

import com.sun.jna.platform.win32.*
import com.sun.jna.ptr.IntByReference
import com.tbread.entity.CaptureKeyPayload
import com.tbread.entity.NativeKeyPayload
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.Platform
import javafx.scene.web.WebEngine
import kotlinx.serialization.json.Json
import java.io.File

class KeyHookEvent(private val engine: WebEngine) {
    private val logger = KotlinLogging.logger {}
    private val hotkeyId = 1
    private val hotkeyTargetProcess = "Aion2.exe"
    private val hotkeyTargetTitle = "Aion2"
    private val pmRemoveFlag = 0x0001

    @Volatile
    private var lastHotkeyMods = 0

    @Volatile
    private var lastHotkeyKey = 0

    @Volatile
    private var isAion2ForegroundCached = false

    @Volatile
    private var registeredHotkeyMods = 0

    @Volatile
    private var registeredHotkeyKey = 0

    @Volatile
    private var hotkeyThread: Thread? = null

    @Volatile
    private var hotkeyRunning = false

    private val storageFile = File(
        System.getProperty("user.home"),
        "AppData/Local/aion2meter4j/hotkey.json"
    )

    init {
        try {
            // 저장한 파일이 있으면 초기에 등록
            if (storageFile.exists()) {
                val payload = Json.decodeFromString<CaptureKeyPayload>(storageFile.readText())
                lastHotkeyMods = if (payload.ctrl) WinUser.MOD_CONTROL else if (payload.alt) WinUser.MOD_ALT else 0
                lastHotkeyKey = payload.keyCode
                updateHotkeyRegistration(true)
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to load storage" }
        }
    }

    fun setHotkey(modifiers: Int, keyCode: Int) {
        logger.info { "setHotkey called mods=$modifiers vk=$keyCode" }
        lastHotkeyMods = modifiers
        lastHotkeyKey = keyCode
        // Aion2 포커스일 때만 등록되도록 현재 상태로 갱신.
        updateHotkeyRegistration(isAion2ForegroundCached)
    }

    fun getCurrentHotKey(): String {
        return when (registeredHotkeyMods) {
            WinUser.MOD_CONTROL -> "Ctrl+${registeredHotkeyKey.toChar()}"
            WinUser.MOD_ALT -> "Alt+${registeredHotkeyKey.toChar()}"
            else -> ""
        }
    }

    private fun startHotkeyThread(modifiers: Int, keyCode: Int) {
        stopHotkeyThread()
        hotkeyRunning = true
        hotkeyThread = Thread {
            val registeredMods = modifiers or WinUser.MOD_NOREPEAT
            val registered = User32.INSTANCE.RegisterHotKey(null, hotkeyId, registeredMods, keyCode)
            if (!registered) {
                val err = Kernel32.INSTANCE.GetLastError()
                logger.warn { "RegisterHotKey 실패 mods=$registeredMods vk=$keyCode err=$err" }
            } else {
                logger.info { "RegisterHotKey 등록 mods=$registeredMods vk=$keyCode" }
            }

            val msg = WinUser.MSG()
            while (hotkeyRunning) {
                while (User32.INSTANCE.PeekMessage(msg, null, 0, 0, pmRemoveFlag)) {
                    if (msg.message == WinUser.WM_HOTKEY) {
                        val foreground = User32.INSTANCE.GetForegroundWindow()
                        if (foreground == null || !isAion2Window(foreground)) {
                            continue
                        }

                        val lParam = msg.lParam.toInt()
                        val recvMods = lParam and 0xFFFF
                        val recvVk = (lParam ushr 16) and 0xFFFF
                        val payload = NativeKeyPayload(
                            keyCode = recvVk,
                            keyText = java.awt.event.KeyEvent.getKeyText(recvVk),
                            ctrl = (recvMods and WinUser.MOD_CONTROL) != 0,
                            alt = (recvMods and WinUser.MOD_ALT) != 0,
                            shift = (recvMods and WinUser.MOD_SHIFT) != 0,
                            meta = (recvMods and WinUser.MOD_WIN) != 0
                        )

                        logger.info { "hotkey received mods=$recvMods vk=$recvVk keyText=$payload.keyText" }
                        dispatchResetHotKey()
                    }
                }

                Thread.sleep(25)
            }

            User32.INSTANCE.UnregisterHotKey(null, hotkeyId)
        }.apply {
            isDaemon = true
            name = "hotkey-thread"
            start()
        }
    }

    private fun getWindowTitle(hwnd: WinDef.HWND): String {
        val buffer = CharArray(256)
        val len = User32.INSTANCE.GetWindowText(hwnd, buffer, buffer.size)
        return if (len > 0) String(buffer, 0, len) else ""
    }

    private fun isAion2Window(hwnd: WinDef.HWND): Boolean {
        val title = getWindowTitle(hwnd)
        if (title.contains(hotkeyTargetTitle, ignoreCase = true)) {
            return true
        }
        val pidRef = IntByReference()
        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
        val pidValue = pidRef.value
        if (pidValue <= 0) {
            return false
        }
        val processName = getProcessName(pidValue) ?: return false
        return processName.equals(hotkeyTargetProcess, ignoreCase = true)
    }

    private fun getProcessName(pid: Int): String? {
        val processHandle = Kernel32.INSTANCE.OpenProcess(
            WinNT.PROCESS_QUERY_LIMITED_INFORMATION or WinNT.PROCESS_VM_READ,
            false,
            pid
        ) ?: return null
        return try {
            val buffer = CharArray(260)
            val ok = Psapi.INSTANCE.GetProcessImageFileName(
                processHandle,
                buffer,
                buffer.size
            )
            if (ok > 0) {
                val fullPath = String(buffer, 0, ok)
                fullPath.substringAfterLast('\\', fullPath)
            } else {
                null
            }
        } finally {
            Kernel32.INSTANCE.CloseHandle(processHandle)
        }
    }

    private fun stopHotkeyThread() {
        hotkeyRunning = false
        hotkeyThread?.interrupt()
        hotkeyThread = null
        registeredHotkeyMods = 0
        registeredHotkeyKey = 0
    }

    @Synchronized
    private fun updateHotkeyRegistration(shouldRegister: Boolean) {
        if (!shouldRegister) {
            stopHotkeyThread()
            return
        }
        if (lastHotkeyMods == 0 && lastHotkeyKey == 0) {
            stopHotkeyThread()
            return
        }
        if (hotkeyRunning &&
            lastHotkeyMods == registeredHotkeyMods &&
            lastHotkeyKey == registeredHotkeyKey
        ) {
            return
        }

        registerHotkey(lastHotkeyMods, lastHotkeyKey)
    }

    private fun registerHotkey(modifiers: Int, keyCode: Int) {
        if (!System.getProperty("os.name").lowercase().contains("windows")) {
            logger.info { "전역 핫키는 Windows에서만 활성화됩니다." }
            return
        }

        if (modifiers == 0 && keyCode == 0) {
            stopHotkeyThread()
            return
        }

        val captureKey = CaptureKeyPayload(
            keyCode,
            "",
            "",
            modifiers == WinUser.MOD_CONTROL,
            modifiers == WinUser.MOD_ALT,
            shift = false,
            meta = false
        )

        // 디렉토리가 없으면 생성
        storageFile.parentFile?.mkdirs()
        storageFile.writeText(Json.encodeToString(captureKey))

        startHotkeyThread(modifiers, keyCode)
        registeredHotkeyMods = modifiers
        registeredHotkeyKey = keyCode
    }

    private fun dispatchResetHotKey() {
        Platform.runLater {
            try {
                engine.executeScript(
                    "window.dispatchEvent(new CustomEvent('nativeResetHotKey'));"
                )
            } catch (e: Exception) {
                logger.debug(e) { "nativeResetHotKey 이벤트 전달 실패" }
            }
        }
    }
}
