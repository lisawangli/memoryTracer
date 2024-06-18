package com.source.log

import android.util.Log

class Logger {


    companion object {

        init {
            Log.e("Logger","===init=========");
            System.loadLibrary("liblog") // 假设库名为native_logger

        }

        @JvmStatic
        external  fun  init(filepath:String,bufferSize:Int):Long

        @JvmStatic
        external fun writeLog(mappedRegionPtr: Long,logMessage: String)
    }


}
