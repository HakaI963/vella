#include <jni.h>

#include <mutex>
#include <sstream>
#include <string>
#include <vector>

#include "libdecomp.hh"
#include "sleigh_arch.hh"
#include "raw_arch.hh"
#include "database.hh"
#include "funcdata.hh"
#include "printlanguage.hh"

namespace {

std::mutex g_mutex;
std::once_flag g_init_once;

std::string from_java(JNIEnv *env, jstring value) {
    if (value == nullptr) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result = chars == nullptr ? "" : chars;
    if (chars != nullptr) env->ReleaseStringUTFChars(value, chars);
    return result;
}

void init_library(const std::string &spec_dir) {
    std::call_once(g_init_once, [spec_dir]() {
        std::vector<std::string> paths;
        paths.push_back(spec_dir);
        ghidra::startDecompilerLibrary(paths);
    });
}

std::string decompile(const std::string &binary_path,
                      const std::string &language_id,
                      ghidra::uintb base,
                      ghidra::uintb entry_offset,
                      ghidra::uintb size,
                      const std::string &function_name,
                      const std::string &spec_dir) {
    std::ostringstream output;
    std::ostringstream diagnostics;

    try {
        init_library(spec_dir);

        ghidra::RawBinaryArchitecture architecture(binary_path, language_id, &diagnostics);
        ghidra::DocumentStorage store;
        architecture.init(store);

        if (base != 0) {
            architecture.loader->adjustVma(static_cast<long>(base));
        }

        ghidra::AddrSpace *space = architecture.getDefaultCodeSpace();
        ghidra::uintb entry_address = base + entry_offset;
        ghidra::Address entry(space, entry_address);

        ghidra::FunctionSymbol *symbol =
            architecture.symboltab->getGlobalScope()->addFunction(entry, function_name);
        ghidra::Funcdata *data = symbol->getFunction();
        if (data == nullptr) {
            throw ghidra::LowlevelError("Unable to create function");
        }

        try {
            data->followFlow(entry, ghidra::Address(space, entry_address + size));
        } catch (ghidra::RecovError &err) {
            diagnostics << "flow: " << err.explain << "\n";
        }

        architecture.allacts.getCurrent()->reset(*data);
        ghidra::int4 status = architecture.allacts.getCurrent()->perform(*data);
        if (status < 0) {
            diagnostics << "analysis stopped early\n";
        }

        architecture.print->setOutputStream(&output);
        architecture.print->docFunction(data);
    } catch (ghidra::LowlevelError &err) {
        return "ERROR: " + err.explain;
    } catch (std::exception &err) {
        return std::string("ERROR: ") + err.what();
    } catch (...) {
        return "ERROR: unknown native failure";
    }

    std::string body = output.str();
    if (body.empty()) {
        body = "// Decompiler produced no output.\n";
    }

    std::string notes = diagnostics.str();
    if (!notes.empty()) {
        std::string sanitized;
        for (size_t i = 0; i < notes.size(); ++i) {
            if (notes[i] == '*' && i + 1 < notes.size() && notes[i + 1] == '/') {
                sanitized += "* /";
                ++i;
            } else {
                sanitized += notes[i];
            }
        }
        body = "/* " + sanitized + "*/\n" + body;
    }
    return body;
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

    ghidra::uintb base_value = base > 0 ? static_cast<ghidra::uintb>(base) : 0;
    ghidra::uintb entry_value = entry_offset > 0 ? static_cast<ghidra::uintb>(entry_offset) : 0;
    ghidra::uintb size_value = size > 0 ? static_cast<ghidra::uintb>(size) : 0x4000;
    if (name.empty()) name = "entry";

    std::string result;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        result = decompile(path, language, base_value, entry_value, size_value, name, specs);
    }
    return env->NewStringUTF(result.c_str());
}
