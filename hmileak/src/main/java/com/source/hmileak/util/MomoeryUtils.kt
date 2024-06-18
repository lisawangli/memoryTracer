package com.source.hmileak.util

import android.app.ActivityManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import java.io.RandomAccessFile

/** 获取可用内存 */
fun getFreeMemory(): Long {
    val file = "/proc/meminfo"
    var reader: RandomAccessFile? = null
    try {
        reader = RandomAccessFile(file, "r")
        val load = reader.readLine()
        val tokens = load.split("\\s+".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()
        val value = tokens[1]
        // 返回当前空闲内存，单位是kB
        return value.toLong() * 1024
    } catch (e: IOException) {
        e.printStackTrace()
    } finally {
        if (reader != null) {
            try {
                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
    return -1
}

/** 获取可用内存 */
fun getAvailableMemory(context:Context): Long {
    val activityManager = context.getSystemService(AppCompatActivity.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    val availableMemory = memoryInfo.availMem
    return availableMemory
}