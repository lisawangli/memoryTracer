package com.source.memorytracer

import android.app.ActivityManager
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.source.hmileak.util.getFreeMemory
import android.widget.TextView
import com.source.log.Logger
import com.source.memorytracer.databinding.ActivityMainBinding
import java.io.IOException
import java.io.RandomAccessFile


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)



        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val logFilePath = "/sdcard/Download/log.txt"
        val bufferSize = 1024 * 1024 // 1MB
        var strP = Logger.init(logFilePath, bufferSize);
        var start = System.currentTimeMillis();
        repeat(100000) { i ->
            Logger.writeLog(strP, "Log entry #$i\n")
        }
        Logger.writeLog(strP,"Application started.\n");
        Log.e("MainActivity","time:"+(System.currentTimeMillis() - start));
        // Example of a call to a native method
        binding.sampleText.text = stringFromJNI()

//        var config:Config = Config.Builder().setApplication(application).build()
//        OOMMonitor.init(config)
//        OOMMonitor.startLoop()

        var total = getFreeMemory()
        Log.e("MainActivity", "free memory: $total")

    }

    /**
     * A native method that is implemented by the 'memorytracer' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'memorytracer' library on application startup.
        init {
            System.loadLibrary("memorytracer")
        }
    }




}