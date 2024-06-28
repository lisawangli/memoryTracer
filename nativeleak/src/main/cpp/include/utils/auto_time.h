

#ifndef KOOM_NATIVE_OOM_SRC_MAIN_JNI_INCLUDE_UTILS_AUTO_TIME_H_
#define KOOM_NATIVE_OOM_SRC_MAIN_JNI_INCLUDE_UTILS_AUTO_TIME_H_
#include <log/log.h>

#include <ctime>

class AutoTime {
 public:
  AutoTime(const char *tag = nullptr) : tag_(tag), start_(clock()) {}
  ~AutoTime() {
    clock_t end = clock();
    ALOGI("%s consume time: %f s", tag_ ? tag_ : "",
          (static_cast<double>(end - start_) / CLOCKS_PER_SEC));
  }

 private:
  const char *tag_;
  clock_t start_;
};
#endif  // KOOM_NATIVE_OOM_SRC_MAIN_JNI_INCLUDE_UTILS_AUTO_TIME_H_
