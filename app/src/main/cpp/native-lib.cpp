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

//    int init_result = bytehook_init(0, false);
//    if (init_result != 0) {
//        LOG_ERROR("stderr", "Bytehook initialization failed\n");
//        return (jstring) "1";
//    }



    int *array = (int *)malloc(5 * sizeof(int)); // 初始分配5个整数
    if (array == NULL) {
        fprintf(stderr, "Memory allocation failed\n");
    }

    // 使用分配的内存
    for (int i = 0; i < 5; ++i) {
        array[i] = i;
    }

    // 需要更多的空间
    array = (int *)realloc(array, 10 * sizeof(int)); // 重新分配10个整数
    if (array == NULL) {
        fprintf(stderr, "Memory reallocation failed\n");
    }

    // 扩展数组
    for (int i = 5; i < 10; ++i) {
        array[i] = i;
    }

    // 打印数组内容
    for (int i = 0; i < 10; ++i) {
        printf("%d ", array[i]);
    }
    printf("\n");

    // 释放内存
    free(array);
    // 清理 Bytehook
    LOG_ERROR("loglib","Java_com_source_memorytracer_MainActivity_stringFromJNI end");

    return env->NewStringUTF(hello.c_str());
}