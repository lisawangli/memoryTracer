package com.source.memorytracer;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.source.hmileak.ForkStripHeapDumper;
import com.source.hmileak.OOMMonitor;
import com.source.memorytracer.test.LeakMaker;
import com.source.memorytracer.R;
import java.io.File;

public class JavaLeakTestActivity extends AppCompatActivity {

    public static void start(Context context) {
        context.startActivity(new Intent(context, JavaLeakTestActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_java_leak_test);
    }

    public void onClick(View v){
        if (v.getId() == R.id.btn_make_java_leak) {
            OOMMonitorInitTask.INSTANCE.init(JavaLeakTestActivity.this.getApplication());
            OOMMonitor.INSTANCE.startLoop(true,false,5000L);
            LeakMaker.makeLeak(this);
        } else if (v.getId() == R.id.btn_hprof_dump) {
            showHprofDumpHint();

            //Pull the hprof from the devices.
            //adb shell "run-as com.kwai.koom.demo cat 'files/test.hprof'" > ~/temp/test.hprof
            ForkStripHeapDumper.getInstance().dump(
                    this.getFilesDir().getAbsolutePath() + File.separator + "test.hprof");
        }

    }

    private void showJavaLeakHint() {
        findViewById(R.id.btn_make_java_leak).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_hprof_dump).setVisibility(View.INVISIBLE);
        findViewById(R.id.tv_make_java_leak_hint).setVisibility(View.VISIBLE);
    }
    private void showHprofDumpHint() {
        findViewById(R.id.btn_make_java_leak).setVisibility(View.INVISIBLE);
        findViewById(R.id.btn_hprof_dump).setVisibility(View.INVISIBLE);
        findViewById(R.id.tv_hprof_dump_hint).setVisibility(View.VISIBLE);
    }
}
