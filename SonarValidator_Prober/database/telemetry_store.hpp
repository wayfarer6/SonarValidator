#ifndef SONAR_VALIDATOR_PROBER_DATABASE_TELEMETRY_STORE_HPP_
#define SONAR_VALIDATOR_PROBER_DATABASE_TELEMETRY_STORE_HPP_

#include <string>

#include <nlohmann/json.hpp>

#include "database/database_service.hpp"

namespace telemetry_store
{

std::string CurrentUtcTimestamp();

bool SaveRouteStatus(DatabaseService& database_service,
                            const std::string& agent,
                            const nlohmann::json& route_status,
                            const std::string& collected_at = {});

bool SaveNicInfo(DatabaseService& database_service,
                        const std::string& agent,
                        const nlohmann::json& nic_status,
                        const std::string& collected_at = {});

bool SaveVlanStatus(DatabaseService& database_service,
                           const std::string& agent,
                           const nlohmann::json& vlan_status,
                           const std::string& collected_at = {});

bool SaveTrunkStatus(DatabaseService& database_service,
                            const std::string& agent,
                            const nlohmann::json& trunk_status,
                            const std::string& collected_at = {});

bool SaveArpTable(DatabaseService& database_service,
                         const std::string& agent,
                         const nlohmann::json& arp_table,
                         const std::string& collected_at = {});

}

#endif
