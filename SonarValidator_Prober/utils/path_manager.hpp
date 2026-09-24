#include <iostream>
#include <filesystem>

namespace fs = std::filesystem;

class PathManager {
    public:
        static fs::path ResolveDataDirectory();
        static fs::path ResolveTemplatePath(); // Database Template.
};