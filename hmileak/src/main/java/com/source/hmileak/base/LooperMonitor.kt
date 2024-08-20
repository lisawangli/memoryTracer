package com.source.hmileak.base

import android.app.Application
import android.os.Handler
import android.util.Log
import com.source.hmileak.Monitor
import com.source.hmileak.util.GlobalThreadPool
import com.source.hmileak.util.async
import java.util.concurrent.Callable

abstract class LooperMonitor<T> : Monitor<T>(), Callable<LooperMonitor.State> {

    companion object{
        private const val DEFAULT_LOOP_INTERVAL = 1000L
    }

    @Volatile
    private var mIsLoopStop = true

    private val mLooperMonitor = object:Runnable{
        override fun run() {
            Log.e("LoopMonitor","==========mLoopRunnable======="+call()+" mIsLoopStop:"+mIsLoopStop)

            if (call()==State.Terminate)
                return
            if (mIsLoopStop)
                return

            getLoopHandler().removeCallbacks(this)
            getLoopHandler().postDelayed(this, getLoopInterval())
        }
    }

    open fun startLoop(cleanData:Boolean = true,postAtFront:Boolean = true,delayMillis:Long = 0L){
        Log.e("LoopMonitor","clearQueue:"+cleanData+"  postAtFront:"+postAtFront+" delayMillis:"+delayMillis)

        if (cleanData)
            getLoopHandler().removeCallbacks(mLooperMonitor);
        if (postAtFront) {
            getLoopHandler().postAtFrontOfQueue(mLooperMonitor)
        }
        else {
            Log.e("LoopMonitor","==============postDelayed============")
            getLoopHandler().postDelayed(mLooperMonitor, delayMillis)
        }
        mIsLoopStop = false
    }



    open fun stopLoop(){
        mIsLoopStop = true
        getLoopHandler().removeCallbacks(mLooperMonitor)
    }

    protected open fun getLoopInterval(): Long {
        return DEFAULT_LOOP_INTERVAL
    }

    protected open fun getLoopHandler(): Handler {
        return commonConfig.loopHandlerInvoker()
    }

    sealed class State{
        object Continue :State();
        object Terminate : State()
    }
}