// Copyright (c) 2020-2022 ByteDance, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//

// Created by Kelun Cai (caikelun@bytedance.com) on 2020-06-02.

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/time.h>
#include <unistd.h>
#include "bytehook.h"
#include "bh_log.h"
#define HACKER_TAG            "bytehook_tag"
#define BH_JNI_VERSION    JNI_VERSION_1_6
#define BH_JNI_CLASS_NAME "com/bytedance/android/bytehook/ByteHook"



#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wgnu-zero-variadic-macro-arguments"
#define LOG(fmt, ...) __android_log_print(ANDROID_LOG_INFO, HACKER_TAG, fmt, ##__VA_ARGS__)
#pragma clang diagnostic pop

typedef void *(*realloc_t)(void *,size_t);
typedef void *(*calloc_t)(size_t,size_t);
typedef void *(*memalign_t)(size_t,size_t);

typedef int (*posix_memalign_t)(void **, size_t, size_t);
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
OPEN_DEF(realloc)
OPEN_DEF(calloc)
OPEN_DEF(memalign)
OPEN_DEF(posix_memalign)
OPEN_DEF(free)

static void *memalign_proxy(size_t ptr,size_t size){
    void *new_ptr = BYTEHOOK_CALL_PREV(memalign_proxy,memalign_t ,ptr,size);
    LOG("memalign_proxy called with alignment: %zu, size: %zu, returning address: %p\n", ptr, size, new_ptr);
    return new_ptr;

}

static int posix_memalign_proxy(void **memptr,size_t alignment,size_t size) {
    int result = BYTEHOOK_CALL_PREV(posix_memalign_proxy,posix_memalign_t,memptr,alignment,size);
    if (result==0){
        LOG("posix_memalign_proxy called with alignment: %zu, size: %zu, returning pointer: %p\n", alignment, size, *memptr);
    }
    return result;
}

static void *realloc_proxy(void *ptr,size_t size){
    void *new_ptr = BYTEHOOK_CALL_PREV(realloc_proxy,realloc_t,ptr,size);
    LOG("realloc_proxy called with ptr: %p, size: %zu, returning address: %p\n", ptr, size, new_ptr);
    return new_ptr;
}

static void *calloc_proxy(size_t ptr,size_t size){
    void *new_ptr = BYTEHOOK_CALL_PREV(realloc_proxy,calloc_t ,ptr,size);
    LOG("calloc_proxy called with nmemb: %zu, size: %zu, returning address: %p\n", ptr, size, ptr);
    return new_ptr;
}


static void *malloc_proxy(size_t size) {
    void *ptr = BYTEHOOK_CALL_PREV(malloc_proxy, malloc_t, size);
    LOG("malloc_proxy called with size: %zu, returning address: %p\n", size, ptr);
    return ptr;
}

static void free_proxy(void *ptr) {
    BYTEHOOK_CALL_PREV(free_proxy,free_t,ptr);
    LOG("free_proxy called with address: %p\n", ptr);

}

static int hacker_hook(JNIEnv *env, jobject thiz, jint type) {
    (void)env, (void)thiz;
    if (type==1){

    }

    malloc_stub = bytehook_hook_single("libmemorytracer.so",NULL,"malloc", (void *)malloc_proxy,
                                       malloc_hooked_callback,NULL);
    free_stub = bytehook_hook_single("libmemorytracer.so", NULL, "free", (void *)free_proxy, free_hooked_callback, NULL);

    calloc_stub = bytehook_hook_single("libmemorytracer.so",NULL, "calloc",(void *) calloc_proxy,
                                       calloc_hooked_callback,NULL);
    realloc_stub = bytehook_hook_single("libmemorytracer.so",NULL,"realloc",(void *) realloc_proxy,realloc_hooked_callback,NULL);
    memalign_stub = bytehook_hook_single("libmemorytracer.so",NULL,"memalign",(void *) memalign_proxy,memalign_hooked_callback,NULL);
    posix_memalign_stub = bytehook_hook_single("libmemorytracer.so",NULL,"posix_memalign",(void *) posix_memalign_proxy,posix_memalign_hooked_callback,NULL);
    return 0;
}

static int hacker_unhook(JNIEnv *env, jobject thiz) {
    (void)env, (void)thiz;
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

static jstring bh_jni_get_version(JNIEnv *env, jobject thiz) {
  (void)thiz;
  return (*env)->NewStringUTF(env, bytehook_get_version());
}

static jint bh_jni_init(JNIEnv *env, jobject thiz, jint mode, jboolean debug) {
  (void)env;
  (void)thiz;

  return bytehook_init((int)mode, (bool)debug);
}

static jint bh_jni_add_ignore(JNIEnv *env, jobject thiz, jstring caller_path_name) {
  (void)env;
  (void)thiz;

  int r = BYTEHOOK_STATUS_CODE_IGNORE;
  if (!caller_path_name) return r;

  const char *c_caller_path_name;
  if (NULL == (c_caller_path_name = (*env)->GetStringUTFChars(env, caller_path_name, 0))) goto clean;
  r = bytehook_add_ignore(c_caller_path_name);

clean:
  if (caller_path_name && c_caller_path_name)
    (*env)->ReleaseStringUTFChars(env, caller_path_name, c_caller_path_name);
  return r;
}

static jint bh_jni_get_mode(JNIEnv *env, jobject thiz) {
  (void)env, (void)thiz;

  return BYTEHOOK_MODE_AUTOMATIC == bytehook_get_mode() ? 0 : 1;
}

static jboolean bh_jni_get_debug(JNIEnv *env, jobject thiz) {
  (void)env, (void)thiz;

  return bytehook_get_debug();
}

static void bh_jni_set_debug(JNIEnv *env, jobject thiz, jboolean debug) {
  (void)env;
  (void)thiz;

  bytehook_set_debug((bool)debug);
}

static jboolean bh_jni_get_recordable(JNIEnv *env, jobject thiz) {
  (void)env, (void)thiz;

  return bytehook_get_recordable();
}

static void bh_jni_set_recordable(JNIEnv *env, jobject thiz, jboolean recordable) {
  (void)env, (void)thiz;

  bytehook_set_recordable((bool)recordable);
}

static jstring bh_jni_get_records(JNIEnv *env, jobject thiz, jint item_flags) {
  (void)thiz;

  char *str = bytehook_get_records((uint32_t)item_flags);
  if (NULL == str) return NULL;

  jstring jstr = (*env)->NewStringUTF(env, str);
  free(str);
  return jstr;
}



static jstring bh_jni_get_arch(JNIEnv *env, jobject thiz) {
  (void)thiz;

#if defined(__arm__)
  char *arch = "arm";
#elif defined(__aarch64__)
  char *arch = "arm64";
#elif defined(__i386__)
  char *arch = "x86";
#elif defined(__x86_64__)
  char *arch = "x86_64";
#else
  char *arch = "unsupported";
#endif

  return (*env)->NewStringUTF(env, arch);
}

static void bh_jni_memoryhook(JNIEnv *env, jobject thiz){
  (void)env, (void)thiz;
  BH_LOG_SHOW("bytehook  bh_jni_memoryhook");



}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
  (void)reserved;

  if (__predict_false(NULL == vm)) return JNI_ERR;

  JNIEnv *env;
  if (__predict_false(JNI_OK != (*vm)->GetEnv(vm, (void **)&env, BH_JNI_VERSION))) return JNI_ERR;
  if (__predict_false(NULL == env || NULL == *env)) return JNI_ERR;

  jclass cls;
  if (__predict_false(NULL == (cls = (*env)->FindClass(env, BH_JNI_CLASS_NAME)))) return JNI_ERR;

  JNINativeMethod m[] = {{"nativeGetVersion", "()Ljava/lang/String;", (void *)bh_jni_get_version},
                         {"nativeInit", "(IZ)I", (void *)bh_jni_init},
                         {"nativeAddIgnore", "(Ljava/lang/String;)I", (void *)bh_jni_add_ignore},
                         {"nativeGetMode", "()I", (void *)bh_jni_get_mode},
                         {"nativeGetDebug", "()Z", (void *)bh_jni_get_debug},
                         {"nativeSetDebug", "(Z)V", (void *)bh_jni_set_debug},
                         {"nativeGetRecordable", "()Z", (void *)bh_jni_get_recordable},
                         {"nativeSetRecordable", "(Z)V", (void *)bh_jni_set_recordable},
                         {"nativeGetRecords", "(I)Ljava/lang/String;", (void *)bh_jni_get_records},
                         {"nativeGetArch", "()Ljava/lang/String;", (void *)bh_jni_get_arch},
                         {"nativeHook", "(I)I", (void *)hacker_hook},
                         {"nativeUnhook", "()I", (void *)hacker_unhook},
                         {"nativeDumpRecords", "(Ljava/lang/String;)V", (void *)hacker_dump_records},
                         {"memoryHook", "()V", (void *)bh_jni_memoryhook}

  };
  if (__predict_false(0 != (*env)->RegisterNatives(env, cls, m, sizeof(m) / sizeof(m[0])))) return JNI_ERR;

  return BH_JNI_VERSION;
}

