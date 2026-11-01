#include "components/backend_communication/test_websocket_server.hpp"
#include "components/backend_communication/envelope.hpp"
#include "module/telemetry_module/telemetry_monitor.hpp"
#include "module/management_module/management_service.hpp"
#include "module/configuration_module/prober_config.hpp"
#include "database/database_service.hpp"
#include "database/schema.hpp"
#include <iostream>
#include <stdexcept>
using Json = nlohmann::json;
using namespace std::chrono_literals;
void Require(bool value, const char* message) {
    if (!value) throw std::runtime_error(message);
    std::cout << "[ok] " << message << '\n';
}
void Run(DatabaseService& database, int cached, int desired, long revision, bool change) {
    std::stop_source stop;
    TestWebSocketServer server([&](auto& peer) {
        const auto read = [&] {
            boost::beast::flat_buffer buffer; peer.read(buffer);
            return Json::parse(boost::beast::buffers_to_string(buffer.data()));
        };
        const auto send = [&](const Json& value) { peer.write(boost::asio::buffer(value.dump())); };
        const auto hello = read();
        Require(hello["payload"]["channel"] == "telemetry", "dedicated telemetry hello");
        Require(hello["payload"]["monitor_interval_seconds"] == cached, "restart hello reports SQLite interval");
        send(envelope::Make(envelope::kAck,"test","VM","hello",
            {{"monitor_settings",{{"monitor_interval_seconds",desired},{"revision",revision}}}}));
        auto ack = read();
        Require(ack["payload"]["action"] == "monitor-interval-applied" && ack["payload"]["revision"] == revision,
                "applied acknowledgement follows durable storage");
        auto telemetry = read();
        Require(telemetry["type"] == "telemetry" && telemetry["payload"]["monitor_interval_seconds"] == desired,
                "first collection uses server settings");
        if (change) {
            send(envelope::Make(envelope::kCommand,"test","VM","change",
                {{"action","set-monitor-interval"},{"monitor_interval_seconds",1},{"revision",revision+1}}));
            ack = read();
            Require(ack["payload"]["monitor_interval_seconds"] == 1, "live update acknowledged");
            const auto start = std::chrono::steady_clock::now();
            telemetry = read();
            Require(telemetry["payload"]["monitor_interval_seconds"] == 1 &&
                    std::chrono::steady_clock::now() - start < 2s, "shortened interval is used without reconnect");
            send(envelope::Make(envelope::kCommand,"test","VM","stale",
                {{"action","set-monitor-interval"},{"monitor_interval_seconds",86400},{"revision",revision}}));
            telemetry = read();
            Require(telemetry["type"] == "telemetry" && telemetry["payload"]["monitor_interval_seconds"] == 1,
                    "stale update cannot postpone collection for a day");
        }
        stop.request_stop();
        boost::beast::flat_buffer buffer; boost::system::error_code ignored; peer.read(buffer, ignored);
    });
    ProberConfig config("test","test","Linux","Linux",DeviceType::kVirtualMachine,"interval-test",0,"127.0.0.1",server.Port());
    ManagementService device;
    TelemetryMonitor monitor;
    monitor.RegisterStrategy(std::make_shared<collector::FunctionalTelemetryStrategy>("interval-test",
        [](DeviceType, const std::string& product) { return product == "interval-test"; },
        std::vector<collector::DeviceQuery>{},
        [](DeviceType, const std::string&, const collector::Outputs&) { return collector::CollectedState{}; }, 100));
    monitor.Run(stop.get_token(), config, database, device);
    server.Wait();
}
int main() {
    sqlite3* raw = nullptr;
    Require(sqlite3_open(":memory:",&raw) == SQLITE_OK, "open SQLite");
    DbHandle handle(raw);
    Require(sqlite3_exec(raw,database_schema::CreateTablesSql().c_str(),nullptr,nullptr,nullptr)==SQLITE_OK,"create schema");
    DatabaseService database(handle);
    Run(database,30,2,4,true);
    Require(LoadSetting(database,"monitor_interval_seconds") == "1", "live interval remains in SQLite");
    Run(database,1,3,0,false);
    Require(LoadSetting(database,"monitor_interval_seconds") == "3", "recreated server settings override cache on restart");
}
