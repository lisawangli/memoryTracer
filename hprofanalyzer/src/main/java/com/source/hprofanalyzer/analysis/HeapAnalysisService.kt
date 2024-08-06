package com.source.hprofanalyzer.analysis

import android.app.IntentService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Debug
import android.os.ResultReceiver
import android.util.Log
import com.google.gson.Gson

import com.source.hprofanalyzer.util.OOMFileManager
import com.source.hprofanalyzer.util.SizeUnit
import com.source.hprofanalyzer.util.SystemInfo.javaHeap
import com.source.hprofanalyzer.util.SystemInfo.memInfo
import com.source.hprofanalyzer.util.SystemInfo.procStatus
import kshark.AndroidReferenceMatchers
import kshark.HeapAnalyzer
import kshark.HeapGraph
import kshark.HprofHeapGraph.Companion.openHeapGraph
import kshark.HprofRecordTag
import kshark.OnAnalysisProgressListener
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

                putExtra(Info.JAVA_MAX_MEM, SizeUnit.BYTE.toMB(javaHeap.max).toString())
                putExtra(Info.JAVA_USED_MEM, SizeUnit.BYTE.toMB(javaHeap.total - javaHeap.free).toString())
                putExtra(Info.DEVICE_MAX_MEM,SizeUnit.KB.toMB(memInfo.totalInKb).toString())
                putExtra(Info.DEVICE_AVA_MEM, SizeUnit.KB.toMB(memInfo.availableInkb).toString())
                putExtra(Info.FD,(File("/proc/self/fd").listFiles()?.size?:0).toString())

                var pss = Debug.getPss()
                Log.e("Analysis","startAnalysisService get pss:${pss}")
                putExtra(Info.PSS,SizeUnit.KB.toMB(pss).toString()+"mb")
                putExtra(Info.RSS, SizeUnit.KB.toMB(procStatus.rssInKb).toString()+"mb")
                putExtra(Info.VSS, SizeUnit.KB.toMB(procStatus.vssInKb).toString()+"mb")
                putExtra(Info.THREAD, procStatus.thread.toString())


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
        kotlin.runCatching {
            filterLeakingObjects()
        }.onFailure {
            it.printStackTrace()
            Log.i(OOM_ANALYSIS_EXCEPTION_TAG, "find gc path exception " + it.message)
            resultReceiver?.send(AnalysisReceiver.RESULT_CODE_FAIL,null)
            return
        }
        fillJsonFile(jsonFile)
        resultReceiver?.send(AnalysisReceiver.RESULT_CODE_OK,null)
        System.exit(0)

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

    /**
     * 遍历镜像所有class查找
     *
     * 计算gc path：
     * 1.已经destroyed和finished的activity
     * 2.已经fragment manager为空的fragment
     * 3.已经destroyed的window
     * 4.超过阈值大小的bitmap
     * 5.超过阈值大小的基本类型数组
     * 6.超过阈值大小的对象个数的任意class
     *
     *
     * 记录关键类:
     * 对象数量
     * 1.基本类型数组
     * 2.Bitmap
     * 3.NativeAllocationRegistry
     * 4.超过阈值大小的对象的任意class
     *
     *
     * 记录大对象:
     * 对象大小
     * 1.Bitmap
     * 2.基本类型数组
     */

    private fun filterLeakingObjects() {
        var startTime =System.currentTimeMillis()
        Log.i(TAG, "filterLeakingObjects " + Thread.currentThread())
        val activityHeapClass = mHeapGraph.findClassByName(ACTIVITY_CLASS_NAME)
        val fragmentHeapClass = mHeapGraph.findClassByName(ANDROIDX_FRAGMENT_CLASS_NAME)?:mHeapGraph.findClassByName(
            NATIVE_FRAGMENT_CLASS_NAME)?:mHeapGraph.findClassByName(SUPPORT_FRAGMENT_CLASS_NAME)
        val bitmapHeapClass  = mHeapGraph.findClassByName(BITMAP_CLASS_NAME)
        val nativeAllocationHeapClass = mHeapGraph.findClassByName(NATIVE_ALLOCATION_CLASS_NAME)
        val nativeAllocationThunkHeapClass = mHeapGraph.findClassByName(
            NATIVE_ALLOCATION_CLEANER_THUNK_CLASS_NAME)
        val windowClass = mHeapGraph.findClassByName(WINDOW_CLASS_NAME)

        val classHierearchyMap = mutableMapOf<Long,Pair<Long,Long>>()
        val classObjectCounterMap = mutableMapOf<Long,ObjectCounter>()
        for (instance in mHeapGraph.instances) {
            if (instance.isPrimitiveWrapper)
                continue
            var instanceClassId = instance.instanceClassId
            val(superId1,superId4) = if(classHierearchyMap[instanceClassId] !=null) {
                classHierearchyMap[instanceClassId]!!
            } else {
                val classHierarhyList = instance.instanceClass.classHierarchy.toList()
                var first = classHierarhyList.getOrNull(classHierarhyList.size -2)?.objectId?:0L
                var second = classHierarhyList.getOrNull(classHierarhyList.size - 5)?.objectId?:0L
                Pair(first,second).also { classHierearchyMap[instanceClassId] = it }
            }
            if (activityHeapClass?.objectId==superId4) {
                var destroyField = instance[ACTIVITY_CLASS_NAME, DESTROYED_FIELD_NAME]!!
                var finishedField = instance[ACTIVITY_CLASS_NAME, FINISHED_FIELD_NAME]!!
                if (destroyField.value.asBoolean!!||finishedField.value.asBoolean!!) {
                    var objectCounter = updateClassObjectCounterMap(classObjectCounterMap,instanceClassId,true)
                    Log.i(TAG, "activity name : " + instance.instanceClassName
                            + " mDestroyed:" + destroyField.value.asBoolean
                            + " mFinished:" + finishedField.value.asBoolean
                            + " objectId:" + (instance.objectId and 0xffffffffL))
                    if (objectCounter.leakCnt<= SAME_CLASS_LEAK_OBJECT_PATH_THRESHOLD) {
                        mLeakingObjectIds.add(instance.objectId)
                        mLeakReasonTable[instance.objectId] = "Activity Leak"
                        Log.i(OOM_ANALYSIS_TAG,
                            instance.instanceClassName + " objectId:" + instance.objectId)
                    }
                }
                continue
            }
            if (fragmentHeapClass?.objectId==superId1) {
                val fragmentManager =instance[fragmentHeapClass.name, FRAGMENT_MANAGER_FIELD_NAME]
                if (fragmentManager!=null&&fragmentManager.value.asObject==null){
                    val mCalledField = instance[fragmentHeapClass.name, FRAGMENT_MCALLED_FIELD_NAME]
                    val isLeak = mCalledField != null && mCalledField.value.asBoolean!!
                    val objectCounter =updateClassObjectCounterMap(classObjectCounterMap,instanceClassId,isLeak)
                    Log.i(TAG, "fragment name:" + instance.instanceClassName + " isLeak:" + isLeak)
                    if (objectCounter.leakCnt<= SAME_CLASS_LEAK_OBJECT_PATH_THRESHOLD && isLeak) {
                        mLeakingObjectIds.add(instance.objectId)
                        mLeakReasonTable[instance.objectId] = "fragment Leak"
                        Log.i(OOM_ANALYSIS_TAG,
                            instance.instanceClassName + " objectId:" + instance.objectId)
                    }
                }
                continue
            }
            if (bitmapHeapClass?.objectId==superId1) {
                val fieldWidht = instance[BITMAP_CLASS_NAME,"mWidth"]
                val filedHeight = instance[BITMAP_CLASS_NAME,"mHeight"]
                val width = fieldWidht!!.value.asInt!!
                val height = filedHeight!!.value.asInt!!
                if (width * height >= DEFAULT_BIG_BITMAP)  {
                    var objectCounter = updateClassObjectCounterMap(classObjectCounterMap,instanceClassId,true)
                    Log.i(TAG,"suspect leak! bitmap name: ${instance.instanceClassName}" +
                            " width: ${width} height:${height}")
                    if (objectCounter.leakCnt <= SAME_CLASS_LEAK_OBJECT_PATH_THRESHOLD) {
                        mLeakingObjectIds.add(instance.objectId)
                        mLeakReasonTable[instance.objectId] = "Bitmap Size Over Threshold, ${width}x${height}"
                        Log.i(OOM_ANALYSIS_TAG,
                            instance.instanceClassName + " objectId:" + instance.objectId)
                        val leakObject = HeapReport.LeakObject().apply {
                            className =instance.instanceClassName
                            size = (width * height).toString()
                            extDetail = "$width x $height"
                            objectId =(instance.objectId and 0xffffffffL).toString()
                        }
                        mLeakModel.leakObjects.add(leakObject)
                    }
                }
                continue
            }
            if (nativeAllocationHeapClass?.objectId==superId1
                || nativeAllocationThunkHeapClass?.objectId == superId1
                || windowClass?.objectId == superId1) {
                updateClassObjectCounterMap(classObjectCounterMap,instanceClassId,false)
            }
        }
        for ((instanceId, objectCounter) in classObjectCounterMap){
            val leakClass = HeapReport.ClassInfo().apply {
                val heapClass = mHeapGraph.findObjectById(instanceId).asClass
                className = heapClass!!.name
                instanceCount = objectCounter.allCnt.toString()
                Log.i(OOM_ANALYSIS_TAG, "leakClass.className: $className leakClass.objectCount: $instanceCount")


        }
            mLeakModel.classInfos.add(leakClass)
        }

        val primitiveArrayIterator = mHeapGraph.primitiveArrays.iterator()
        while (primitiveArrayIterator.hasNext()) {
            val primitiveArray = primitiveArrayIterator.next()
            val arraySize = primitiveArray.recordSize
            if (arraySize>= DEFAULT_BIG_BITMAP) {
                val arrayName = primitiveArray.arrayClassName
                val typeName = primitiveArray.primitiveType.toString()
                Log.e(OOM_ANALYSIS_TAG,
                    "uspect leak! primitive arrayName:" + arrayName
                            + " size:" + arraySize + " typeName:" + typeName
                            + ", objectId:" + (primitiveArray.objectId and 0xffffffffL)
                            + ", toString:" + primitiveArray.toString())
                mLeakingObjectIds.add(primitiveArray.objectId)
                mLeakReasonTable[primitiveArray.objectId]  = "Primitive Array Size Over Threshold, ${arraySize}"
                val leakObject = HeapReport.LeakObject().apply {
                    className = arrayName
                    size = arraySize.toString()
                    objectId = (primitiveArray.objectId and 0xffffffffL).toString()
                }
                mLeakModel.leakObjects.add(leakObject)
            }
        }

        val objectArrayIterator = mHeapGraph.objectArrays.iterator()
        while (objectArrayIterator.hasNext()) {
            val objectArray = objectArrayIterator.next()
            var arraySize = objectArray.recordSize
            if (arraySize >= DEFAULT_BIG_BITMAP) {
                var arrayName = objectArray.arrayClassName
                Log.i(OOM_ANALYSIS_TAG,
                    "object arrayName:" + arrayName + " objectId:" + objectArray.objectId)
                mLeakingObjectIds.add(objectArray.objectId)
                val leakObject = HeapReport.LeakObject().apply {
                    className = arrayName
                    size = arraySize.toString()
                    objectId =(objectArray.objectId and 0xffffffffL).toString()
                }
                mLeakModel.leakObjects.add(leakObject)
            }
        }
        val endTime = System.currentTimeMillis()
        mLeakModel.runningInfo.filterInstanceTime = ((endTime - startTime).toFloat()/1000).toString()
        Log.i(OOM_ANALYSIS_TAG, "filterLeakingObjects time:" + 1.0f * (endTime - startTime) / 1000)
    }


    private fun findPathsToGcRoot() {
        val startTime = System.currentTimeMillis()
        val heapAnalyzer = HeapAnalyzer(
            OnAnalysisProgressListener{
                Log.i(TAG, "step:" + it.name + ", leaking obj size:" + mLeakingObjectIds.size)
            }
        )
        var findLeakInput = HeapAnalyzer.FindLeakInput(mHeapGraph,AndroidReferenceMatchers.appDefaults,false,
            mutableListOf()
        )
        val (applicationLeaks,libraryLeaks) = with(heapAnalyzer) {
            findLeakInput.findLeaks(mLeakingObjectIds)
        }
        Log.i(OOM_ANALYSIS_TAG,
            "---------------------------Application Leak---------------------------------------")
        //填充application leak
        Log.i(OOM_ANALYSIS_TAG, "ApplicationLeak size:" + applicationLeaks.size)
        for (applicationLeak in applicationLeaks) {
            Log.i(OOM_ANALYSIS_TAG, "shortDescription:" + applicationLeak.shortDescription
                    + ", signature:" + applicationLeak.signature
                    + " same leak size:" + applicationLeak.leakTraces.size
            )
            val(gcRootType,referencePath,leakTraceObject) =applicationLeak.leakTraces[0]
            val gcRoot = gcRootType.description
            val lables = leakTraceObject.labels.toTypedArray()
            leakTraceObject.leakingStatusReason = mLeakReasonTable[leakTraceObject.objectId].toString()
            Log.i(OOM_ANALYSIS_TAG, "GC Root:" + gcRoot
                    + ", leakObjClazz:" + leakTraceObject.className
                    + ", leakObjType:" + leakTraceObject.typeName
                    + ", labels:" + lables.contentToString()
                    + ", leaking reason:" + leakTraceObject.leakingStatusReason
                    + ", leaking obj:" + (leakTraceObject.objectId and 0xffffffffL))
            val leakTraceChainModel = HeapReport.GCPath().apply {
                this.instanceCount = applicationLeak.leakTraces.size
                this.leakReason = leakTraceObject.leakingStatusReason
                this.gcRoot = gcRoot
                this.signature = applicationLeak.signature
            }.also {
                mLeakModel.gcPaths.add(it)
            }

            for (reference in referencePath) {
               var referenceName = reference.referenceName
                var clazz = reference.originObject.className
                var referenceDisplayName =reference.referenceDisplayName
                var referenceGenericName = reference.referenceGenericName
                var referenceType = reference.referenceType.toString()
                var decleardClassName = reference.owningClassName
                Log.i(OOM_ANALYSIS_TAG, "clazz:" + clazz +
                        ", referenceName:" + referenceName
                        + ", referenceDisplayName:" + referenceDisplayName
                        + ", referenceGenericName:" + referenceGenericName
                        + ", referenceType:" + referenceType
                        + ", declaredClassName:" + decleardClassName)
                val leakPathItem = HeapReport.GCPath.PathItem().apply {
                    this.reference = if (referenceDisplayName.startsWith("["))
                        clazz
                    else
                        "$clazz.$referenceDisplayName"
                    this.referenceType =referenceType
                    this.declaredClass = decleardClassName
                }
                leakTraceChainModel.path.add(leakPathItem)
            }
            leakTraceChainModel.path.add(HeapReport.GCPath.PathItem().apply {
                reference = leakTraceObject.className
                referenceType = leakTraceObject.typeName
            })
            break
        }
        Log.i(OOM_ANALYSIS_TAG,
            "=======================================================================")
        var endTime =System.currentTimeMillis()
        mLeakModel.runningInfo.findGCPathTime = ((endTime-startTime).toFloat()/1000).toString()
        Log.i(OOM_ANALYSIS_TAG, "findPathsToGcRoot cost time: "
                + (endTime - startTime).toFloat() / 1000)

    }

    private fun fillJsonFile(jsonFile: String?) {
        var json = Gson().toJson(mLeakModel)
        jsonFile.let {
            File(it)
        }?.writeText(json)
        Log.i(OOM_ANALYSIS_TAG, "JSON write success: $json")

    }

    private fun updateClassObjectCounterMap(classObCountMap:MutableMap<Long,ObjectCounter>,instanceClassId:Long,
                                            isLeak:Boolean):ObjectCounter{
        var objectCounter = classObCountMap[instanceClassId]?:ObjectCounter().also {
            classObCountMap[instanceClassId] = it
        }
        objectCounter.allCnt++
        if (isLeak) {
            objectCounter.leakCnt++
        }
        return objectCounter
    }

    class ObjectCounter{
        var allCnt = 0
        var leakCnt = 0
    }

}