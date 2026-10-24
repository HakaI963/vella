cmake_minimum_required(VERSION 3.22.1)
project(vella CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT DEFINED GHIDRA_CPP)
    message(FATAL_ERROR "GHIDRA_CPP must point at Ghidra's decompile/cpp directory")
endif()

file(GLOB GHIDRA_SOURCES "${GHIDRA_CPP}/*.cc")

# Files that are only for the desktop console, BFD loaders, the Ghidra
# client/server bridge, the SLEIGH compiler, or unit tests.
list(FILTER GHIDRA_SOURCES EXCLUDE REGEX
    "/(analyzesigs|bfd_arch|codedata|loadimage_bfd|callgraph|ifacedecomp|ifaceterm|interface|rulecompile|testfunction|unify|consolemain|sleighexample|test|slgh_compile|slghparse|slghscan)\\.cc$")
list(FILTER GHIDRA_SOURCES EXCLUDE REGEX "/[^/]*ghidra[^/]*[.]cc$")

add_library(vella SHARED
    vella_core.cc
    vella_jni.cc
    ${GHIDRA_SOURCES}
)

target_include_directories(vella PRIVATE "${GHIDRA_CPP}")

target_compile_options(vella PRIVATE
    -O2
    -fexceptions
    -frtti
    -fvisibility=hidden
    -Wno-sign-compare
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    -Wno-deprecated-declarations
)

target_link_libraries(vella z log)
