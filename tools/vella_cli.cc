#include <cstdlib>
#include <iostream>
#include <string>

#include "vella_core.hh"

namespace {

bool good(const std::string &text) {
    return text.rfind("ERROR:", 0) != 0 && text.find("0x2a") != std::string::npos;
}

int repeat_test(const char *x64_bin, const char *a64_bin, const char *specs) {
    for (int i = 0; i < 3; ++i) {
        std::string x64 = vella::decompile(
            x64_bin, "x86:LE:64:default", 0x100000, 0, 0x10, "test_func", specs);
        if (!good(x64)) {
            std::cerr << "x86-64 pass " << i << " failed:\n" << x64 << "\n";
            return 1;
        }

        std::string a64 = vella::decompile(
            a64_bin, "AARCH64:LE:64:v8A", 0x400000, 0, 0x10, "test_func", specs);
        if (!good(a64)) {
            std::cerr << "AArch64 pass " << i << " failed:\n" << a64 << "\n";
            return 1;
        }
    }
    std::cout << "repeat decompilation passed\n";
    return 0;
}

} // namespace

int main(int argc, char **argv) {
    if (argc == 5 && std::string(argv[1]) == "--repeat") {
        return repeat_test(argv[2], argv[3], argv[4]);
    }

    if (argc != 8) {
        std::cerr << "usage: vella_cli <binary> <language-id> <base> <entry-offset> "
                     "<size> <function-name> <spec-dir>\n"
                     "       vella_cli --repeat <x64-bin> <a64-bin> <spec-dir>\n";
        return 2;
    }

    const std::string binary = argv[1];
    const std::string language = argv[2];
    const unsigned long long base = std::strtoull(argv[3], nullptr, 0);
    const unsigned long long entry = std::strtoull(argv[4], nullptr, 0);
    const unsigned long long size = std::strtoull(argv[5], nullptr, 0);
    const std::string name = argv[6];
    const std::string specs = argv[7];

    std::string result = vella::decompile(binary, language, base, entry, size, name, specs);
    std::cout << result;
    if (!result.empty() && result.back() != '\n') std::cout << '\n';
    return result.rfind("ERROR:", 0) == 0 ? 1 : 0;
}
