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
void* threadFunction(void* arg) {
    int threadId = *((int*)arg); // 将传入的参数转换为int类型
    printf("Hello, World! from thread %d\n", threadId);
    free(arg); // 释放传入的参数内存
    pthread_exit(NULL); // 线程结束
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


    pthread_t thread; // 用于存储新线程的标识符
    int* threadId = static_cast<int *>(malloc(sizeof(int))); // 分配空间用于保存线程ID
    *threadId = 1; // 设置线程ID

    // 创建新线程，传入线程函数和参数
    if (pthread_create(&thread, NULL, threadFunction, (void*)threadId) != 0) {
        LOG_ERROR("stderr", "Failed to create thread\n");
        exit(EXIT_FAILURE);
    }

    // 等待新线程结束
    if (pthread_join(thread, NULL) != 0) {
        LOG_ERROR("stderr", "Failed to join thread\n");
        exit(EXIT_FAILURE);
    }

    LOG_ERROR("test","Hello, World! from main thread");
//    int *array = (int *)malloc(5 * sizeof(int)); // 初始分配5个整数
//    if (array == NULL) {
//        fprintf(stderr, "Memory allocation failed\n");
//    }
//
//    // 使用分配的内存
//    for (int i = 0; i < 5; ++i) {
//        array[i] = i;
//    }
//
//    // 需要更多的空间
//    array = (int *)realloc(array, 10 * sizeof(int)); // 重新分配10个整数
//    if (array == NULL) {
//        fprintf(stderr, "Memory reallocation failed\n");
//    }
//
//    // 扩展数组
//    for (int i = 5; i < 10; ++i) {
//        array[i] = i;
//    }
//
//    // 打印数组内容
//    for (int i = 0; i < 10; ++i) {
//        printf("%d ", array[i]);
//    }
//    printf("\n");
//
//    // 释放内存
//    free(array);
    // 清理 Bytehook
    LOG_ERROR("loglib","Java_com_source_memorytracer_MainActivity_stringFromJNI end");

    return env->NewStringUTF(hello.c_str());
}