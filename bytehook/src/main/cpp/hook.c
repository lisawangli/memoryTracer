#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <inttypes.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>
#include "bytehook.h"
#define HACKER_TAG            "bytehook_tag"
#define HACKER_JNI_VERSION    JNI_VERSION_1_6




#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wgnu-zero-variadic-macro-arguments"
#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, HACKER_TAG, fmt, ##__VA_ARGS__)
#pragma clang diagnostic pop
typedef void *(*malloc_t)(size_t);
typedef void (*free_t)(void*);

#define OPEN_DEF(fn)                                                                                         \
  static fn##_t fn##_prev = NULL;                                                                            \
  static bytehook_stub_t fn##_stub = NULL;                                                                   \
  static void fn##_hooked_callback(bytehook_stub_t task_stub, int status_code, const char *caller_path_name, \
                                   const char *sym_name, void *new_func, void *prev_func, void *arg) {       \
    if (BYTEHOOK_STATUS_CODE_ORIG_ADDR == status_code) {                                                     \
      fn##_prev = (fn##_t)prev_func;                                                                         \
      LOG(">>>>> save original address: %" PRIxPTR, (uintptr_t)prev_func);                                   \
    } else {                                                                                                 \
      LOG(">>>>> hooked. stub: %" PRIxPTR                                                                    \
          ", status: %d, caller_path_name: %s, sym_name: %s, new_func: %" PRIxPTR ", prev_func: %" PRIxPTR   \
          ", arg: %" PRIxPTR,                                                                                \
          (uintptr_t)task_stub, status_code, caller_path_name, sym_name, (uintptr_t)new_func,                \
          (uintptr_t)prev_func, (uintptr_t)arg);                                                             \
    }                                                                                                        \
  }


OPEN_DEF(malloc)
OPEN_DEF(free)

static void *malloc_proxy(size_t size) {
    void *ptr = BYTEHOOK_CALL_PREV(malloc_proxy, malloc_t, size);
    LOG("malloc_proxy called with size: %zu, returning address: %p\n", size, ptr);
    return ptr;
}

static void free_proxy(void *ptr) {
    BYTEHOOK_CALL_PREV(free_proxy,free_t,ptr);
    LOG("free_proxy called with address: %p\n", ptr);


}

JNIEXPORT static jint JNICALL
Java_com_bytedance_android_bytehook_NativeHook_nativeHook(JNIEnv *env, jclass clazz, jint type) {
    (void )env,(void) clazz;
    if (type==1){

    }

    malloc_stub = bytehook_hook_single("libhookee.so",NULL,"malloc", (void *)malloc_proxy,
                                       malloc_hooked_callback,NULL);
    free_stub = bytehook_hook_single("libhookee.so", NULL, "free", (void *)free_proxy, free_hooked_callback, NULL);

    return 0;
}

JNIEXPORT static jint JNICALL
Java_com_bytedance_android_bytehook_NativeHook_nativeUnhook(JNIEnv *env, jclass clazz) {
    (void) env,(void) clazz;
    if (NULL != malloc_stub) {
        bytehook_unhook(malloc_stub);
        malloc_stub = NULL;
    }
    if (NULL != free_stub) {
        bytehook_unhook(free_stub);
        free_stub = NULL;
    }

    return 0;
}

JNIEXPORT static void JNICALL
Java_com_bytedance_android_bytehook_NativeHook_nativeDumpRecords(JNIEnv *env, jclass clazz,
                                                                 jstring pathname) {
    if (pathname){

    }
    (void) env, (void) clazz;

}

static void hacker_dump_records(JNIEnv *env, jobject thiz, jstring pathname) {
    (void)thiz;

    const char *c_pathname = (*env)->GetStringUTFChars(env, pathname, 0);
    if (NULL == c_pathname) return;

    int fd = open(c_pathname, O_CREAT | O_WRONLY | O_CLOEXEC | O_TRUNC | O_APPEND, S_IRUSR | S_IWUSR);
    if (fd >= 0) {
        bytehook_dump_records(fd, BYTEHOOK_RECORD_ITEM_ALL);
        close(fd);
    }

    (*env)->ReleaseStringUTFChars(env, pathname, c_pathname);
}



