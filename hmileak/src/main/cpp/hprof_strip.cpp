//
// Created by ts on 2024/8/8.
//
#define KWAI_EXPORT __attribute__((visibility("default")))
#define ALWAYS_INLINE __attribute__((always_inline))
#include "./include/hprof_strip.h"
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <list>
#include <jni.h>

enum HprofTag{
    HPROF_TAG_STRING = 0x01,
    HPROF_TAG_LOAD_CLASS = 0x02,
    HPROF_TAG_UNLOAD_CLASS = 0x03,
    HPROF_TAG_STACK_FRAME = 0x04,
    HPROF_TAG_STACK_TRACE = 0x05,
    HPROF_TAG_ALLOC_SITES = 0x06,
    HPROF_TAG_HEAP_SUMMARY = 0x07,
    HPROF_TAG_START_THREAD = 0x0A,
    HPROF_TAG_END_THREAD = 0x0B,
    HPROF_TAG_HEAP_DUMP = 0x0C,
    HPROF_TAG_HEAP_DUMP_SEGMENT = 0x1C,
    HPROF_TAG_HEAP_DUMP_END = 0x2C,
    HPROF_TAG_CPU_SAMPLES = 0x0D,
    HPROF_TAG_CONTROL_SETTINGS = 0x0E,
};

enum HprofHeapTag{
    HPROF_ROOT_UNKNOWN = 0xFF,
    HPROF_ROOT_JNI_GLOBAL = 0x01,
    HPROF_ROOT_JNI_LOCAL = 0x02,
    HPROF_ROOT_JAVA_FRAME = 0x03,
    HPROF_ROOT_NATIVE_STACK = 0x04,
    HPROF_ROOT_STICKY_CLASS = 0x05,
    HPROF_ROOT_THREAD_BLOCK = 0x06,
    HPROF_ROOT_MONITOR_USED = 0x07,
    HPROF_ROOT_THREAD_OBJECT = 0x08,
    HPROF_CLASS_DUMP = 0x20,
    HPROF_INSTANCE_DUMP = 0x21,
    HPROF_OBJECT_ARRAY_DUMP = 0x22,
    HPROF_PRIMITIVE_ARRAY_DUMP = 0x23,

    // Android.
    HPROF_HEAP_DUMP_INFO = 0xfe,
    HPROF_ROOT_INTERNED_STRING = 0x89,
    HPROF_ROOT_FINALIZING = 0x8a,  // Obsolete.
    HPROF_ROOT_DEBUGGER = 0x8b,
    HPROF_ROOT_REFERENCE_CLEANUP = 0x8c,  // Obsolete.
    HPROF_ROOT_VM_INTERNAL = 0x8d,
    HPROF_ROOT_JNI_MONITOR = 0x8e,
    HPROF_UNREACHABLE = 0x90,                  // Obsolete.
    HPROF_PRIMITIVE_ARRAY_NODATA_DUMP = 0xc3,  // Obsolete.
};

enum HprofBasicType{
    hprof_basic_object = 2,
    hprof_basic_boolean = 4,
    hprof_basic_char = 5,
    hprof_basic_float = 6,
    hprof_basic_double = 7,
    hprof_basic_byte = 8,
    hprof_basic_short = 9,
    hprof_basic_int = 10,
    hprof_basic_long = 11,
};

enum HprofHeapId{
    HPROF_HEAP_DEFAULT = 0,
    HPROF_HEAP_ZYGOTE = 'Z',
    HPROF_HEAP_APP = 'A',
    HPROF_HEAP_IMAGE = 'I',
};

enum HprofTagBytes{
    OBJECT_ID_BYTE_SIZE = 4,
    JNI_GLOBAL_REF_ID_BYTE_SIZE = 4,
    CLASS_ID_BYTE_SIZE = 4,
    CLASS_LOADER_ID_BYTE_SIZE = 4,
    INSTANCE_SIZE_BYTE_SIZE = 4,
    CONSTANT_POOL_LENGTH_BYTE_SIZE = 2,
    STATIC_FIELD_LENGTH_BYTE_SIZE = 2,
    INSTANCE_FIELD_LENGTH_BYTE_SIZE = 2,
    STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE = 4,
    RECORD_TIME_BYTE_SIZE = 4,
    RECORD_LENGTH_BYTE_SIZE = 4,
    STRING_ID_BYTE_SIZE = 4,

    HEAP_TAG_BYTE_SIZE = 1,
    THREAD_SERIAL_BYTE_SIZE = 4,
    CONSTANT_POLL_INDEX_BYTE_SIZE = 2,
    BASIC_TYPE_BYTE_SIZE = 1,
    HEAP_TYPE_BYTE_SIZE = 4,
};

#define VERBOSE_LOG  false
static constexpr int U4 = 4;

ALWAYS_INLINE int HprofStrip::GetShortFromBytes(const unsigned char *buf, int index) {
    return(buf[index] << 8u) + buf[index+1];
}

ALWAYS_INLINE int HprofStrip::GetIntFromBytes(const unsigned char *buf, int index) {
    return(buf[index] << 24u) +(buf[index+1]<<16u) +
            (buf[index+2]<<8u) + buf[index + 3];
}

int HprofStrip::GetByteSizeFromType(unsigned char basic_type) {
    switch (basic_type) {
        case hprof_basic_boolean:
        case hprof_basic_byte:
            return 1;
        case hprof_basic_char:
        case hprof_basic_short:
            return 2;
        case hprof_basic_float:
        case hprof_basic_int:
        case hprof_basic_object:
            return 4;
        case hprof_basic_long:
        case hprof_basic_double:
            return 8;
        default:
            return 0;
    }
}

int HprofStrip::ProcessHeap(const void *buf, int first_index, int max_len, int heap_serial_no,
                            int array_serial_no) {
    if (first_index>=max_len) {
        return array_serial_no;
    }
    const unsigned char subtag = ((unsigned char *)buf)[first_index];
    switch (subtag) {
        case HPROF_ROOT_UNKNOWN:
        case HPROF_ROOT_STICKY_CLASS:
        case HPROF_ROOT_MONITOR_USED:
        case HPROF_ROOT_INTERNED_STRING:
        case HPROF_ROOT_DEBUGGER:
        case HPROF_ROOT_VM_INTERNAL:
            array_serial_no = ProcessHeap(buf,first_index+HEAP_TAG_BYTE_SIZE+OBJECT_ID_BYTE_SIZE,max_len,
                                          heap_serial_no,array_serial_no);
            break;
        case HPROF_ROOT_JNI_GLOBAL:
            array_serial_no = ProcessHeap(buf,first_index+HEAP_TAG_BYTE_SIZE+OBJECT_ID_BYTE_SIZE+JNI_GLOBAL_REF_ID_BYTE_SIZE,max_len,
                                          heap_serial_no,array_serial_no);
            break;
        case HPROF_ROOT_JNI_LOCAL:
        case HPROF_ROOT_JAVA_FRAME:
        case HPROF_ROOT_JNI_MONITOR:
            array_serial_no = ProcessHeap(buf,first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE + THREAD_SERIAL_BYTE_SIZE + U4,
                                          max_len,heap_serial_no,array_serial_no);
            break;
        case HPROF_ROOT_NATIVE_STACK:
        case HPROF_ROOT_THREAD_BLOCK:
            array_serial_no = ProcessHeap(buf,first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE + THREAD_SERIAL_BYTE_SIZE ,
                                          max_len,heap_serial_no,array_serial_no);
            break;
        case HPROF_ROOT_THREAD_OBJECT:
            array_serial_no = ProcessHeap(buf,first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE + THREAD_SERIAL_BYTE_SIZE + U4,
                                          max_len,heap_serial_no,array_serial_no);
            break;
        case HPROF_CLASS_DUMP: {

            int constant_pool_index = first_index + HEAP_TAG_BYTE_SIZE + CLASS_ID_BYTE_SIZE + STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE
                    + CLASS_ID_BYTE_SIZE + CLASS_LOADER_ID_BYTE_SIZE + OBJECT_ID_BYTE_SIZE + OBJECT_ID_BYTE_SIZE
                    + OBJECT_ID_BYTE_SIZE + OBJECT_ID_BYTE_SIZE + INSTANCE_SIZE_BYTE_SIZE;
            int constants_pool_size = GetShortFromBytes((unsigned char *)buf,constant_pool_index);
            constant_pool_index += CONSTANT_POOL_LENGTH_BYTE_SIZE;
            for (int i = 0; i < constants_pool_size; ++i) {
                unsigned char type =((unsigned char *)buf)[constant_pool_index +
                        CONSTANT_POLL_INDEX_BYTE_SIZE];
                constant_pool_index += CONSTANT_POLL_INDEX_BYTE_SIZE + BASIC_TYPE_BYTE_SIZE
                        + GetByteSizeFromType(type);
            }
            int static_fields_index = constant_pool_index;
            int static_fields_size = GetShortFromBytes((unsigned char *)buf,static_fields_index);
            static_fields_index += STATIC_FIELD_LENGTH_BYTE_SIZE;
            for (int i = 0; i < static_fields_size; ++i) {
                unsigned char type =((unsigned char*)buf)[static_fields_index+STRING_ID_BYTE_SIZE];
                static_fields_index += STRING_ID_BYTE_SIZE + BASIC_TYPE_BYTE_SIZE +
                        GetByteSizeFromType(type);
            }
            int instance_field_index = static_fields_index;
            int instance_field_size = GetShortFromBytes((unsigned char *)buf,instance_field_index);
            instance_field_index += INSTANCE_FIELD_LENGTH_BYTE_SIZE;
            instance_field_index += (BASIC_TYPE_BYTE_SIZE + STRING_ID_BYTE_SIZE) * instance_field_size;
            array_serial_no = ProcessHeap(buf,instance_field_index,max_len,heap_serial_no,array_serial_no);

        }
            break;
        case HPROF_INSTANCE_DUMP: {
            int instance_dump_index = first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE
                    + STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE + CLASS_ID_BYTE_SIZE;
            int instance_size = GetIntFromBytes((unsigned char *)buf,instance_dump_index);
            if (is_current_system_heap_){
                strip_index_list_pair_[strip_index_*2] = first_index;
                strip_index_list_pair_[strip_index_*2+1] = instance_dump_index+U4+instance_size;
                strip_index_++;
                strip_bytes_sum_+=instance_dump_index+U4+instance_size-first_index;
            }
            array_serial_no = ProcessHeap(buf,instance_dump_index+U4+instance_size,max_len,
                                          heap_serial_no,array_serial_no);
        }

            break;
        case HPROF_OBJECT_ARRAY_DUMP:{
            int lenght = GetIntFromBytes((unsigned char*)buf,first_index+HEAP_TAG_BYTE_SIZE+OBJECT_ID_BYTE_SIZE
            +STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE);
            if (is_current_system_heap_) {
                strip_index_list_pair_[strip_index_*2] = first_index;
                strip_index_list_pair_[strip_index_*2+1] = first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE
                        +STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE + U4
                        + CLASS_ID_BYTE_SIZE + U4*lenght;
                strip_index_++;
                strip_bytes_sum_ += HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE +
                    STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE + U4 + CLASS_ID_BYTE_SIZE + U4*lenght;
            }
            array_serial_no = ProcessHeap(buf,first_index+HEAP_TAG_BYTE_SIZE+OBJECT_ID_BYTE_SIZE+STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE
            +U4*lenght,max_len,heap_serial_no,array_serial_no);
        }
            break;
        case   HPROF_PRIMITIVE_ARRAY_DUMP:{
            int primitive_array_dump_index =   first_index + HEAP_TAG_BYTE_SIZE + OBJECT_ID_BYTE_SIZE
                    + STACK_TRACE_SERIAL_NUMBER_BYTE_SIZE;
            int lenght = GetIntFromBytes((unsigned char *)buf,primitive_array_dump_index);
            primitive_array_dump_index +=U4;
            if (is_current_system_heap_){
                strip_index_list_pair_[strip_index_*2]= first_index;
            } else {
                strip_index_list_pair_[strip_index_*2] =
                        primitive_array_dump_index + BASIC_TYPE_BYTE_SIZE;
            }
            array_serial_no++;
            int value_size = GetByteSizeFromType(((unsigned char *)buf)[primitive_array_dump_index]);
            primitive_array_dump_index += BASIC_TYPE_BYTE_SIZE +value_size*lenght;
            strip_index_list_pair_[strip_index_*2+1] = primitive_array_dump_index;
            if (is_current_system_heap_) {
                strip_bytes_sum_+=primitive_array_dump_index - first_index;
            }
            strip_index_++;
            array_serial_no = ProcessHeap(buf,primitive_array_dump_index,max_len,heap_serial_no,array_serial_no);
        }
            break;
        case HPROF_HEAP_DUMP_INFO:{
            const unsigned char heap_type =((unsigned char *)buf)[first_index + HEAP_TAG_BYTE_SIZE + 3];
            is_current_system_heap_ =(heap_type==HPROF_HEAP_ZYGOTE || heap_type == HPROF_HEAP_IMAGE);
            if (is_current_system_heap_) {
                strip_index_list_pair_[strip_index_*2] = first_index;
                strip_index_list_pair_[strip_index_*2+1] = first_index
                        +HEAP_TAG_BYTE_SIZE+HEAP_TYPE_BYTE_SIZE+STRING_ID_BYTE_SIZE;
                strip_index_++;
                strip_bytes_sum_ += HEAP_TAG_BYTE_SIZE +
                                    HEAP_TYPE_BYTE_SIZE
                                    +STRING_ID_BYTE_SIZE;
            }
            array_serial_no = ProcessHeap(buf,first_index+HEAP_TAG_BYTE_SIZE+HEAP_TYPE_BYTE_SIZE+STRING_ID_BYTE_SIZE,
                                          max_len,heap_serial_no,array_serial_no);
        }
            break;
        case HPROF_ROOT_FINALIZING:
        case HPROF_ROOT_REFERENCE_CLEANUP:
        case HPROF_UNREACHABLE:
        case HPROF_PRIMITIVE_ARRAY_NODATA_DUMP:{
            array_serial_no = ProcessHeap(buf,first_index+HEAP_TAG_BYTE_SIZE,max_len,heap_serial_no,array_serial_no);
        }
            break;
        default:
            break;
    }
    return array_serial_no;
}

static int HookOpen(const char *pathname,int flags,...) {
    va_list ap;
    va_start(ap,flags);
    int fd = HprofStrip::getInstance().HookOpenInternal(pathname,flags,ap);
    va_end(ap);
    return fd;
}

int HprofStrip::HookOpenInternal(const char *path_name, int flags, ...) {
    va_list ap;
    va_start(ap,flags);
    int fd = open(path_name,flags,ap);
    va_end(ap);
    if (hprof_name_.empty())
        return fd;
    if (path_name != nullptr && strstr(path_name,hprof_name_.c_str())) {
        hprof_fd_ = fd;
        is_hook_success_ = true;
    }
    return fd;
}

static ssize_t HookWrite(int fd,const void *buf,size_t count) {
    return HprofStrip::getInstance().HookWriteInternal(fd,buf,count);
}

void HprofStrip::reset() {
    strip_index_ = 0;
    strip_bytes_sum_ = 0;
}

size_t HprofStrip::FullyWrite(int fd, const void *buf, ssize_t count) {
    size_t left = count;
    while (left>0) {
        ssize_t written = write(fd,(unsigned char*)buf +(count - left),left);
        if (written!=-1) left -=written;
    }
    return count;
}

ssize_t HprofStrip::HookWriteInternal(int fd, const void *buf, ssize_t count) {
    if (fd!=hprof_fd_) {
        return write(fd,buf,count);
    }
    reset();
    const unsigned char tag =((unsigned char*)buf)[0];
    switch (tag) {
        case HPROF_TAG_HEAP_DUMP:
        case HPROF_TAG_HEAP_DUMP_SEGMENT:
            ProcessHeap(buf,
                        HEAP_TAG_BYTE_SIZE+RECORD_TIME_BYTE_SIZE + RECORD_LENGTH_BYTE_SIZE,
                        count,heap_serial_num_,0);
            break;
        default:
            break;
    }
    int record_length;
    if (tag == HPROF_TAG_HEAP_DUMP || tag==HPROF_TAG_HEAP_DUMP_SEGMENT) {
        record_length = GetIntFromBytes((unsigned char*)buf,HEAP_TAG_BYTE_SIZE+RECORD_TIME_BYTE_SIZE);
        record_length -= strip_bytes_sum_;
        int index = HEAP_TAG_BYTE_SIZE + RECORD_TIME_BYTE_SIZE;
        ((unsigned char *)buf)[index] =
                (unsigned char)(((unsigned int)record_length & 0xff000000u) >> 24u);
        ((unsigned char *)buf)[index + 1] =
                (unsigned char)(((unsigned int)record_length & 0x00ff0000u) >> 16u);
        ((unsigned char *)buf)[index + 2] =
                (unsigned char)(((unsigned int)record_length & 0x0000ff00u) >> 8u);
        ((unsigned char *)buf)[index + 3] =
                (unsigned char)((unsigned int)record_length & 0x000000ffu);
    }
    size_t total_write = 0;
    int start_index = 0;
    for (int i = 0; i < strip_index_; ++i) {
        void *write_buf =(void*)((unsigned char *)buf+start_index);
        auto write_len =(size_t)(strip_index_list_pair_[i*2] - start_index);
        if (write_len > 0) {
            total_write += FullyWrite(fd,write_buf,write_len);
        } else if(write_len < 0){
            __android_log_print(ANDROID_LOG_ERROR, "Hprof_strip",
                                "HookWrite array i:%d writeLen<0:%zu", i, write_len);
        }
        start_index = strip_index_list_pair_[i*2+1];
    }
    auto writen_len =(size_t)(count-start_index);
    if (writen_len>0) {
      void *write_buf = (void*)((unsigned char*)buf+start_index);
      total_write += FullyWrite(fd,write_buf,count - start_index);
    }
    hook_write_serial_num_++;
    if (VERBOSE_LOG && total_write != count) {
        __android_log_print(ANDROID_LOG_INFO, "Hprof_strip",
                            "hook write, hprof strip happens");
    }
    return count;
}

void HprofStrip::HookInit() {
    __android_log_print(ANDROID_LOG_INFO, "Hprof_strip",
                        "HookInit========");
}

HprofStrip &HprofStrip::getInstance() {
    static HprofStrip hropf_strip;
    return hropf_strip;
}

HprofStrip::HprofStrip() :
hprof_fd_(-1),
strip_bytes_sum_(0),
heap_serial_num_(0),
hook_write_serial_num_(0),
strip_index_(0),
is_hook_success_(false),
is_current_system_heap_(false){
    std::fill(strip_index_list_pair_,
              strip_index_list_pair_+ arraysize(strip_index_list_pair_),0);
}

void HprofStrip::setHprofName(const char *hprof_name) {
    hprof_name_ = hprof_name;
}


