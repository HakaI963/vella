#include <jni.h>

#include <string>

#include "vella_core.hh"

namespace {

std::string from_java(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_vella_app_Native_decompile(JNIEnv *env,
                                    jobject,
                                    jstring binary_path,
                                    jstring language_id,
                                    jlong base,
                                    jlong entry_offset,
                                    jlong size,
                                    jstring function_name,
                                    jstring spec_dir) {
    std::string path = from_java(env, binary_path);
    std::string language = from_java(env, language_id);
    std::string name = from_java(env, function_name);
    std::string specs = from_java(env, spec_dir);

    unsigned long long base_value = base > 0 ? static_cast<unsigned long long>(base) : 0;
    unsigned long long entry_value =
        entry_offset > 0 ? static_cast<unsigned long long>(entry_offset) : 0;
    unsigned long long size_value = size > 0 ? static_cast<unsigned long long>(size) : 0x4000;

    std::string result =
        vella::decompile(path, language, base_value, entry_value, size_value, name, specs);
    return env->NewStringUTF(result.c_str());
}
