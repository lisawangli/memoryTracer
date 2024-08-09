package com.source.hmileak.base

import com.source.hmileak.Monitor

abstract class OOMTracker : Monitor<Config>() {

//    abstract fun init(config: Config)
    abstract fun track(): Boolean

    abstract fun reason():String

    abstract fun reset()
}