package com.tbread

import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps

object PcapHandler {
    fun printDevices(){
        try{
            var devices = Pcaps.findAllDevs()
            for ((i, device) in devices.withIndex()) {
                println(i.toString()+" - "+device.description)
            }
        } catch (e:PcapNativeException){
            println("네트워크 기기 체크중 오류가 발생했습니다.")
        }
    }

    fun getDeviceSize():Int{
        return Pcaps.findAllDevs().size
    }
}