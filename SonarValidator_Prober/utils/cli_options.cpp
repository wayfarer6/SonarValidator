#include "utils/cli_options.hpp"
#include <iostream>

CliOptions CliOptions::ParseArgs(int argc, char** argv) {
    CliOptions options;
    for (int i = 1; i < argc; ++i) {
        const std::string argument = argv[i] == nullptr ? "" : argv[i];
        if (argument == "--help" || argument == "-h") options.show_help = true;
        else if (argument == "--export" && options.export_file.empty()) {
            if (i + 1 >= argc || argv[i + 1] == nullptr || std::string(argv[i + 1]).starts_with("-")) {
                options.error = "--export requires an output file";
                break;
            }
            options.export_file = argv[++i];
        } else {
            options.error = "unknown or repeated argument: " + argument;
            break;
        }
    }
    return options;
}

void CliOptions::printUsage(const char* program) const {
    std::cout << "SonarValidator Prober\nUsage: " << (program ? program : "sonar_validator_prober")
              << " [--export <snapshot.json>]\n"
              << "  --export <file>   Collect once, write one JSON file, then exit.\n"
              << "  --help, -h        Show this help.\n";
}
