#include "utils/log.hpp"
#include "module/telemetry_module/telemetry_monitor.hpp"
#include "components/telemetry/monitor_schedule.hpp"
#include <chrono>
#include <iostream>
#include "utils/stop_wait.hpp"
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
        if (!saved) SV_LOG_WARN << "[TELEMETRY] local snapshot write failed\n";
    }
    catch (const std::exception& ex)
    {

        SV_LOG_WARN << "[TELEMETRY] persist skipped: " << ex.what() << '\n';
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
        telemetry_service.SetStopToken(stop_token);

        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();

        using Clock = monitor_settings::Schedule::Clock;
        monitor_settings::Schedule schedule(
            monitor_settings::FromCache(LoadSetting(database_service, "monitor_interval_seconds")), Clock::now());
        auto next_connect = Clock::now();

        const auto apply_settings = [&](const Json& message) {
            const auto update = monitor_settings::Parse(envelope::Payload(message));
            if (!update || !schedule.Accepts(*update)) return;
            Json reply{{"revision", update->revision}};
            if (!SaveSetting(database_service, "monitor_interval_seconds", std::to_string(update->seconds))) {
                reply["action"] = "monitor-interval-rejected";
                reply["requested_interval_seconds"] = update->seconds;
                reply["monitor_interval_seconds"] = schedule.Seconds();
                reply["reason"] = "Prober could not persist the collection interval";
            } else {
                schedule.Apply(*update, Clock::now());
                reply["action"] = "monitor-interval-applied";
                reply["monitor_interval_seconds"] = schedule.Seconds();
                SV_LOG_INFO << "수집 주기 변경: " << schedule.Seconds() << "초" << '\n';
            }
            telemetry_service.sendText(envelope::Make(envelope::kAck, agent_id,
                envelope::DeviceTypeToString(config.GetDeviceType()), envelope::CorrelationId(message), reply).dump());
        };

        FunctionalTelemetryObserver storage([&](const TelemetryEvent& event) {
            PersistSnapshot(database_service, event.agent_name, event.state, event.collected_at);
        });
        auto next_summary = Clock::time_point::min();
        bool transmission_failed = false;
        FunctionalTelemetryObserver network([&](const TelemetryEvent& event) {
            const auto report = envelope::Telemetry(event.agent_id, config.GetDeviceType(), event.payload);
            if (!telemetry_service.IsConnected() || !telemetry_service.sendText(report.dump())) {
                transmission_failed = true;
                SV_LOG_WARN << "수집 전송 실패: 로컬 저장소에 보관 중";
                return;
            }
            if (transmission_failed) {
                SV_LOG_INFO << "수집 전송 복구";
                transmission_failed = false;
            }
            if (Clock::now() >= next_summary) {
                SV_LOG_INFO << "수집·전송 완료: 인터페이스="
                            << event.payload.value("nic_status", Json::object()).value("interfaces", Json::array()).size()
                            << " 경로=" << event.payload.value("route_status", Json::object()).value("routes", Json::array()).size();
                next_summary = Clock::now() + std::chrono::minutes(5);
            }
        });
        TelemetryEvents events;
        events.Subscribe(storage);
        events.Subscribe(network);
        for (const auto& observer : observers_) events.Subscribe(*observer);

        while (!stop_token.stop_requested())
        {
            if (!telemetry_service.IsConnected() && Clock::now() >= next_connect) {
                if (telemetry_service.connect()) {
                    schedule.Reconnected();
                    auto hello = envelope::Hello(agent_id, config.GetDeviceType());
                    hello["payload"]["channel"] = "telemetry";
                    hello["payload"]["monitor_interval_seconds"] = schedule.Seconds();
                    telemetry_service.sendText(hello.dump());
                }
                next_connect = Clock::now() + std::chrono::seconds(3);
            }
            if (stop_token.stop_requested()) break;
            std::string instruction;
            if (telemetry_service.IsConnected()) {
                // Poll even when collection is due, so the initial hello can supply settings.
                if (telemetry_service.tryReceiveText(instruction, std::chrono::milliseconds(200))) {
                    try { apply_settings(Json::parse(instruction)); }
                    catch (const std::exception& error) {
                        SV_LOG_WARN << "[TELEMETRY] invalid settings message: " << error.what() << '\n';
                    }
                }
            } else {
                WaitForStop(stop_token, schedule.Wait(Clock::now()));
            }
            if (stop_token.stop_requested()) break;
            if (!schedule.Due(Clock::now())) continue;

            auto collected = collector::CollectState(config, management_service, strategies_);
            if (stop_token.stop_requested()) break;
            auto body = BuildTelemetryPayload(config, collected);
            const auto collected_at = telemetry_store::CurrentUtcTimestamp();
            body["collected_at"] = collected_at;
            body["monitor_interval_seconds"] = schedule.Seconds();
            body["monitor_revision"] = schedule.Revision();
            const TelemetryEvent event{agent_id, config.GetAgentName(), collected_at,
                                       std::move(collected), std::move(body)};
            events.Publish(event);
            schedule.Collected(Clock::now());
        }
    }
    catch (const std::exception& ex)
    {
        SV_LOG_WARN << "[WARN] Telemetry monitor exception: " << ex.what() << '\n';
    }
}
