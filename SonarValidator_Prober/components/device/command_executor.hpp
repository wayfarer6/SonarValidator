#ifndef SONAR_DEVICE_COMMAND_EXECUTOR_HPP_
#define SONAR_DEVICE_COMMAND_EXECUTOR_HPP_
#include <string>
#include <vector>

class DeviceCommandExecutor {
public:
    virtual ~DeviceCommandExecutor() = default;
    virtual bool RunCommand(const std::string& command) = 0;
    virtual std::string RunCommandOutput(const std::string& command) = 0;
    virtual std::string ExecuteIosCli(const std::vector<std::string>& commands) = 0;
    virtual std::string CliCommand(const std::vector<std::string>& argv, const std::string& command) = 0;
    virtual std::string QueryAristaCli(const std::string& command) = 0;
};
#endif
