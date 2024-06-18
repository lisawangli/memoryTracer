package com.source.hmileak.base

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

private var _currentActivity:WeakReference<Activity>?=null
val Application.currentActivity: Activity?
    get() =_currentActivity?.get()

private var _isForeground = false

val Application.isForeground
    get() = _isForeground

private val _lifecycleEventObservers = CopyOnWriteArrayList<LifecycleEventObserver>()

fun Application.registerProcessLifecycleObserver(observer: LifecycleEventObserver) =
    _lifecycleEventObservers.add(observer)

fun Application.unregisterProcessLifeCycleObserver(observer: LifecycleEventObserver) =
    _lifecycleEventObservers.remove(observer)

internal fun registerApplicationExtension(config: Config){
    config.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks{
        private fun updateCurrentActivityWeakRef(activity: Activity) {
            _currentActivity = if (_currentActivity?.get() == activity) {
                _currentActivity
            } else {
                WeakReference(activity)
            }
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            updateCurrentActivityWeakRef(activity)
        }

        override fun onActivityStarted(activity: Activity) {
        }

        override fun onActivityResumed(activity: Activity) {
            updateCurrentActivityWeakRef(activity)

        }

        override fun onActivityPaused(activity: Activity) {
            TODO("Not yet implemented")
        }

        override fun onActivityStopped(activity: Activity) {
            TODO("Not yet implemented")
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            TODO("Not yet implemented")
        }

        override fun onActivityDestroyed(activity: Activity) {
            TODO("Not yet implemented")
        }

    })

    ProcessLifecycleOwner.get().lifecycle.addObserver(object : LifecycleEventObserver{
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when(event){
                Lifecycle.Event.ON_START -> _isForeground = true
                Lifecycle.Event.ON_STOP -> _isForeground = false
                else -> Unit
            }
            for (observer in _lifecycleEventObservers) {
                observer.onStateChanged(source, event)
            }
        }

    })
}
