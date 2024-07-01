package com.source.memorytracer

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import com.bytedance.android.bytehook.ByteHook
import com.bytedance.android.bytehook.ByteHook.ConfigBuilder
import com.source.hmileak.util.PermissionUtil
import com.source.log.Logger
import com.source.memorytracer.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        // init bytehook
        val r = ByteHook.init(
            ConfigBuilder()
                .setMode(ByteHook.Mode.AUTOMATIC) //                .setMode(ByteHook.Mode.MANUAL)
                .setDebug(true)
                .setRecordable(true)
                .build()
        )



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

//        NativeHook.hook(1)
            ByteHook.nativeHook(1)

        binding.sampleText.text = stringFromJNI()
        binding.sampleText.setOnClickListener(object : OnClickListener {
            override fun onClick(v: View?) {
//               NativeHook.dumpRecords("")
            }

        } )

        ByteHook.nativeUnhook()

//        var config:Config = Config.Builder().setApplication(application).build()
//        OOMMonitor.init(config)
//        OOMMonitor.startLoop()
        // <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"/>
        //    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"/>
        if(!PermissionUtil.hasPermission(this,"android.permission.WRITE_EXTERNAL_STORAGE"
                , "android.permission.READ_EXTERNAL_STORAGE")){
            PermissionUtil.requestPermissions(this,"android.permission.WRITE_EXTERNAL_STORAGE"
                    , "android.permission.READ_EXTERNAL_STORAGE",requestCode=100, listener=null)
        }

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