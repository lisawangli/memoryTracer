//
// Created by ts on 2024/6/27.
//

#ifndef MEMORYTRACER_ELF_WRAPPER_H
#define MEMORYTRACER_ELF_WRAPPER_H
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <link.h>
#include <android/log.h>
#include <errno.h>
#include <cstring>
#include <stdlib.h>
#include <string>

#define LOG_DEBUG(tag,fmt,...) __android_log_print(ANDROID_LOG_DEBUG,tag,fmt,##__VA_ARGS__)

    class ElfWrapper {
    public:
        ElfWrapper() : start_(nullptr), size_(0) {}
        virtual ~ElfWrapper() {}

        virtual bool IsValid() { return false; }
        ElfW(Ehdr) * Start() { return reinterpret_cast<ElfW(Ehdr) *>(start_); }
        size_t Size() { return size_; }

    protected:
        void *start_;
        size_t size_;
    };

/**
 * Read ELF from so file.
 */
    class FileElfWrapper : public ElfWrapper {
    public:
        explicit FileElfWrapper(const char *name) : fd_(-1) {
            if (!name) {
                return;
            }
            fd_ = open(name, O_RDONLY);
            if (fd_ < 0) {
                return;
            }

            size_ = lseek(fd_, 0, SEEK_END);
            if (size_ <= 0) {
                return;
            }

            start_ = reinterpret_cast<ElfW(Ehdr) *>(
                    mmap(0, size_, PROT_READ, MAP_SHARED, fd_, 0));
            if (start_ == MAP_FAILED) {
                return;
            }
        }

        ~FileElfWrapper() {
            if (start_ != MAP_FAILED && size_ > 0) {
                munmap(reinterpret_cast<void *>(start_), size_);
            }
            if (fd_ >= 0) {
                close(fd_);
            }
        }

        bool IsValid() { return fd_ >= 0 && start_ != MAP_FAILED && size_ > 0; }

    private:
        int fd_;
    };

/**
 * Read ELF from memory data.
 */
    class MemoryElfWrapper : public ElfWrapper {
    public:
        explicit MemoryElfWrapper(std::string &elf_data) {
            if (elf_data.empty()) {
                return;
            }
            elf_data_ = std::move(elf_data);
            start_ = (void *)elf_data_.data();
            size_ = elf_data_.size();
        }

        bool IsValid() { return start_ && size_ > 0; }

    private:
        std::string elf_data_;
    };
 // namespace linker

#endif //MEMORYTRACER_ELF_WRAPPER_H
