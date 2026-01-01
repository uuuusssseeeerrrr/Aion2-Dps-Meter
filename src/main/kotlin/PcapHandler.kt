package com.tbread

import org.pcap4j.core.BpfProgram
import org.pcap4j.core.PacketListener
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface
import org.pcap4j.core.Pcaps
import org.pcap4j.packet.Packet
import org.pcap4j.packet.TcpPacket
import org.pcap4j.util.ByteArrays
import java.util.*
import kotlin.system.exitProcess

object PcapHandler {

    private val SERVER_IP = PropertyHandler.getProperty("server.ip")
    private val SERVER_PORT = PropertyHandler.getProperty("server.port")
    //추후 배포시엔 디폴트값 넣은채로 빌드하거나, deviceIdx 처럼 자동 저장 추가하기

    private val TIMEOUT_WAIT_TIME = PropertyHandler.getProperty("server.timeout", "10")?.toInt()!!
    private val MAX_SNAPSHOT_SIZE = PropertyHandler.getProperty("server.maxSnapshotSize", "65536")?.toInt()!!

    private val nif by lazy {
        devices[PropertyHandler.getProperty("device")!!.toInt()]
    }

    private val PCAP_HANDLE by lazy {
        nif.openLive(MAX_SNAPSHOT_SIZE, PcapNetworkInterface.PromiscuousMode.PROMISCUOUS, TIMEOUT_WAIT_TIME)
    }

    private val devices by lazy {
        try {
            Pcaps.findAllDevs()
        } catch (e: PcapNativeException) {
            println("Pcap 핸들러 초기화 실패 : 네트워크 기기 검색 실패")
            exitProcess(2)
        }
    }

    private val MAGIC_PACKET = byteArrayOf(0x06.toByte(),0x00.toByte(),0x36.toByte())


    fun printDevices() {
        for ((i, device) in devices.withIndex()) {
            println(i.toString() + " - " + device.description + " : " + device.addresses)
        }
    }

    fun getDeviceSize(): Int {
        return devices.size
    }

    fun pcapStart() {
        val filter = "src host $SERVER_IP and port $SERVER_PORT"
        PCAP_HANDLE.setFilter(filter, BpfProgram.BpfCompileMode.OPTIMIZE)
        println("캡쳐시작 필터구문 : $filter")

        val listener = PacketListener { packet ->
            if (packet.contains(TcpPacket::class.java)) {
                val tcpPacket = packet.get(TcpPacket::class.java)
                val payload = tcpPacket.payload
                if (payload != null) {
                    val data = payload.rawData
                    if (data.isNotEmpty()) {
                        println(ByteArrays.toHexString(data, " "))
                    }
                }
            }
        }
        try {
            PCAP_HANDLE.use { handle ->
                handle.loop(-1, listener)
            }
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }


}