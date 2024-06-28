package com.source.hprofanalyzer.analysis

import android.os.Bundle
import android.os.ResultReceiver

class AnalysisReceiver :ResultReceiver(null) {

    private var mResultCallback:ResultCallback?=null

    fun setResultCallback(resultCallback: ResultCallback){
        mResultCallback = resultCallback;
    }

    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        super.onReceiveResult(resultCode, resultData)
        if (mResultCallback!=null){
            if (resultCode== RESULT_CODE_OK){
                mResultCallback!!.onSuccess()
            } else {
                mResultCallback!!.onError()
            }
        }
    }

    interface ResultCallback{
        fun onSuccess()

        fun onError()
    }

    companion object {
        const val RESULT_CODE_OK = 1001
        const val RESULT_CODE_FAIL = 1002
    }
}