#include <jni.h>
#include <string>
#include <stdio.h>
extern "C" JNIEXPORT jstring JNICALL
Java_com_source_nativeleak_NativeLib_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}