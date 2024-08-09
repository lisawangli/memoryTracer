//
// Created by ts on 2024/8/8.
//

#ifndef MEMORYTRACER_HPROF_STRIP_H
#define MEMORYTRACER_HPROF_STRIP_H
#include <memory>
#include <string>
#include "../../../../../hprofanalyzer/src/main/cpp/include/macros.h"

class HprofStrip{

public:
    static HprofStrip &getInstance();

    static void HookInit();

    int HookOpenInternal(const char *path_name,int flags,...);

    ssize_t HookWriteInternal(int fd,const void *buf,ssize_t count);

    bool isHookSuccess() const;

    void setHprofName(const char *hprof_name);

private:
    HprofStrip();

    ~HprofStrip() = default;

    DISALLOW_COPY_AND_ASSIGN(HprofStrip);

    static int GetShortFromBytes(const unsigned char *buf,int index);

    static int GetIntFromBytes(const unsigned char *buf,int index);
    static int GetByteSizeFromType(unsigned char basic_type);

    int ProcessHeap(const void *buf,int first_index,int max_len,int heap_serial_no,int array_serial_no);

    static size_t FullyWrite(int fd,const void *buf,ssize_t count);

    void reset();

    int hprof_fd_;
    int strip_bytes_sum_;
    int heap_serial_num_;
    int hook_write_serial_num_;
    int strip_index_;
    bool is_hook_success_;
    bool is_current_system_heap_;
    std::string hprof_name_;
    static constexpr int kStripListLength = 65536 * 2 * 2 + 2;
    int strip_index_list_pair_[kStripListLength];


};
#endif //MEMORYTRACER_HPROF_STRIP_H
