#include "module/telemetry_module/telemetry_monitor.hpp"
#include <chrono>
#include <iostream>
#include <thread>
#include <nlohmann/json.hpp>
#include "module/telemetry_module/command_collector.hpp"
#include "database/database_service.hpp"
#include "database/telemetry_store.hpp"
#include "components/device/device_type.hpp"
#include "components/backend_communication/envelope.hpp"
#include "module/management_module/management_service.hpp"
#include "module/offline_export_module/offline_export.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "module/telemetry_module/telemetry_service.hpp"

using Json = nlohmann::json;

void TelemetryMonitor::SetMonitorInterval(std::chrono::seconds interval)
{
    const int seconds = static_cast<int>(interval.count());
    if (seconds > 0)
    {
        interval_seconds_.store(seconds);
    }
}

std::chrono::seconds TelemetryMonitor::GetMonitorInterval() const
{
    return std::chrono::seconds(interval_seconds_.load());
}

namespace
{

Json BuildTelemetryPayload(const ProberConfig& config, const collector::CollectedState& collected)
{
    Json body;
    body["agent"] = config.GetAgentName();
    body["kernel"] = config.GetKernelName();

    const std::string product_name = config.GetProductName();
    if (!product_name.empty())
    {
        body["product"] = product_name;
        body["vendor"] = product_name;
    }
    body["device_type"] = envelope::DeviceTypeToString(config.GetDeviceType());

    if (collected.nic.is_object())
    {
        body["nic_status"] = collected.nic;
    }
    if (collected.route.is_object())
    {
        body["route_status"] = collected.route;
    }
    if (collected.vlan.is_object())
    {
        body["vlan_status"] = collected.vlan;
    }
    if (collected.trunk.is_object())
    {
        body["trunk_status"] = collected.trunk;
    }
    if (collected.arp.is_object())
    {
        body["arp_table"] = collected.arp;
    }

    if (collected.rules.is_object())
    {
        body["firewall_rules"] = collected.rules;
    }
    if (collected.topology.is_object())
    {
        body["ovs_topology"] = collected.topology;
    }

    return body;
}

void PersistSnapshot(DatabaseService& database_service,
                          const std::string& agent_name,
                          const collector::CollectedState& collected,
                          const std::string& collected_at)
{
    try
    {
        bool saved = true;
        if (collected.nic.is_object())
        {
            saved = telemetry_store::SaveNicInfo(
                database_service, agent_name, collected.nic, collected_at) && saved;
        }
        if (collected.route.is_object())
        {
            saved = telemetry_store::SaveRouteStatus(
                database_service, agent_name, collected.route, collected_at) && saved;
        }
        if (collected.vlan.is_object())
        {
            saved = telemetry_store::SaveVlanStatus(
                database_service, agent_name, collected.vlan, collected_at) && saved;
        }
        if (collected.trunk.is_object())
        {
            saved = telemetry_store::SaveTrunkStatus(
                database_service, agent_name, collected.trunk, collected_at) && saved;
        }
        if (collected.arp.is_object())
        {
            saved = telemetry_store::SaveArpTable(
                database_service, agent_name, collected.arp, collected_at) && saved;
        }
        if (!saved) std::cerr << "[TELEMETRY] local snapshot write failed\n";
    }
    catch (const std::exception& ex)
    {

        std::cerr << "[TELEMETRY] persist skipped: " << ex.what() << '\n';
    }
}

}

Json TelemetryMonitor::CollectSnapshotDocument(const ProberConfig& config,
                                               ManagementService& management_service,
                                               const collector::TelemetryStrategyRegistry& strategies)
{
    const collector::CollectedState collected = collector::CollectState(config, management_service, strategies);

    const std::string agent_id =
        config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

    return offline::BuildSnapshotDocument(
        agent_id,
        config.GetAgentName(),
        envelope::DeviceTypeToString(config.GetDeviceType()),
        config.GetProductName(),
        config.GetProductName(),
        config.GetKernelName(),
        telemetry_store::CurrentUtcTimestamp(),
        "manual-export",
        BuildTelemetryPayload(config, collected));
}

void TelemetryMonitor::Run(std::stop_token stop_token,
                           const ProberConfig& config,
                           DatabaseService& database_service,
                           ManagementService& management_service)
{
    try
    {
        TelemetryService telemetry_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/v1/telemetry");

        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

        FunctionalTelemetryObserver storage([&](const TelemetryEvent& event) {
            PersistSnapshot(database_service, event.agent_name, event.state, event.collected_at);
        });
        FunctionalTelemetryObserver network([&](const TelemetryEvent& event) {
            const auto report = envelope::Telemetry(event.agent_id, config.GetDeviceType(), event.payload);
            if (!telemetry_service.sendRequest(report.dump(), "/api/v1/telemetry"))
                std::cerr << "[TELEMETRY] server unavailable; snapshot retained in local SQLite\n";
        });
        TelemetryEvents events;
        events.Subscribe(storage);
        events.Subscribe(network);
        for (const auto& observer : observers_) events.Subscribe(*observer);

        while (!stop_token.stop_requested())
        {
            const auto interval = std::chrono::seconds(interval_seconds_.load());
            const auto deadline = std::chrono::steady_clock::now() + interval;

            while (!stop_token.stop_requested() &&
                   std::chrono::steady_clock::now() < deadline)
            {
                std::string instruction;
                if (telemetry_service.tryReceiveText(instruction, std::chrono::milliseconds(200)))
                {
                    try
                    {
                        const Json message = Json::parse(instruction);

                        const Json& payload = envelope::Payload(message);
                        int seconds = 0;
                        if (payload.contains("monitor_interval"))
                        {
                            seconds = payload.at("monitor_interval").get<int>();
                        }
                        else if (message.contains("monitor_interval"))
                        {
                            seconds = message["monitor_interval"].get<int>();
                        }

                        if (seconds > 0)
                        {
                            interval_seconds_.store(seconds);
                            std::cout << "[TELEMETRY] monitor interval updated: "
                                      << seconds << "s\n";
                        }
                        else
                        {
                            std::cout << "[TELEMETRY] server command: "
                                      << envelope::Type(message) << '\n';
                        }
                    }
                    catch (const std::exception&)
                    {
                    }
                }
                else
                {
                    std::this_thread::sleep_for(std::chrono::milliseconds(200));
                }
            }

            if (stop_token.stop_requested())
            {
                break;
            }

            auto collected = collector::CollectState(config, management_service, strategies_);
            if (stop_token.stop_requested()) break;

            auto body = BuildTelemetryPayload(config, collected);
            const TelemetryEvent event{agent_id, config.GetAgentName(), telemetry_store::CurrentUtcTimestamp(),
                                       std::move(collected), std::move(body)};
            events.Publish(event);

        }
    }
    catch (const std::exception& ex)
    {
        std::cerr << "[WARN] Telemetry monitor exception: " << ex.what() << '\n';
    }
}
