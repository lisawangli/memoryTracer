cmake_minimum_required(VERSION 3.22.1)
project(bytehook)

enable_language(ASM)

file(GLOB SRC *.c)
add_library(bytehook SHARED ${SRC})
target_compile_features(bytehook PUBLIC c_std_17)
target_include_directories(bytehook PUBLIC . include third_party/bsd third_party/lss)
target_link_libraries(bytehook log)




if(USEASAN)
    target_compile_options(bytehook PUBLIC -fsanitize=address -fno-omit-frame-pointer)
    target_link_options(bytehook PUBLIC -fsanitize=address)
else()
    target_compile_options(bytehook PUBLIC -Oz -ffunction-sections -fdata-sections)
    target_link_options(bytehook PUBLIC -Oz -Wl,--exclude-libs,ALL -Wl,--gc-sections -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/bytehook.map.txt)
endif()

