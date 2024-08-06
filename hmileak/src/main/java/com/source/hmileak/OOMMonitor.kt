package com.source.hmileak

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.source.hmileak.base.Config
import com.source.hmileak.base.LooperMonitor
import com.source.hmileak.base.currentActivity
import com.source.hmileak.base.isForeground
import com.source.hmileak.base.registerProcessLifecycleObserver
import com.source.hmileak.tracer.FdOomTracer
import com.source.hmileak.tracer.HeapOOMTracker
import com.source.hmileak.tracer.HugeMemoryTracer
import com.source.hmileak.tracer.PhysicalMemoryTracer
import com.source.hmileak.tracer.ThreadOOMTracer
import com.source.hmileak.util.async
import com.source.hmileak.util.isMainProcess
import com.source.hprofanalyzer.ForkJvmHeap
import com.source.hprofanalyzer.analysis.AnalysisExtraData
import com.source.hprofanalyzer.analysis.AnalysisReceiver
import com.source.hprofanalyzer.analysis.HeapAnalysisService
import com.source.hprofanalyzer.util.OOMFileManager
import com.source.hprofanalyzer.util.OOMFileManager.hprofAnalysisDir
import com.source.hprofanalyzer.util.OOMFileManager.manualDumpDir
import com.source.hprofanalyzer.util.SystemInfo
import java.io.File
import java.util.Date

object OOMMonitor : LooperMonitor<Config>(),LifecycleEventObserver {

    private val mOOMTracers = mutableListOf(FdOomTracer(),HugeMemoryTracer(),HeapOOMTracker(),PhysicalMemoryTracer(),ThreadOOMTracer())

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
    lateinit var mConfig:Config

    override fun init(config: Config) {
        super.init(config)
        mConfig = config;
        mMonitorInitTime = SystemClock.elapsedRealtime()
        OOMFileManager.init(mConfig.rootFileInvoker)
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
        for (hprofFile in manualDumpDir.listFiles().orEmpty()) {
            mConfig.hprofUploader?.upload(hprofFile,OOMHprofUploader.HprofType.STRIPPED)
        }
    }

    private fun reAnalysisHprof() {
        for(file in hprofAnalysisDir.listFiles().orEmpty()){
            if (!file.exists())
                continue
            if (file.canonicalPath.endsWith(".hprof")) {
                var jsonFile = File(file.canonicalPath.replace(".hprof",".json"))
                if (!jsonFile.exists()) {
                    jsonFile.createNewFile()
                    startAnalysisService(file,jsonFile,"reanalysis")
                } else{
                    jsonFile.delete()
                    file.delete()
                }
            }
        }
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

    override fun getLoopInterval(): Long {
        return mConfig.loopInterval
    }


    private fun trackOOM():State{
        SystemInfo.refresh()
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
        Log.i("OOMMonitor", "dumpAndAnalysis");
        kotlin.runCatching {
            if (!OOMFileManager.isSpaceEnough()){
                Log.e("OOMMonitor", "available space not enough")
                return@runCatching
            }
            if (mHasDumped)
                return
            mHasDumped =true
            val date = Date()
            var jsonFile = OOMFileManager.createJsonAnalysisFile(date)
            var hprofFile = OOMFileManager.createHprofAnalysisFile(date).apply {
                createNewFile()
                setWritable(true)
                setReadable(true)
            }
            Log.i("OOMMonitor", "hprof analysis dir:$hprofAnalysisDir")
            ForkJvmHeap.getInstance().run {
                dump(hprofFile.absolutePath)
            }
            Log.i("OOMMonitor","end hprof dump")
            Thread.sleep(1000)
            startAnalysisService(hprofFile,jsonFile, mTraceReason.joinToString())

        }
    }

    private fun startAnalysisService(hprofile:File,jsonFile:File,reason:String) {
        if (hprofile.length() == 0L) {
            hprofile.delete()
            return
        }
        if (!getApplication().isForeground) {
            mForegroundPendingRunnables.add(Runnable {
                startAnalysisService(hprofile,jsonFile,reason)
            })
            return
        }
        val extraData = AnalysisExtraData().apply {
            this.reason = reason
            this.currentPage = getApplication().currentActivity?.localClassName.orEmpty()
            this.usageSeconds ="${(SystemClock.elapsedRealtime() - mMonitorInitTime)/1000}"
        }
        HeapAnalysisService.startAnalysisService(getApplication(),
            hprofile.canonicalPath,
            jsonFile.canonicalPath,
            extraData,
            object:AnalysisReceiver.ResultCallback{

                override fun onError() {
                    hprofile.delete()
                    jsonFile.delete()
                }

                override fun onSuccess() {
                    val content =jsonFile.readText()
                    mConfig.reportUploader!!.upload(jsonFile,content)
                    mConfig.hprofUploader!!.upload(hprofile,OOMHprofUploader.HprofType.ORIGIN)
                }
            })
    }
}