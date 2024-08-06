package com.source.hmileak.tracer

import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.OOMFileManager
import java.io.File

class ThreadOOMTracer : OOMTracker() {

    companion object {
        private const val THREAD_COUNT_THRESHOLD_GAP = 50 //Thread连续值递增浮动范围50
    }

    private var mLastThreadCount = 0
    private var mOverThresholdCount = 0
    private var mConfig:Config? = null
    override fun init(config: Config) {
        this.mConfig = config
    }

    override fun track(): Boolean {
        var threadCount = getThreadCount()
        if (threadCount>mConfig!!.threadThreshold && threadCount>=mLastThreadCount- THREAD_COUNT_THRESHOLD_GAP) {
            mOverThresholdCount++
            Log.i("ThreadOOMTracer",
                "[meet condition] "
                        + "overThresholdCount:$mOverThresholdCount"
                        + ", threadCount: $threadCount")
            dumpThreadIfNeed()
        } else {
            reset()
        }
        mLastThreadCount = threadCount
        return mOverThresholdCount>=mConfig!!.maxOverThresholdCount
    }

    override fun reason()= "reason_thread_oom"

    override fun reset() {
        mLastThreadCount = 0
        mOverThresholdCount = 0
    }

    private fun getThreadCount():Int {
        return SystemInfo.procStatus.thread
    }

    private fun dumpThreadIfNeed() {
        Log.i("ThreadOOMTracer", "over threshold dumpThreadIfNeed")
        if (mOverThresholdCount>mConfig!!.maxOverThresholdCount)
            return
        var threadNames = kotlin.runCatching { File("/proc/self/task").listFiles() }
            .getOrElse {
                Log.i("ThreadOOMTracer", "/proc/self/task child files is empty")
                return@getOrElse emptyArray()
            }?.map {
                kotlin.runCatching { File(it,"comm").readText() }.getOrElse {
                    "failed to read $it/comm"
                }
            }?.map {
                if (it.endsWith("\n"))
                    it.substring(0,it.length-1) else it
            }?: emptyList()
        Log.i("ThreadOOMTracer", "threadNames = $threadNames")
        OOMFileManager.createDumpFile(OOMFileManager.threadDumpDir)
            .run {
                kotlin.runCatching {
                writeText(threadNames.joinToString(","))
                }
            }
    }


}