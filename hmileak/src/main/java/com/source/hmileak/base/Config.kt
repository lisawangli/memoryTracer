package com.source.hmileak.base

import android.app.Application
import android.os.Handler

class Config private constructor(
    val application: Application,
    internal val loopHandlerInvoker: () -> Handler
){

    class Builder{
        private lateinit var mApplication: Application

        private var mLoopHandlerInvoker: (() -> Handler)? = null

        fun setApplication(application: Application) = apply {
            mApplication = application
        }
    fun setLoopHandlerInvoker(loopHandlerInvoker: () -> Handler) = apply {
        this.mLoopHandlerInvoker = loopHandlerInvoker
    }

    fun build():Config = Config (
        application = mApplication,

        loopHandlerInvoker = mLoopHandlerInvoker ?: { LoopThread.LOOP_HANDLER }

    )

    }

}