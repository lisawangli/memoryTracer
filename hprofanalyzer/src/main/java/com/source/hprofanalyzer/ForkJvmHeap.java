package com.source.hprofanalyzer;

public class ForkJvmHeap {

    private native void nativeInit();

    private native int suspendAndFork();

    private native boolean resumeAndWait(int pid);

    private native void exitProcess();
}
