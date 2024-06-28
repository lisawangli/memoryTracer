#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include "hprof_dump.h"
#include "linker/mydlfcn.h"
#include <pthread.h>
#include <unistd.h>
#include <wait.h>
#include <string>
#include "hprof_dump.h"
#define LOG_TAG "JNIBridge"


extern "C"
JNIEXPORT void JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_nativeInit(JNIEnv *env, jobject thiz) {
    leak_monitor::HprofDump::getInstance().Initialize();
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_suspendAndFork(JNIEnv *env, jobject thiz) {
    return leak_monitor::HprofDump::getInstance().SuspendAndFork();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_resumeAndWait(JNIEnv *env, jobject thiz, jint pid) {
    return leak_monitor::HprofDump::getInstance().ResumeAndWait(pid);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_exitProcess(JNIEnv *env, jobject thiz) {
    _exit(0);
}