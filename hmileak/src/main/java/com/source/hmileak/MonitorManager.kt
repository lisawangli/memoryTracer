package com.source.hmileak

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.source.hmileak.base.Config
import org.json.JSONObject
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

object MonitorManager {

    internal val MONITOR_MAP = ConcurrentHashMap<Class<*>,Monitor<*>>()

    internal lateinit var commonConfig: CommonConfig

    @JvmStatic
    fun initConfig(config: CommonConfig) = apply {
        this.commonConfig = config
    }

    @JvmStatic
    fun <M : MonitorConfig<*>> addMonitorConfig(_config:M) = apply {
        var supperType: Type? = _config.javaClass.genericSuperclass
        while (supperType is Class<*>) {
            supperType = supperType.genericSuperclass
        }

        if (supperType !is ParameterizedType) {
            throw java.lang.RuntimeException("config must be parameterized")
        }

        val monitorType = supperType.actualTypeArguments[0] as Class<Monitor<M>>

        Log.e("MonitorManager","supperType:"+supperType)
        if (MONITOR_MAP.containsKey(monitorType)) {
            return@apply
        }

        Log.e("MonitorManager","monitorType:"+monitorType)


        Log.e("MonitorManager","instance11:"+monitorType.getDeclaredField("INSTANCE").get(null))

//        var monitor =  monitorType.getDeclaredField("INSTANCE").get(null) as Monitor<M>
//        Log.e("MonitorManager","monitor:"+monitor)

        var monitor = try {
            monitorType.getDeclaredField("INSTANCE").get(null) as Monitor<M>
        }catch (e:Throwable) {
            monitorType.newInstance() as Monitor<M>
        }

        MONITOR_MAP[monitorType] = monitor

        monitor.init(commonConfig,_config)
    }

    @JvmStatic
    fun getApplication() = commonConfig.application

    @JvmStatic
    fun onApplicationCreate(){
        registerApplicationExtension()
        registerMonitorEventObserver()
    }
    private fun registerMonitorEventObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver{
            private var mHasLogMonitorEvent = false
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_START) {
                    logAllMonitorEvent()
                }
            }

            private fun logAllMonitorEvent() {
                if (mHasLogMonitorEvent) return
                mHasLogMonitorEvent = true
                mutableMapOf<Any?,Any?>()
                    .apply { MONITOR_MAP.forEach{ putAll(it.value.getLogParams())} }
                    .also {
                        Log.e("MonitorManager","switch-stat:"+JSONObject(it).toString())
                    }
            }
        })
    }

}