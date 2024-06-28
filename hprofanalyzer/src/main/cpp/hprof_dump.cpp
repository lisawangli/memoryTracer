//
// Created by ts on 2024/6/27.
//
#include "hprof_dump.h"
#include <wait.h>
#include "include/tls.h"
#include <sys/prctl.h>
#include "./linker/mydlfcn.h"
#include <dlfcn.h>
#define LOG_TAG "HprofDump"

namespace leak_monitor{

    HprofDump &HprofDump::getInstance() {
        static HprofDump hprof_dump;
        return hprof_dump;
    }

    HprofDump::HprofDump() : init_done_(false),android_api_(0){
        android_api_ = android_get_device_api_level();
    }

    void HprofDump::Initialize() {
        if (init_done_||android_api_<__ANDROID_API_L__) {
            return;
        }

        void *handle = linker::DlFcn::dlopen("libart.so", RTLD_NOW);
        if (android_api_<__ANDROID_API_R__) {
            suspend_vm_func_ =
                    (void (*)())linker::DlFcn::dlsym(handle,"_ZN3art3Dbg9SuspendVMEv");
            resume_vm_func_ = (void (*)())linker::DlFcn::dlsym(
                    handle,"_ZN3art3Dbg8ResumeVMEv"
                    );
        } else if(android_api_<=__ANDROID_API_S__) {
            ssa_instance_ = std::make_unique<char[]>(64);
            sgc_instance_ = std::make_unique<char[]>(64);
            ssa_constructor_func_ =  (void (*)(void *, const char *, bool))
                    linker::DlFcn::dlsym(handle,"_ZN3art16ScopedSuspendAllC1EPKcb");
            ssa_destructor_func_ =(void (*)(void *))linker::DlFcn::dlsym(
                    handle,"_ZN3art16ScopedSuspendAllD1Ev");
            sgc_constructor_func_ =
                    (void (*)(void *, void *, GcCause, CollectorType))linker::DlFcn::dlsym(
                            handle,
                            "_ZN3art2gc23ScopedGCCriticalSectionC1EPNS_6ThreadENS0_"
                            "7GcCauseENS0_13CollectorTypeE");
            sgc_destructor_func_ = (void (*)(void *))linker::DlFcn::dlsym(
                    handle,"_ZN3art2gc23ScopedGCCriticalSectionD1Ev");
            mutator_lock_otr = (void **) linker::DlFcn::dlsym(handle,"_ZN3art5Locks13mutator_lock_E");
            exclusive_lock_fnc_ = (void (*) (void *,void *))linker::DlFcn::dlsym(
                    handle,"_ZN3art17ReaderWriterMutex13ExclusiveLockEPNS_6ThreadE");
            exclusive_unlock_func_ = (void (*)(void *, void *))linker::DlFcn::dlsym(
                    handle, "_ZN3art17ReaderWriterMutex15ExclusiveUnlockEPNS_6ThreadE");
        }
        linker::DlFcn::dlclose(handle);
        init_done_ = true;
    }

    pid_t HprofDump::SuspendAndFork() {
        if (android_api_ < __ANDROID_API_R__) {
            suspend_vm_func_();
        } else if (android_api_<__ANDROID_API_S__) {
            void *self = __get_tls()[TLS_SLOT_ART_THREAD_SELF];
            sgc_constructor_func_ ((void *)sgc_instance_.get(), self, GcCause::KGcCauseHprof,
                                 kCollectorTypeHprof);
            ssa_constructor_func_((void*)ssa_instance_.get(),LOG_TAG, true);
            exclusive_unlock_func_(*mutator_lock_otr,self);
            sgc_destructor_func_((void *)sgc_instance_.get());
        }
        pid_t pid = fork();
        if (pid==0){
            alarm(60);
            prctl(PR_SET_NAME,"forked-dump-process");
        }
        return pid;
    }

    bool HprofDump::ResumeAndWait(pid_t pid) {
        if (android_api_ < __ANDROID_API_S__) {
            resume_vm_func_();
        } else if (android_api_ <= __ANDROID_API_S__) {
            void *self = __get_tls()[TLS_SLOT_ART_THREAD_SELF];
            exclusive_lock_fnc_(*mutator_lock_otr,self);
            ssa_destructor_func_((void *)sgc_instance_.get());

        }
        int status;
        for(;;){
            if (waitpid(pid,&status,0)!=-1){
                if (!WIFEXITED(status)){
                    return false;
                }
                return true;
            }
            if (errno == EINTR)
                continue;
        }
        return false;
    }
}

