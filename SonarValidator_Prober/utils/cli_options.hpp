#ifndef SONAR_VALIDATOR_CLI_OPTIONS_HPP_
#define SONAR_VALIDATOR_CLI_OPTIONS_HPP_
#include <string>

struct CliOptions {
    std::string export_file;
    std::string error;
    bool show_help = false;
    static CliOptions ParseArgs(int argc, char** argv);
    void printUsage(const char* program) const;
};
#endif
