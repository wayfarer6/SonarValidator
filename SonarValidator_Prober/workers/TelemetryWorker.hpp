
// 텔레메트리 스레드 진입점입니다. 실제 루프는 TelemetryMonitor가 담당합니다.
// (조회 명령 실행 + 파싱 → 서버 전송 + DB 큐 저장, 기본 30초 간격)
void TelemetryWorker(std::stop_token stop_token,
                     const ProberConfig &config,
                     DatabaseQueue &database_queue,
                     const fs::path &offline_directory,
                     bool offline_only)
{
    // 장치 조회 명령 실행에 쓰는 서비스입니다.
    // 관리 스레드와 정책 적용은 각자 별도 인스턴스를 씁니다(영속 CLI 세션 공유 방지).
    ManagementService management_service(
        config.GetServerIpv4(),
        static_cast<int>(config.GetServerPort()),
        "/api/v1/management");

    TelemetryMonitor monitor;

    // 오프라인 폴백 설정입니다. 디렉터리가 비어 있으면 기능이 꺼집니다.
    monitor.SetOfflineExportDirectory(offline_directory.empty() ? std::string{} : offline_directory.string());
    monitor.SetOfflineOnly(offline_only);

    monitor.Run(stop_token, config, database_queue, management_service);
}
