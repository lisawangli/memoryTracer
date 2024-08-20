package com.source.memorytracer

import android.app.Application
import com.source.hmileak.DefaultInitTask
import com.source.hmileak.OOMMonitor.startLoop
import com.source.memorytracer.OOMMonitorInitTask.init

class MyApplication:Application() {

    override fun onCreate() {
        super.onCreate()
        DefaultInitTask.init(this)
        init(this@MyApplication)
        startLoop(true, false, 5000L)
    }
}