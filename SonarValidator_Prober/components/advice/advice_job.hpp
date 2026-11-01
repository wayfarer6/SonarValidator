#ifndef SONAR_ADVICE_JOB_HPP
#define SONAR_ADVICE_JOB_HPP
#include "components/advice/advice_executor.hpp"
#include "database/database_service.hpp"
#include "module/configuration_module/prober_config.hpp"
#include <stop_token>
namespace advice {
using Sender=std::function<void(const Json&)>;
Runner NativeRunner(const ProberConfig& config,std::stop_token stop);
void Handle(const Json& message,const ProberConfig& config,DatabaseService& database,
            const Runner& run,const Sender& send,bool terminal_busy);
}
#endif
