package com.source.hmileak.base

abstract class OOMTracker {

    abstract fun init(config: Config)
    abstract fun track(): Boolean

    abstract fun reason():String

    abstract fun reset()
}