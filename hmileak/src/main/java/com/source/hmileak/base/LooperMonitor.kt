package com.source.hmileak.base

import android.app.Application
import android.os.Handler
import java.util.concurrent.Callable

abstract class LooperMonitor<T> : Callable<LooperMonitor.State> {

    private lateinit var mConfig:Config;

    open fun init(config: Config){
        this.mConfig = config;
    }

    companion object{
        private const val DEFAULT_LOOP_INTERVAL = 1000L
    }

    @Volatile
    private var mIsLoopStop = true

    private val mLooperMonitor = object:Runnable{
        override fun run() {
            if (call()==State.Terminate)
                return
            if (mIsLoopStop)
                return

        }
    }

    open fun startLoop(cleanData:Boolean = true,postAtFront:Boolean = true,delayMillis:Long = 0L){
        if (cleanData)
            getLooperHandler().removeCallbacks(mLooperMonitor);
        if (postAtFront)
            getLooperHandler().postAtFrontOfQueue(mLooperMonitor)
        else
            getLooperHandler().postDelayed(mLooperMonitor,delayMillis)
        mIsLoopStop = false
    }

    protected open fun getLoopInterval(): Long {
        return DEFAULT_LOOP_INTERVAL
    }

    open fun stopLoop(){
        mIsLoopStop = true
        getLooperHandler().removeCallbacks(mLooperMonitor)
    }

    protected open fun getLooperHandler() : Handler  {
        return mConfig.loopHandlerInvoker();
    }

    protected open fun getApplication() : Application  {
        return mConfig.application;
    }

    sealed class State{
        object Continue :State();
        object Terminate : State()
    }
}