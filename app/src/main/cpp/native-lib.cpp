#include <jni.h>
#include <string>
#include <dlfcn.h>
#include "../../../../bytehook/src/main/cpp/include/bytehook.h"
#include <android/log.h>
#define LOG_ERROR(tag,fmt,...) __android_log_print(ANDROID_LOG_ERROR,tag,fmt,##__VA_ARGS__)

void *my_malloc(size_t size) {
    LOG_ERROR("JNI","Hooked malloc called with size: %zu\n", size);
    void *ptr = malloc(size); // 调用原始的 malloc
    return ptr;
}

// 自定义的 free 函数
void my_free(void *ptr) {
    LOG_ERROR("JNI","Hooked free called with pointer: %p\n", ptr);
    free(ptr); // 调用原始的 free
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_source_memorytracer_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    LOG_ERROR("loglib","Java_com_source_memorytracer_MainActivity_stringFromJNI start");

    int init_result = bytehook_init(0, false);
    if (init_result != 0) {
        LOG_ERROR("stderr", "Bytehook initialization failed\n");
        return (jstring) "1";
    }


    // 设置 hook 到 malloc 和 free
    bytehook_hook_all("libc.so.6", "malloc", (void *)my_malloc, NULL, NULL);
    bytehook_hook_all("libc.so.6", "free", (void *)my_free, NULL, NULL);

    // 测试 hook
    void *ptr = malloc(1024);
    if (ptr != NULL) {
        // 使用分配的内存
        // ...

        // 释放内存
        free(ptr);
    }

    // 清理 Bytehook
    LOG_ERROR("loglib","Java_com_source_memorytracer_MainActivity_stringFromJNI end");


    return env->NewStringUTF(hello.c_str());
}