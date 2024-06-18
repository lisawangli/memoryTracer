package com.source.hmileak

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.source.hmileak.base.Config
import com.source.hmileak.base.LooperMonitor
import com.source.hmileak.base.registerProcessLifecycleObserver
import com.source.hmileak.tracer.FdOomTracer
import com.source.hmileak.tracer.HugeMemoryTracer
import com.source.hmileak.tracer.OOMTracer
import com.source.hmileak.tracer.PhysicalMemoryTracer
import com.source.hmileak.tracer.ThreadOOMTracer
import com.source.hmileak.util.async
import com.source.hmileak.util.isMainProcess

object OOMMonitor : LooperMonitor<Config>(),LifecycleEventObserver {

    private val mOOMTracers = mutableListOf(FdOomTracer(),HugeMemoryTracer(),OOMTracer(),PhysicalMemoryTracer(),ThreadOOMTracer())

    private var mTraceReason = mutableListOf<String>()

    private var mMonitorInitTime = 0L

    private var mForegroundPendingRunnables = mutableListOf<Runnable>()
    @Volatile
    private var mIsLoopStarted = false

    @Volatile
    private var mIsLoopPendingStart = false

    @Volatile
    private var mHasDumped = false

    @Volatile
    private var mHasProcessOldHprof = false
    lateinit var mConfig:Config;

    override fun init(config: Config) {
        super.init(config)
        mConfig = config;
        mMonitorInitTime = SystemClock.elapsedRealtime()
        for (oomTracker in mOOMTracers) {
            oomTracker.init(config)
        }

       getApplication().registerProcessLifecycleObserver(this)
    }

    override fun startLoop(cleanData: Boolean, postAtFront: Boolean, delayMillis: Long) {
        if (!isMainProcess(mConfig)){
            return
        }
        if (mIsLoopStarted)
            return
        mIsLoopStarted = true
        super.startLoop(cleanData, postAtFront, delayMillis)
        getLooperHandler().postDelayed({ async { processOldHprofFile() } }, delayMillis)

    }


    override fun stopLoop() {
        if (!isMainProcess(mConfig)){
            return
        }

        super.stopLoop()
        mIsLoopStarted = false
    }

    private fun processOldHprofFile() {
        if (mHasProcessOldHprof) {
            return
        }
        mHasProcessOldHprof = true;
        reAnalysisHprof()
        manualDumpHprof()
    }

    private fun manualDumpHprof() {

    }

    private fun reAnalysisHprof() {

    }


    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when(event){
            Lifecycle.Event.ON_START ->{
                if (!mHasDumped && mIsLoopPendingStart) {
                    startLoop()
                }
                mForegroundPendingRunnables.forEach { it.run() }
                mForegroundPendingRunnables.clear()
            }

            Lifecycle.Event.ON_STOP->{
                mIsLoopPendingStart = mIsLoopStarted
                stopLoop()
            }
            else -> Unit
        }
    }

    override fun call(): State {
        if (mHasDumped) {
            return State.Terminate
        }

        return trackOOM()
    }

    private fun trackOOM():State{
        mTraceReason.clear()
        for (oomTracer in mOOMTracers) {
            if (oomTracer.track())
                mTraceReason.add(oomTracer.reason())
        }
        if (mTraceReason.isEmpty()){
            async {

                dumpAndAnalysis()
            }
            return State.Terminate
        }
        return State.Continue
    }

    private fun dumpAndAnalysis() {
        TODO("Not yet implemented")
    }
}