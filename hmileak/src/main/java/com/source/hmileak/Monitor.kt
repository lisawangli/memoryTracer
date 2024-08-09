package com.source.hmileak

import com.source.hmileak.base.Config

abstract class Monitor<C> {

    private var _commonConfig: CommonConfig? = null
    protected val commonConfig: CommonConfig
        get() = _commonConfig!!

    private var _monitorConfig:C? = null
    protected val monitorConfig:C
        get() = _monitorConfig!!

    open var isInitialized = false

    protected inline fun throwIfNotInitialized( onDebug: () -> Unit = {
        throw RuntimeException("Monitor is not initialized")
    },
                                                onRelease: () -> Unit){
        if (isInitialized)
            return
    }

    protected fun Boolean.syncToInitialized() = apply {
        isInitialized = this && isInitialized
    }

    open fun init(commonConfig: CommonConfig,monitor: C) {
        _commonConfig = commonConfig;
        _monitorConfig = monitor
        isInitialized = true
    }

   open fun getLogParams(): Map<String,Any> {
       return mapOf("${javaClass.simpleName.decapitalize()}ingEnabled" to isInitialized)
   }
}