#include <cstdlib>
#include <iostream>
#include <string>

#include "vella_core.hh"

int main(int argc, char **argv) {
    if (argc != 8) {
        std::cerr << "usage: vella_cli <binary> <language-id> <base> <entry-offset> "
                     "<size> <function-name> <spec-dir>\n";
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
