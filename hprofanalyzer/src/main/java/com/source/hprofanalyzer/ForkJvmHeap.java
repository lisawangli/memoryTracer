package com.source.hprofanalyzer;

import android.os.Debug;
import android.util.Log;

import java.io.IOException;

public class ForkJvmHeap implements HeapDumper{

    private static class Holder{
        private static final ForkJvmHeap INSTANCE = new ForkJvmHeap();
    }

    public static ForkJvmHeap getInstance(){
        return Holder.INSTANCE;
    }

    private void init(){
        System.loadLibrary("fast-dump");
        nativeInit();
    }

    private native void nativeInit();

    private native int suspendAndFork();

    private native boolean resumeAndWait(int pid);

    private native void exitProcess();

    @Override
    public boolean dump(String path) {
        Log.e("ForkJvmHeap","==========dump=========");
        init();
        Log.e("ForkJvmHeap","==========dump=====init====");

        boolean dumpRes = false;
        int pid = suspendAndFork();
        Log.e("ForkJvmHeap","==========dump=====suspendAndFork====");

        if (pid==0){
            try {
                Debug.dumpHprofData(path);
                Log.e("ForkJvmHeap","==========dump=====dumpHprofData====");

                exitProcess();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (pid > 0) {
            dumpRes = resumeAndWait(pid);
        }
        return dumpRes;
    }


}
