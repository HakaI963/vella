#include "vella_core.hh"

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

void init_library(const std::string &spec_dir) {
    std::call_once(g_init_once, [spec_dir]() {
        std::vector<std::string> paths;
        paths.push_back(spec_dir);
        ghidra::startDecompilerLibrary(paths);
    });
}

std::string sanitize_comment(const std::string &text) {
    std::string result;
    result.reserve(text.size());
    for (size_t i = 0; i < text.size(); ++i) {
        if (text[i] == '*' && i + 1 < text.size() && text[i + 1] == '/') {
            result += "* /";
            ++i;
        } else {
            result += text[i];
        }
    }
    return result;
}

std::string run(const std::string &binary_path,
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
        body = "/* " + sanitize_comment(notes) + "*/\n" + body;
    }
    return body;
}

} // namespace

namespace vella {

std::string decompile(const std::string &binary_path,
                      const std::string &language_id,
                      unsigned long long base,
                      unsigned long long entry_offset,
                      unsigned long long size,
                      const std::string &function_name,
                      const std::string &spec_dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    return run(binary_path,
               language_id,
               static_cast<ghidra::uintb>(base),
               static_cast<ghidra::uintb>(entry_offset),
               static_cast<ghidra::uintb>(size == 0 ? 0x4000 : size),
               function_name.empty() ? std::string("entry") : function_name,
               spec_dir);
}

} // namespace vella
