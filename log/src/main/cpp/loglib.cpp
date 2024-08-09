#include <jni.h>
#include <android/log.h>
#include <sys/mman.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <errno.h>
#include <mutex>
#include <vector>


#define LOG_ERROR(tag,fmt,...) __android_log_print(ANDROID_LOG_ERROR,tag,fmt,##__VA_ARGS__)
#define LOG_DEBUG(tag,fmt,...) __android_log_print(ANDROID_LOG_ERROR,tag,fmt,##__VA_ARGS__)
#define LOG_DEBUG(tag,fmt,...) __android_log_print(ANDROID_LOG_DEBUG,tag,fmt,##__VA_ARGS__)
#define LOG_INFO(tag,fmt,...) __android_log_print(ANDROID_LOG_INFO,tag,fmt,##__VA_ARGS__)


static char* gMappedRegion = nullptr;
static size_t gBufferSize;
static size_t gCurrentOffset = 0;

static size_t gBufferSizeToFlush = 4096;
static std::mutex gMutex;

extern "C"
JNIEXPORT jlong  JNICALL
Java_com_source_log_Logger_init(JNIEnv *env, jclass clazz, jstring filepath, jint buffer_size) {
    const char* path = env->GetStringUTFChars(filepath, nullptr);
    if (!path) return -1;
    int fd = open(path,O_RDWR|O_CREAT|O_APPEND,S_IRUSR|S_IWUSR);
    if (fd==-1){
        LOG_ERROR("loglib","openfile error:%s", strerror(errno));
//        LOG_ERROR("loglib","openfile error:%s", strerror(errno));
        goto cleanup;
    }

    if (ftruncate(fd,buffer_size)==-1){
        LOG_ERROR("loglib","ftruncate error:%s", strerror(errno));
//        LOG_ERROR("loglib","ftruncate error:%s", strerror(errno));
        goto cleanup;
    }

    gMappedRegion = (char*)mmap(nullptr,buffer_size,PROT_WRITE,MAP_SHARED,fd,0);
    if (gMappedRegion==MAP_FAILED){
        LOG_ERROR("loglib","gMappedRegion error:%s", strerror(errno));
//        LOG_ERROR("loglib","gMappedRegion error:%s", strerror(errno));
        goto cleanup;
    }
    gBufferSize = buffer_size;
    gCurrentOffset = 0;

    cleanup:
    env->ReleaseStringUTFChars(filepath,path);
    if (fd != -1) close(fd);
    return reinterpret_cast<jlong>(gMappedRegion);

}
extern "C"
JNIEXPORT void JNICALL
Java_com_source_log_Logger_writeLog(JNIEnv *env, jclass clazz, jlong mappedRegionPtr, jstring logMessage) {
    if (mappedRegionPtr == 0) return;

    std::lock_guard<std::mutex> lock(gMutex);
    char* region = reinterpret_cast<char*>(mappedRegionPtr);
    const char* message = env->GetStringUTFChars(logMessage, nullptr);
    if (!message) return;

    size_t messageLength = strlen(message);
    if (gCurrentOffset + messageLength >= gBufferSize) {
        msync(region, gCurrentOffset, MS_SYNC);
        gCurrentOffset = 0;
    }
    strncpy(region + gCurrentOffset, message, messageLength);
    gCurrentOffset += messageLength;

    env->ReleaseStringUTFChars(logMessage, message);

}
