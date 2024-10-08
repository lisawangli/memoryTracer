//
// Created by ts on 2024/6/27.
//

#include "./../include/linker/mydlfcn.h"
#include <fcntl.h>
#include <link.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>
#include <macros.h>
#include "./../include/linker/elf_reader.h"
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>
#include "map_util.hpp"

    int dl_iterate_phdr_wrapper(int(*__callback)
            ( struct dl_phdr_info *,size_t,void *),
                    void *__data
            ){
        if (dl_iterate_phdr){
            return dl_iterate_phdr(__callback,__data);
        }
        return 0;
    }

    int DlFcn::android_api_;
    void DlFcn::init_api() {
        android_api_ = android_get_device_api_level();
    }
    static pthread_once_t  once_control = PTHREAD_ONCE_INIT;
    static int dl_iterate_callback(dl_phdr_info *info,size_t size,void *data){
        auto target = reinterpret_cast<DlFcn::dl_iterate_data*>(data);
        if (info->dlpi_addr!=0&&strstr(info->dlpi_name,target->info_.dlpi_name)){
            target->info_.dlpi_name = info->dlpi_name;
            target->info_.dlpi_addr = info->dlpi_addr;
            target->info_.dlpi_phdr = info->dlpi_phdr;
            target->info_.dlpi_phnum = info->dlpi_phnum;
            return 1;
        }
        return 0;
    }
    using __loader_dlopen_fn = void *(*)(const char *filename,int flag,void *address);

    void *DlFcn::dlopen(const char *lib_name, int flags) {
        pthread_once(&once_control, init_api);
        if (android_api_ < __ANDROID_API_N__) {
            return ::dlopen(lib_name, flags);
        }
        if (android_api_ >= __ANDROID_API_O__) {
            void *handle = ::dlopen("libdl.so", RTLD_NOW);
            auto __loader_dlopen = reinterpret_cast<__loader_dlopen_fn>(
                    ::dlsym(handle, "__loader_dlopen"));
            if (android_api_ < __ANDROID_API_Q__) {
                return __loader_dlopen(lib_name, flags, (void *)dlerror);
            } else {
                handle = __loader_dlopen(lib_name, flags, (void *)dlerror);
                if (handle == nullptr) {
                    // Android Q added "runtime" namespace
                    dl_iterate_data data{};
                    data.info_.dlpi_name = lib_name;
                    dl_iterate_phdr_wrapper(dl_iterate_callback, &data);
                    handle = __loader_dlopen(lib_name, flags, (void *)data.info_.dlpi_addr);
                }
                return handle;
            }
        }
        // __ANDROID_API_N__ && __ANDROID_API_N_MR1__
        auto *data = new dl_iterate_data();
        data->info_.dlpi_name = lib_name;
        dl_iterate_phdr_wrapper(dl_iterate_callback, data);

        return data;
    }

    void *DlFcn::dlsym(void *handle, const char *name) {
        auto is_android_N = []() -> bool {
            return android_api_ == __ANDROID_API_N__ ||
                   android_api_ == __ANDROID_API_N_MR1__;
        };

        if (!is_android_N()) {
            return ::dlsym(handle, name);
        }

        // __ANDROID_API_N__ && __ANDROID_API_N_MR1__
        auto *data = (dl_iterate_data *)handle;
        if (!data->info_.dlpi_name || data->info_.dlpi_name[0] != '/') {
            return nullptr;
        }

        ElfReader elf_reader(std::make_shared<FileElfWrapper>(data->info_.dlpi_name));
        if (!elf_reader.Init()) {
            return nullptr;
        }

        return elf_reader.LookupSymbol(name, data->info_.dlpi_addr, is_android_N());
    }

    int DlFcn::dlclose(void *handle) {
        if (android_api_ != __ANDROID_API_N__ &&
            android_api_ != __ANDROID_API_N_MR1__) {
            return ::dlclose(handle);
        }
        // __ANDROID_API_N__ && __ANDROID_API_N_MR1__
        delete (dl_iterate_data *)handle;
        return 0;
    }

    void *DlFcn::dlopen_elf(const char *lib_name, int flags) {
        pthread_once(&once_control,init_api);
        ElfW(Addr) load_base;
        std::string  so_full_name;
        bool  ret = MapUtil::GetLoadInfo(lib_name,&load_base,so_full_name,android_api_);
        if (!ret||so_full_name.empty()||so_full_name[0]!='/')
            return nullptr;
        SoDlInfo *so_dl_info = new (std::nothrow) SoDlInfo;
        if (!so_dl_info){
            return nullptr;
        }
        so_dl_info->load_base = load_base;
        so_dl_info->full_name = so_full_name;
        return so_dl_info;
    }

    void *DlFcn::dlsym_elf(void *handle, const char *name) {
        auto *so_dl_info= reinterpret_cast<SoDlInfo *>(handle);
        ElfReader elfReader(
                std::make_shared<FileElfWrapper>(so_dl_info->full_name.c_str())
                );
        if (!elfReader.Init()){
            return nullptr;
        }
        return elfReader.LookupSymbol(name,so_dl_info->load_base);
    }

    int DlFcn::dlclose_elf(void *handle) {
        delete reinterpret_cast<SoDlInfo *>(handle);
        return 0;
    }
