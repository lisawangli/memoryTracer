package com.source.hmileak.tracer

import android.system.Os
import android.util.Log
import com.source.hmileak.base.Config
import com.source.hmileak.base.OOMTracker
import com.source.hprofanalyzer.util.OOMFileManager
import java.io.File

/**
 * fd 追踪
 */
class FdOomTracer : OOMTracker() {

    companion object {
        private const val FD_COUNT_THRESHOLD_GAP =50
    }

    private var mLastCount = 0
    private var mOverThresholdCount = 0


    override fun track(): Boolean {
        var fdCount = getFdCount()
        if (fdCount > monitorConfig!!.fdThreshold && fdCount>=mLastCount- FD_COUNT_THRESHOLD_GAP) {
            mOverThresholdCount++
            Log.i("FdOOmTracer","[meet condition] "
                    + "overThresholdCount: $mOverThresholdCount"
                    + ", fdCount: $fdCount")
            dumpFdIfNeed()
        } else {
            reset()
        }
        mLastCount = fdCount
        return mOverThresholdCount >= monitorConfig!!.maxOverThresholdCount
    }

    override fun reason() = "reason_fd_oom"

    override fun reset() {
        mLastCount = 0
        mOverThresholdCount = 0
    }

    private fun getFdCount(): Int{
        return File("/proc/self/fd").listFiles()?.size?:0
    }

    private fun dumpFdIfNeed() {
        Log.i("FDOOMTracer","over threshold dumpFdIfneed")
        if (mOverThresholdCount>monitorConfig!!.maxOverThresholdCount)
            return
        var fdNames = kotlin.runCatching {
            File("/proc/self/fd").listFiles()
        }.getOrElse {
            Log.i("FdOOmTracer","/proc/self/fd child files is empty")
            return@getOrElse emptyArray()
        }?.map { file ->
            kotlin.runCatching {
                Os.readlink(file.path)
            }.getOrElse {
                "failed to read link ${file.path}"
            }
        }?: emptyList()
        OOMFileManager.createDumpFile(OOMFileManager.fdDumpDir).run {
            kotlin.runCatching {
                writeText(fdNames.sorted().joinToString(","))
            }
        }
    }
}