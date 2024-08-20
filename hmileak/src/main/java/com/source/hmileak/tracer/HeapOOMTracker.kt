package com.source.hmileak.tracer

import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.SizeUnit
import com.source.hprofanalyzer.util.SystemInfo

class HeapOOMTracker : OOMTracker() {

    companion object{
        private const val HEAP_RATIO_THRESHOLD_GAP = 0.05f
    }

    private var mlastHeapRatio =0.0f
    private var mOverThresholdCount  = 0

    override fun track(): Boolean {
        val heapRatio = SystemInfo.javaHeap.rate
        Log.e("HeapOomTracer","heapRatio:"+heapRatio+"  heapThreshold:"+monitorConfig.heapThreshold+"===="+(mlastHeapRatio - HEAP_RATIO_THRESHOLD_GAP))
        if (heapRatio>monitorConfig!!.heapThreshold && heapRatio >= mlastHeapRatio - HEAP_RATIO_THRESHOLD_GAP) {
            mOverThresholdCount++
            Log.i("HeapOOMTracker","[meet condition] "
                    + "overThresholdCount: $mOverThresholdCount"
                    + ", heapRatio: $heapRatio"
                    + ", usedMem: ${SizeUnit.BYTE.toMB(SystemInfo.javaHeap.used)}mb"
                    + ", max: ${SizeUnit.BYTE.toMB(SystemInfo.javaHeap.max)}mb")
        } else {
            reset()
        }
        mlastHeapRatio = heapRatio
        return mOverThresholdCount>= monitorConfig!!.maxOverThresholdCount
    }

    override fun reason() = "reason_heap_oom"

    override fun reset() {
        mlastHeapRatio = 0.0f
        mOverThresholdCount = 0
    }
}