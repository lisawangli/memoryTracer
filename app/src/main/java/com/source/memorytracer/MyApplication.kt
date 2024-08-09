package com.source.memorytracer

import android.app.Application
import com.source.hmileak.DefaultInitTask
import com.source.hmileak.MonitorManager
import com.source.hmileak.base.Config

class MyApplication:Application() {

    override fun onCreate() {
        super.onCreate()
        DefaultInitTask.init(this)
    }
}