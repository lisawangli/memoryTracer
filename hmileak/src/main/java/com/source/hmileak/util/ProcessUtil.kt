package com.source.hmileak.util

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import com.source.hmileak.base.Config
import java.io.File


enum class Abi {
    ARMEABI_V7A,
    ARM64_V8A,
    UNKNOWN
}
@Volatile
private var mCurrentAbi: Abi = Abi.UNKNOWN
private var mProcessName: String? = null

fun isMainProcess(config: Config) = config.application.packageName == getProcessName(config)

fun getProcessName(config: Config): String? {
    return mProcessName
        ?: getProcessNameByAms(config)?.also { mProcessName = it }
        ?: getProcessNameByProc()?.also { mProcessName = it }
}
private fun getProcessNameByProc(): String? {
    return try {
        File("/proc/" + Process.myPid() + "/" + "cmdline").readText().trim(' ', '\u0000')
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
private fun getProcessNameByAms(config: Config):String?{
    try {
        val activityManager = config.application.getSystemService(Context.ACTIVITY_SERVICE)
                as ActivityManager

        val appProcessList = activityManager.runningAppProcesses
        for (processInfo in appProcessList.orEmpty()) {
            if (processInfo.pid == Process.myPid()) {
                return processInfo.processName
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}