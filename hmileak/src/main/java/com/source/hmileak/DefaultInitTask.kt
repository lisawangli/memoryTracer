package com.source.hmileak

import android.app.Application
import android.os.Build

object DefaultInitTask:InitTask {
    override fun init(application: Application) {
        val config = CommonConfig.Builder()
            .setApplication(application) // Set application
            .setVersionNameInvoker { "1.0.0" } // Set version name, java leak feature use it
            .setSdkVersionMatch(
                Build.VERSION.SDK_INT <= 34 &&
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
            )  // Set if current sdk version is supported
            .build()
        MonitorManager.initConfig(config).apply {
            onApplicationCreate()
        }
    }
}