package com.tbread.webview

import com.tbread.DpsCalculator
import com.tbread.aion2meter4j.BuildConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import javafx.application.HostServices
import javafx.application.Platform
import javafx.scene.web.WebEngine
import javafx.stage.Stage
import kotlin.system.exitProcess

class JSBridge(
    private val stage: Stage,
    private val dpsCalculator: DpsCalculator,
    private val hostServices: HostServices,
    engine: WebEngine
) {
    private val logger = KotlinLogging.logger {}
    private val hookKeyEvent = HookKeyEvent(engine)

    fun moveWindow(x: Double, y: Double) {
        stage.x = x
        stage.y = y
    }

    fun openBrowser(url: String) {
        try {
            hostServices.showDocument(url)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exitApp() {
        Platform.exit()
        exitProcess(0)
    }

    fun resetDps() {
        dpsCalculator.resetDataStorage()
    }

    fun printLog(message: String) {
        logger.info { message }
    }

    fun getCurrentVersion(): String {
        return BuildConfig.APP_VERSION
    }

    fun getLatestVersion(): String {
        return GitReleaseParser.currentVersion.ifEmpty { BuildConfig.APP_VERSION }
    }

    fun setHotkey(modifiers: Int, keyCode: Int) {
        logger.info { "setHotkey called mods=$modifiers vk=$keyCode" }
        hookKeyEvent.setHotkey(modifiers, keyCode)
    }

    fun getCurrentHotKey(): String {
        return hookKeyEvent.getCurrentHotKey()
    }
}