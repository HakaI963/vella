#ifndef VELLA_CORE_HH
#define VELLA_CORE_HH

#include <string>

namespace vella {

std::string decompile(const std::string &binary_path,
                      const std::string &language_id,
                      unsigned long long base,
                      unsigned long long entry_offset,
                      unsigned long long size,
                      const std::string &function_name,
                      const std::string &spec_dir);

} // namespace vella

#endif
