package com.source.hprofanalyzer.analysis

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.ResultReceiver
import com.source.hprofanalyzer.util.OOMFileManager
import com.source.hprofanalyzer.util.SizeUnit
import kshark.HeapGraph
import kshark.HprofHeapGraph.Companion.openHeapGraph
import kshark.HprofRecordTag
import kshark.SharkLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

class HeapAnalysisService : IntentService("HeapAnalysisService") {

    companion object {
        private const val TAG = "OOMMonitor_HeapAnalysisService"

        private const val OOM_ANALYSIS_TAG = "OOMMonitor"
        private const val OOM_ANALYSIS_EXCEPTION_TAG = "OOMMonitor_Exception"

        //Activity->ContextThemeWrapper->ContextWrapper->Context->Object
        private const val ACTIVITY_CLASS_NAME = "android.app.Activity"

        //Bitmap->Object
        //Exception: Some OPPO devices
        const val BITMAP_CLASS_NAME = "android.graphics.Bitmap"

        //Fragment->Object
        private const val NATIVE_FRAGMENT_CLASS_NAME = "android.app.Fragment"

        // native android Fragment, deprecated as of API 28.
        private const val SUPPORT_FRAGMENT_CLASS_NAME = "android.support.v4.app.Fragment"

        // pre-androidx, support library version of the Fragment implementation.
        private const val ANDROIDX_FRAGMENT_CLASS_NAME = "androidx.fragment.app.Fragment"

        // androidx version of the Fragment implementation
        //Window->Object
        private const val WINDOW_CLASS_NAME = "android.view.Window"

        //NativeAllocationRegistry
        private const val NATIVE_ALLOCATION_CLASS_NAME = "libcore.util.NativeAllocationRegistry"
        private const val NATIVE_ALLOCATION_CLEANER_THUNK_CLASS_NAME = "libcore.util.NativeAllocationRegistry\$CleanerThunk"

        private const val FINISHED_FIELD_NAME = "mFinished"
        private const val DESTROYED_FIELD_NAME = "mDestroyed"

        private const val FRAGMENT_MANAGER_FIELD_NAME = "mFragmentManager"
        private const val FRAGMENT_MCALLED_FIELD_NAME = "mCalled"

        private const val DEFAULT_BIG_PRIMITIVE_ARRAY = 256 * 1024
        private const val DEFAULT_BIG_BITMAP = 768 * 1366 + 1
        private const val DEFAULT_BIG_OBJECT_ARRAY = 256 * 1024
        private const val SAME_CLASS_LEAK_OBJECT_PATH_THRESHOLD = 45

        annotation class Info {
            companion object {
                internal const val HPROF_FILE = "HPROF_FILE"
                internal const val JSON_FILE = "JSON_FILE"
                internal const val ROOT_PATH = "ROOT_PATH"
                internal const val RESULT_RECEIVER = "RESULT_RECEIVER"

                internal const val JAVA_MAX_MEM = "JAVA_MAX_MEM"
                internal const val JAVA_USED_MEM = "JAVA_USED_MEM"
                internal const val DEVICE_MAX_MEM = "DEVICE_MAX_MEM"
                internal const val DEVICE_AVA_MEM = "DEVICE_AVA_MEM"
                internal const val VSS = "VSS"
                internal const val PSS = "PSS"
                internal const val RSS = "RSS"
                internal const val FD = "FD"
                internal const val THREAD = "THREAD"
                internal const val SDK = "SDK"
                internal const val MANUFACTURE = "MANUFACTURE"
                internal const val MODEL = "MODEL"
                internal const val TIME = "TIME"
                internal const val REASON = "REASON"
                internal const val USAGE_TIME = "USAGE_TIME"
                internal const val CURRENT_PAGE = "CURRENT_PAGE"
            }
        }

        fun startAnalysisService(context: Context, hprofFile: String?, jsonFile: String?,
                                 extraData: AnalysisExtraData, resultCallBack: AnalysisReceiver.ResultCallback?) {

            val analysisReceiver = AnalysisReceiver().apply {
                if (resultCallBack != null) {
                    setResultCallback(resultCallBack)
                }
            }

            val intent = Intent(context, HeapAnalysisService::class.java).apply {
                putExtra(Info.HPROF_FILE, hprofFile)
                putExtra(Info.JSON_FILE, jsonFile)
                putExtra(Info.ROOT_PATH, OOMFileManager.rootDir.absolutePath)
                putExtra(Info.RESULT_RECEIVER, analysisReceiver)


                putExtra(Info.MANUFACTURE, Build.MANUFACTURER.toString())
                putExtra(Info.SDK, Build.VERSION.SDK_INT.toString())
                putExtra(Info.MODEL, Build.MODEL.toString())
                putExtra(Info.TIME, SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.CHINESE).format(
                    Date()
                ))

                if (extraData.reason != null) {
                    putExtra(Info.REASON, extraData.reason)
                }
                if (extraData.currentPage != null) {
                    putExtra(Info.CURRENT_PAGE, extraData.currentPage)
                }
                if (extraData.usageSeconds != null) {
                    putExtra(Info.USAGE_TIME, extraData.usageSeconds)
                }
            }

            context.startService(intent)
        }
    }

    private lateinit var mHeapGraph: HeapGraph

    private val mLeakModel = HeapReport()
    private val mLeakingObjectIds = mutableSetOf<Long>()
    private val mLeakReasonTable = mutableMapOf<Long, String>()
    override fun onHandleIntent(intent: Intent?) {
        val resultReceiver = intent?.getParcelableExtra<ResultReceiver>(Info.RESULT_RECEIVER)
        val hprofFile = intent?.getStringExtra(Info.HPROF_FILE)
        val jsonFile = intent?.getStringExtra(Info.JSON_FILE)
        val rootPath = intent?.getStringExtra(Info.ROOT_PATH)

        OOMFileManager.init(rootPath)

        kotlin.runCatching {
            buildIndex(hprofFile)
        }.onFailure {
            it.printStackTrace()
            resultReceiver?.send(AnalysisReceiver.RESULT_CODE_FAIL, null)
            return
        }
        buildJson(intent)


    }

    private fun buildIndex(hprofFile: String?) {
        if (hprofFile.isNullOrEmpty()) return


        SharkLog.logger = object : SharkLog.Logger {
            override fun d(message: String) {
                println(message)
            }

            override fun d(
                throwable: Throwable,
                message: String
            ) {
                println(message)
                throwable.printStackTrace()
            }
        }

        measureTimeMillis {
            mHeapGraph = File(hprofFile).openHeapGraph(null,
                setOf(
                    HprofRecordTag.ROOT_JNI_GLOBAL,
                    HprofRecordTag.ROOT_JNI_LOCAL,
                    HprofRecordTag.ROOT_NATIVE_STACK,
                    HprofRecordTag.ROOT_STICKY_CLASS,
                    HprofRecordTag.ROOT_THREAD_BLOCK,
                    HprofRecordTag.ROOT_THREAD_OBJECT));
        }.also {
        }
    }

    private fun buildJson(intent: Intent?) {
        mLeakModel.runningInfo = HeapReport.RunningInfo().apply {
            jvmMax = intent?.getStringExtra(Info.JAVA_MAX_MEM)
            jvmUsed = intent?.getStringExtra(Info.JAVA_USED_MEM)
            threadCount = intent?.getStringExtra(Info.THREAD)
            fdCount = intent?.getStringExtra(Info.FD)

            vss = intent?.getStringExtra(Info.VSS)
            pss = intent?.getStringExtra(Info.PSS)
            rss = intent?.getStringExtra(Info.RSS)

            sdkInt = intent?.getStringExtra(Info.SDK)
            manufacture = intent?.getStringExtra(Info.MANUFACTURE)
            buildModel = intent?.getStringExtra(Info.MODEL)

            usageSeconds = intent?.getStringExtra(Info.USAGE_TIME)
            currentPage = intent?.getStringExtra(Info.CURRENT_PAGE)
            nowTime = intent?.getStringExtra(Info.TIME)

            deviceMemTotal = intent?.getStringExtra(Info.DEVICE_MAX_MEM);
            deviceMemAvaliable = intent?.getStringExtra(Info.DEVICE_AVA_MEM)

            dumpReason = intent?.getStringExtra(Info.REASON)


        }
    }

}