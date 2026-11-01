#ifndef SONAR_ADVICE_EXECUTOR_HPP
#define SONAR_ADVICE_EXECUTOR_HPP
#include <functional>
#include <string>
#include <vector>
#include <nlohmann/json.hpp>
#include "components/terminal/command_runner.hpp"
namespace advice {
using Json=nlohmann::json;
enum class Channel { Shell, Ios, IosSnapshot, Eos, Nft };
using Runner=std::function<command_runner::Result(Channel,const std::vector<std::string>&)>;
struct Plan { std::string operation,profile; Json arguments; std::vector<std::string> commands,verification; bool write=false; };
std::string Profile(std::string product);
std::vector<std::string> Operations(const std::string& product,bool nft_available);
Plan Compile(const std::string& operation,const std::string& product,Json arguments,
             const std::string& management_prefixes,const std::string& server);
Json Execute(const Plan& plan,const Runner& run);
std::string Hash(const std::string& text);
}
#endif
