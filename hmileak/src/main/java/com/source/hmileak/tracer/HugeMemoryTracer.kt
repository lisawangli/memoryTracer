package com.source.hmileak.tracer

import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.SizeUnit

/**
 * 大对象的检测
 */
class HugeMemoryTracer: OOMTracker() {

    companion object{
        private const val REASON_HIGH_WATERMARK = "high_watermark"
        private const val REASON_HUGE_DELTA = "delta"
    }

    private var mDumpReason = ""


    override fun track(): Boolean {
        var javaHeap = SystemInfo.javaHeap
        if (javaHeap.rate> monitorConfig?.forceDumpJavaHeapMaxThreshold!!) {
            mDumpReason = REASON_HIGH_WATERMARK
            Log.i("HugeMemoryTracer", "[meet condition] fast huge memory allocated detected, " +
                    "high memory watermark, force dump analysis!")
            return true
        }
        val lastJavaHeap =SystemInfo.lastJavaHeap
        if (lastJavaHeap.max!= 0L &&javaHeap.used - lastJavaHeap.used> SizeUnit.KB.toByte(monitorConfig!!.forceDumpJavaHeapMaxThreshold)){
            mDumpReason = REASON_HUGE_DELTA
            Log.i("HugeMemoryTracer",  "[meet condition] fast huge memory allocated detected, " +
                    "over the delta threshold!")
            return true
        }
        return false
    }

    override fun reason() = "reason_fast_huge_$mDumpReason"

    override fun reset() {

    }
}