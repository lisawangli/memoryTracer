package com.source.hmileak

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.source.hmileak.MonitorManager.getApplication
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

private var _currentActivity: WeakReference<Activity>? = null
val Application.currentActivity: Activity?
    get() = _currentActivity?.get()

private var _isForeground = true
val Application.isForeground
    get() = _isForeground

private val _lifecycleEventObservers = CopyOnWriteArrayList<LifecycleEventObserver>()
fun Application.registerProcessLifecycleObserver(observer: LifecycleEventObserver) =
    _lifecycleEventObservers.add(observer)
fun Application.unregisterProcessLifecycleObserver(observer: LifecycleEventObserver) =
    _lifecycleEventObservers.remove(observer)

fun sdkVersionMatch(): Boolean {
    return MonitorManager.commonConfig.sdkVersionMatch
}

internal fun registerApplicationExtension() {
    getApplication().registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
        private fun updateCurrentActivityWeakRef(activity: Activity) {
            _currentActivity = if (_currentActivity?.get() == activity) {
                _currentActivity
            } else {
                WeakReference(activity)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            Log.e("MonitorApplication","onActivityCreated")
            _isForeground = true
            updateCurrentActivityWeakRef(activity)
        }

        override fun onActivityStarted(activity: Activity) {}

        override fun onActivityResumed(activity: Activity) {
            updateCurrentActivityWeakRef(activity)
        }

        override fun onActivityPaused(activity: Activity) {}

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityStopped(activity: Activity) {
            _isForeground = false

        }

        override fun onActivityDestroyed(activity: Activity) {}
    })

    ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            Log.e("MonitorApplication","event:"+event);
            when (event) {
                Lifecycle.Event.ON_START -> _isForeground = true
                Lifecycle.Event.ON_STOP -> _isForeground = false
                else -> Unit
            }

            for (lifecycleEventObserver in _lifecycleEventObservers) {
                lifecycleEventObserver.onStateChanged(source, event)
            }
        }
    })
}