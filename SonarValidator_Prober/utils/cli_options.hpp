#include <iostream>

class CliOptions {
    
    public:
        bool offline_only = false;
        bool export_once = false;
        bool export_stdout = false;
        std::string export_dir{};
        bool show_help = false;
        CliOptions ParseArgs(int argc, char **argv);
        void printUsage(const char *program) const;
};