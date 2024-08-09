package com.source.hmileak

abstract class MonitorConfig<M> {

    interface Builder<C: MonitorConfig<*>> {
        fun build():C
    }
}