package com.tbread

import com.tbread.config.PcapCapturerConfig
import com.tbread.packet.PcapCapturer
import com.tbread.packet.StreamAssembler
import com.tbread.packet.StreamProcessor
import com.tbread.webview.BrowserApp
import com.tbread.webview.GitReleaseParser
import javafx.application.Platform
import javafx.stage.Stage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

fun main() {
    if (!isAdmin()) {
        elevatePrivileges()
        return
    }

    runBlocking {
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            println("thread dead ${t.name}")
            e.printStackTrace()
        }
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        val config = PcapCapturerConfig.loadFromProperties()

        val dataStorage = DataStorage()
        val processor = StreamProcessor(dataStorage)
        val assembler = StreamAssembler(processor)
        val capturer = PcapCapturer(config, channel)
        val calculator = DpsCalculator(dataStorage)

        launch(Dispatchers.Default) {
            for (chunk in channel) {
                assembler.processChunk(chunk)
            }
        }

        launch(Dispatchers.Default) {
            GitReleaseParser.parse()
        }

        launch(Dispatchers.IO) {
            capturer.start()
        }

        Platform.startup {
            val browserApp = BrowserApp(calculator)
            browserApp.start(Stage())
        }
    }
}

// 관리자 권한 확인 (net session 명령어가 성공하면 관리자임)
fun isAdmin(): Boolean {
    return try {
        // "net session" 명령어를 리스트 형태로 전달
        val process = ProcessBuilder("net", "session").start()

        // 프로세스가 끝날 때까지 대기 (최대 5초)
        process.waitFor(5, TimeUnit.SECONDS)

        // 종료 코드가 0이면 관리자 권한임
        process.exitValue() == 0
    } catch (_: Exception) {
        false
    }
}

// 관리자 권한으로 자기 자신을 다시 실행
fun elevatePrivileges() {
    // MSI로 설치된 경우 실행 파일 이름을 정확히 지정해야 합니다.
    val exePath = System.getProperty("compose.application.configure.path")
        ?: "${System.getProperty("user.dir")}\\aion2meter4j.exe"

    // PowerShell을 사용하여 "관리자 권한으로 실행(RunAs)" 호출
    val command = "Start-Process '$exePath' -Verb RunAs"

    try {
        ProcessBuilder("powershell", "-Command", command).start()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    // 현재 (일반 권한) 프로세스 종료
    exitProcess(0)
}
