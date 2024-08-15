package com.source.memorytracer

import android.app.Application
import android.util.Log

import com.source.hmileak.InitTask
import com.source.hmileak.MonitorManager
import com.source.hmileak.OOMHprofUploader
import com.source.hmileak.OOMReportUploader
import com.source.hmileak.base.Config
import com.source.log.Logger
import java.io.File

object OOMMonitorInitTask : InitTask {
  val logFilePath = "/sdcard/Download/log1.txt"
  val bufferSize = 1024 * 1024 // 1MB
  var strP = Logger.init(logFilePath, bufferSize);
  override fun init(application: Application) {
    val config = Config.Builder()
      .setThreadThreshold(50) //50 only for test! Please use default value!
      .setFdThreshold(300) // 300 only for test! Please use default value!
      .setHeapThreshold(0.9f) // 0.9f for test! Please use default value!
      .setLoopInterval(5_000) // 5_000 for test! Please use default value!
      .setHprofUploader(object : OOMHprofUploader {
        override fun upload(file: File, type: OOMHprofUploader.HprofType) {
          Log.e("OOMMonitor", "todo, upload hprof ${file.name} if necessary")
        }
      })
      .setReportUploader(object : OOMReportUploader {
        override fun upload(file: File, content: String) {
          Log.i("OOMMonitorInitTask", content)
          Logger.writeLog(strP,content)
          Log.e("OOMMonitor", "todo, upload report ${file.name} if necessary")
        }
      })
      .build()

    MonitorManager.addMonitorConfig(config)
  }
}