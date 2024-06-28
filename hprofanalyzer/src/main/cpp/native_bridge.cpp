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
#define LOG_TAG "JNIBridge"


extern "C"
JNIEXPORT void JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_nativeInit(JNIEnv *env, jobject thiz) {
    // TODO: implement nativeInit()
}
extern "C"
JNIEXPORT jint JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_suspendAndFork(JNIEnv *env, jobject thiz) {
    // TODO: implement suspendAndFork()
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_resumeAndWait(JNIEnv *env, jobject thiz, jint pid) {
    // TODO: implement resumeAndWait()
}
extern "C"
JNIEXPORT void JNICALL
Java_com_source_hprofanalyzer_ForkJvmHeap_exitProcess(JNIEnv *env, jobject thiz) {
    // TODO: implement exitProcess()
}