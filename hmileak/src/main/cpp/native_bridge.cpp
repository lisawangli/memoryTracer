#include <jni.h>

//
// Created by ts on 2024/8/8.
//
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include "./include/hprof_strip.h"
#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <wait.h>

#include <string>
extern "C"
JNIEXPORT void JNICALL
Java_com_source_hmileak_ForkStripHeapDumper_initStripDump(JNIEnv *, jobject ) {
    HprofStrip::HookInit();
}

extern "C"
JNIEXPORT void JNICALL
Java_com_source_hmileak_ForkStripHeapDumper_hprofName(JNIEnv *env, jobject , jstring name) {
    const char *hprofName =env->GetStringUTFChars(name, nullptr);
    HprofStrip::getInstance().setHprofName(hprofName);
    env->ReleaseStringUTFChars(name,hprofName);
}