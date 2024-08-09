package com.source.hmileak

import android.app.Application

interface InitTask {
    fun init(application: Application)
}