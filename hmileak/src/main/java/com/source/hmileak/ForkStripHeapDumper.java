package com.source.hmileak;

import com.source.hprofanalyzer.ForkJvmHeap;
import com.source.hprofanalyzer.HeapDumper;

public class ForkStripHeapDumper implements HeapDumper {

    boolean mLoadSuccess;

    private static class Holder{
        private static final ForkStripHeapDumper INSTANCE = new ForkStripHeapDumper();
    }

    public static ForkStripHeapDumper getInstance() {
        return Holder.INSTANCE;
    }

    private void init() {
        if (mLoadSuccess)
            return;
        System.loadLibrary("strip_dump");
        mLoadSuccess = true;
        initStripDump();
    }

    @Override
    public boolean dump(String path) {
        init();
        hprofName(path);
        boolean dumpRes = ForkJvmHeap.getInstance().dump(path);
        return dumpRes;
    }

    public native void initStripDump();

    public native void hprofName(String name);
}
