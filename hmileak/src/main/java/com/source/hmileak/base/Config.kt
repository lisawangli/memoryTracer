package com.source.hmileak.base

import android.app.Application
import android.os.Build
import android.os.Handler
import com.source.hmileak.MonitorConfig
import com.source.hmileak.OOMHprofUploader
import com.source.hmileak.OOMMonitor
import com.source.hmileak.OOMReportUploader
import com.source.hprofanalyzer.util.SizeUnit
import java.io.File

class Config (
    val maxOverThresholdCount: Int,
    val fdThreshold: Int,
    val forceDumpJavaHeapMaxThreshold: Float,
    val forceDumpJavaHeapDeltaThreshold: Int,
    val deviceMemoryThreshold: Float,
    val threadThreshold: Int,
    val heapThreshold: Float,
    val loopInterval: Long,
    val hprofUploader: OOMHprofUploader?,
    val reportUploader: OOMReportUploader?,

    internal val loopHandlerInvoker: () -> Handler
): MonitorConfig<OOMMonitor>(){


    class Builder: MonitorConfig.Builder<Config> {
        private var mThreadThreshold: Int? = null

        private var mFdThreshold = 1000

        private var mForceDumpJavaHeapMaxThreshold = 0.90f
        private var mForceDumpJavaHeapDeltaThreshold = 350_000 //java heap rise 350m in a very short time.
        private var mDeviceMemoryThreshold: Float = 0.05f
        private var mHeapThreshold: Float? = null
        private var mRootFileInvoker: ((String) -> File)? = null
        private var mLoopInterval = 15_000L
        private var mHprofUploader: OOMHprofUploader? = null
        private var mReportUploader: OOMReportUploader? = null
        fun setThreadThreshold(threadThreshold: Int) = apply {
            mThreadThreshold = threadThreshold
        }

        fun setForceDumpJavaHeapDeltaThreshold(forceDumpJavaHeapDeltaThreshold: Int) = apply {
            mForceDumpJavaHeapDeltaThreshold = forceDumpJavaHeapDeltaThreshold
        }
        fun setHprofUploader(hprofUploader: OOMHprofUploader) = apply {
            mHprofUploader = hprofUploader
        }

        fun setReportUploader(reportUploader: OOMReportUploader) = apply {
            mReportUploader = reportUploader
        }
        fun setLoopInterval(loopInterval: Long) = apply {
            mLoopInterval = loopInterval
        }
        /**
         * @param heapThreshold: 堆内存的使用比例[0.0, 1.0]
         */
        fun setHeapThreshold(heapThreshold: Float) = apply {
            mHeapThreshold = heapThreshold
        }
        fun setDeviceMemoryThreshold(deviceMemoryThreshold: Float) = apply {
            mDeviceMemoryThreshold = deviceMemoryThreshold
        }

        fun setForceDumpJavaHeapMaxThreshold(forceDumpJavaHeapMaxThreshold: Float) = apply {
            mForceDumpJavaHeapMaxThreshold = forceDumpJavaHeapMaxThreshold
        }
        fun setFdThreshold(fdThreshold: Int) = apply {
            mFdThreshold = fdThreshold
        }

        private var mLoopHandlerInvoker: (() -> Handler)? = null


        fun setLoopHandlerInvoker(loopHandlerInvoker: () -> Handler) = apply {
            this.mLoopHandlerInvoker = loopHandlerInvoker
        }

        override fun build(): Config = Config(
            maxOverThresholdCount = 3,
            fdThreshold = mFdThreshold,
            forceDumpJavaHeapMaxThreshold = mForceDumpJavaHeapMaxThreshold,
            forceDumpJavaHeapDeltaThreshold = mForceDumpJavaHeapDeltaThreshold,
            deviceMemoryThreshold = mDeviceMemoryThreshold,
            threadThreshold = mThreadThreshold ?: DEFAULT_THREAD_THRESHOLD,
            heapThreshold = mHeapThreshold ?: DEFAULT_HEAP_THRESHOLD,
            loopInterval = mLoopInterval,
            hprofUploader = mHprofUploader,
            reportUploader = mReportUploader,
            loopHandlerInvoker = mLoopHandlerInvoker ?: { LoopThread.LOOP_HANDLER }

        )

        val ROM by lazy { Build.MANUFACTURER.toUpperCase()
            .let { if (it == "HUAWEI") "EMUI" else "OTHER"} }

        private val DEFAULT_THREAD_THRESHOLD by lazy {
            if (ROM == "EMUI" && Build.VERSION.SDK_INT <= Build.VERSION_CODES.O) {
                450
            } else {
                750
            }
        }

        private val DEFAULT_HEAP_THRESHOLD by lazy {
            val maxMem = SizeUnit.BYTE.toMB(Runtime.getRuntime().maxMemory())
            when {
                maxMem >= 512 - 10 -> 0.8f
                maxMem >= 256 - 10 -> 0.85f
                else -> 0.9f
            }
        }

    }

}