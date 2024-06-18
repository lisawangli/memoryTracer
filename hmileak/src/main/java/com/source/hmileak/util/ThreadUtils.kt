package com.source.hmileak.util

import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

internal val mainHandler = Handler(Looper.getMainLooper())


object GlobalThreadPool {
    private val executor: ExecutorService = Executors.newFixedThreadPool(5) // 例如，创建一个固定大小为5的线程池

    fun executor(): ExecutorService = executor
}
fun async(delayMills: Long = 0L, block: () -> Unit) {
    if (delayMills != 0L) {
        mainHandler.postDelayed({
            GlobalThreadPool.executor().submit(block)
                ?: thread { block() }
        }, delayMills)
    } else {
        GlobalThreadPool.executor().submit(block)
            ?: thread { block() }
    }
}

fun postOnMainThread(delayMills: Long = 0L, block: () -> Unit) {
    mainHandler.postDelayed(block, delayMills)
}

fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        block()
    } else {
        mainHandler.post(block)
    }
}

fun postOnMainThread(delay: Long = 0L, runnable: Runnable) {
    mainHandler.postDelayed(runnable, delay)
}

fun removeCallbacks(runnable: Runnable) {
    mainHandler.removeCallbacks(runnable)
}
