package com.source.hmileak.tracer

import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.SystemInfo

/**
 *  物理内存追踪
 */
class PhysicalMemoryTracer : OOMTracker(){

    companion object{
        private const val TAG:String = "PhysicalMemoryTracer"
    }


    override fun track(): Boolean {
       var info = SystemInfo.memInfo
        android.util.Log.e("PhysicalMemory","info:"+info.rate+"====="+monitorConfig.deviceMemoryThreshold)

        when{
            info.rate < monitorConfig!!.deviceMemoryThreshold -> {
                Log.e(TAG, "oom meminfo.rate < " +
                        "${monitorConfig!!.deviceMemoryThreshold * 100}%")
            }
            info.rate < 0.10f ->{
                Log.i(TAG, "oom meminfo.rate < 10.0%")
            }
            info.rate<0.15f ->{
                Log.i(TAG, "oom meminfo.rate < 15.0%")
            }
            info.rate<0.20f ->{
                Log.i(TAG, "oom meminfo.rate < 20.0%")
            }
            info.rate<0.30f ->{
                Log.i(TAG, "oom meminfo.rate < 30.0%")
            }
        }
        return false
    }

    override fun reason() = "reason_lmk_OOM"

    override fun reset() {

    }
}