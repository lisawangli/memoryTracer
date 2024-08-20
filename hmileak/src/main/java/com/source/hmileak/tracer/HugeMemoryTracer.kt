package com.source.hmileak.tracer

import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.SizeUnit
import com.source.hprofanalyzer.util.SystemInfo

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
        val javaHeap = SystemInfo.javaHeap

        // 高危阈值直接触发dump分析
        if (javaHeap.rate > monitorConfig.forceDumpJavaHeapMaxThreshold) {
            mDumpReason = REASON_HIGH_WATERMARK
            Log.i("HugeMemnoryTracer", "[meet condition] fast huge memory allocated detected, " +
                    "high memory watermark, force dump analysis!")
            return true
        }

        // 高差值直接dump
        val lastJavaHeap = SystemInfo.lastJavaHeap
        Log.e("HugeMemnoryTracer","======"+ SizeUnit.KB.toByte(monitorConfig.forceDumpJavaHeapDeltaThreshold)+"===="+monitorConfig.forceDumpJavaHeapMaxThreshold+"==="+(javaHeap.used - lastJavaHeap.used))
        if (lastJavaHeap.max != 0L && javaHeap.used - lastJavaHeap.used
            > SizeUnit.KB.toByte(monitorConfig.forceDumpJavaHeapDeltaThreshold)) {
            mDumpReason = REASON_HUGE_DELTA
            Log.i("HugeMemnoryTracer", "[meet condition] fast huge memory allocated detected, " +
                    "over the delta threshold!")
            return true
        }

        return false
    }

    override fun reason() = "reason_fast_huge_$mDumpReason"

    override fun reset() {

    }
}