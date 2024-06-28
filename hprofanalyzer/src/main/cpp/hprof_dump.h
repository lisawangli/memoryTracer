//
// Created by ts on 2024/6/27.
//

#ifndef MEMORYTRACER_HPROF_DUMP_H
#define MEMORYTRACER_HPROF_DUMP_H

#include <memory>
#include <string>
#include "include/macros.h"

namespace leak_monitor{

    enum GcCause{
// Invalid GC cause used as a placeholder.
     KGcCauseNode,
// GC triggered by a failed allocation. Thread doing allocation is blocked
        // waiting for GC before
        // retrying allocation.
     KGcCauseForAlloc,
     // A background GC trying to ensure there is free memory ahead of allocations.
     KGcCauseBackground,
        // GC triggered for a native allocation when NativeAllocationGcWatermark is
        // exceeded.
        // (This may be a blocking GC depending on whether we run a non-concurrent
        // collector).
      KGcCauseForNativeAlloc,
// GC triggered for a collector transition.
      KGCcCauseCollectorTransition,
// Not a real GC cause, used when we disable moving GC (currently for
        // GetPrimitiveArrayCritical).
      KGcCauseDisableMovingGc,
// Not a real GC cause, used when we trim the heap.
      KGCTCauseTrim,
// Not a real GC cause, used to implement exclusion between GC and
        // instrumentation.
      KGcCauseInstrmentation,
// Not a real GC cause, used to add or remove app image spaces.
      KGcCauseAndRemoveAppImageSpace,
// Not a real GC cause, used to implement exclusion between GC and debugger.
     KGcCauseDebugger,
// GC triggered for background transition when both foreground and background
        // collector are CMS.
     KGcCauseHomogeneousSpaceCompact,
        // Class linker cause, used to guard filling art methods with special values.
        kGcCauseClassLinker,
        // Not a real GC cause, used to implement exclusion between code cache
        // metadata and GC.
        KGcCauseJitCodeCache,
        // Not a real GC cause, used to add or remove system-weak holders.
        KGcCauseAddRemoveSystemWeakHolder,
        // Not a real GC cause, used to prevent hprof running in the middle of GC.
        KGcCauseHprof,
        // Not a real GC cause, used to prevent GetObjectsAllocated running in the
        // middle of GC.
        KGcCauseGetObjectsAllocated,
        // GC cause for the profile saver.
        KGcCauseProfileSaver,
        // GC cause for running an empty checkpoint.
        KGcCauseRunEmptyCheckpoint,

    };
// Which types of collections are able to be performed.
    enum CollectorType{
        //no collectot selected
        kCollectorTypeNone,
        // Non concurrent mark-sweep.
        KCollectorTypeMS,
        // Concurrent mark-sweep.
        KCollectorTypeCMS,
// Semi-space / mark-sweep hybrid, enables compaction.
        kCollectorTypeSS,
        // Heap trimming collector, doesn't do any actual collecting.
        kCollectorTypeHeapTrim,
        // A (mostly) concurrent copying collector.
        kCollectorTypeCC,
        // The background compaction of the concurrent copying collector.
        kCollectorTypeCCBackground,
        // Instrumentation critical section fake collector.
        kCollectorTypeInstrumentation,
        // Fake collector for adding or removing application image spaces.
        kCollectorTypeAddRemoveAppImageSpace,
        // Fake collector used to implement exclusion between GC and debugger.
        kCollectorTypeDebugger,
// A homogeneous space compaction collector used in background transition
        // when both foreground and background collector are CMS.
        kCollectorTypeHomogeneousSpaceCompact,
// Class linker fake collector.
        kCollectorTypeClassLinker,
// JIT Code cache fake collector.
        kCollectorTypeJitCodeCache,
        // Hprof fake collector.
       kCollectorTypeHprof,
        // Fake collector for installing/removing a system-weak holder.
        kCollectorTypeAndRemoveSystemWeakHolder,
        // Fake collector type for GetObjectsAllocated
        kCollectorTypeGetObjectsAllocated,
        // Fake collector type for ScopedGCCriticalSection
        kCollectorTypeCriticalSection,
    };

    class HprofDump{
    public:
        static HprofDump &getInstance();

        void Initialize();

        pid_t SuspendAndFork();
        bool ResumeAndWait(pid_t pid);
    private:
        HprofDump();
        ~HprofDump() = default;
        bool init_done_;
        int android_api_;
        std::unique_ptr<char[]> ssa_instance_;
        std::unique_ptr<char[]> sgc_instance_;

        void (*suspend_vm_func_)();
        void (*resume_vm_func_)();

        void (*ssa_constructor_func_)(void *handle,const char *cause,bool long_suspend);
        void (*ssa_destructor_func_)(void *handle);
        void (*sgc_constructor_func_)(void *handle,void *self,GcCause cause,CollectorType collectorType);
        void (*sgc_destructor_func_)(void *handle);
        void **mutator_lock_otr;
        void (*exclusive_lock_fnc_)(void*,void *self);
        void (*exclusive_unlock_func_)(void *,void *self);
    };
}

#endif //MEMORYTRACER_HPROF_DUMP_H
