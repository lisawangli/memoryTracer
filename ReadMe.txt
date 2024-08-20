集成到项目中:
在自己的项目中：
gradle加入：
 implementation(files("libs/bytehook-debug.aar"))
    implementation(files("libs/log-debug.aar"))
    implementation(files("libs/hprofanalyzer-debug.aar"))
    implementation(files("libs/hmileak-debug.aar"))

     implementation("androidx.lifecycle:lifecycle-process:2.8.2")
        implementation("com.squareup.okio:okio:1.14.0")
        implementation("com.google.code.gson:gson:2.8.2")

        implementation "com.source.hmileak:plugin:1.0.2"
            implementation 'com.source.hprofanalyzer:plugin:1.0.1'


 在appliction中加入：
 DefaultInitTask.init(this)
OOMMonitorInitTask.INSTANCE.init(this);
            OOMMonitor.INSTANCE.startLoop(true,false,5000L);

 自定义的话，查看demo的OOMMonitorInitTask
