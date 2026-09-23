// 오프라인 export 옵션을 먼저 해석합니다.
    // (설정/DB 를 준비하기 전에 --help 로 끝낼 수 있어야 한다)
    const CliOptions options = ParseArgs(argc, argv);
    if (options.show_help)
    {
        PrintUsage(argc > 0 ? argv[0] : nullptr);
        return 0;
    }

    // 데이터 디렉터리와 템플릿 경로를 결정합니다.
    //  환경변수로 오버라이드할 수 있어 root 가 아닌 환경(vEOS bash 등)에서도 실행됩니다.
    const fs::path data_directory = ResolveDataDirectory();
    const fs::path config_file_path = data_directory / "settings.conf";
    const fs::path sqlite_db_path = data_directory / "prober_db.sqlite";
    const fs::path sqlite_template_path = ResolveTemplatePath();

    // 임시 기본값으로 config를 만든 뒤, PrepareRuntime에서 실제 값으로 채웁니다.
    ProberConfig config(
        "","","","",DeviceType::kSwitch,"",
        0, "", 0);

    DbHandle database(nullptr);
    if (!AppInitializer::PrepareRuntime(
            data_directory,
            config_file_path,
            sqlite_db_path,
            sqlite_template_path,
            config,
            database))
    {
        std::cerr << "Runtime initialization failed\n";
        std::cerr << "  data dir : " << data_directory << '\n';
        std::cerr << "  template : " << sqlite_template_path << '\n';
        std::cerr << "  (SONAR_DATA_DIR / SONAR_TEMPLATE_PATH 로 경로를 지정할 수 있습니다)\n";
        return 1;
    }

    // 오프라인 폴백 디렉터리를 결정합니다.
    //  --export-dir > SONAR_OFFLINE_DIR > <데이터 디렉터리>/offline
    //  (--export-offline 또는 --export-once 일 때만 실제로 쓰입니다)
    const fs::path offline_directory =
        (options.offline_only || options.export_once)
            ? fs::path(offline::ResolveExportDirectory(data_directory.string(), options.export_dir))
            : fs::path(options.export_dir);

    // --export-once: 수집을 한 번만 하고 결과를 파일/표준출력으로 남긴 뒤 종료합니다.
    // 서버가 없는 장비에서 설정만 뽑아 가져갈 때 쓰는 경로입니다.
    if (options.export_once)
    {
        ManagementService export_service(
            config.GetServerIpv4(),
            static_cast<int>(config.GetServerPort()),
            "/api/v1/management");

        const Json snapshot = TelemetryMonitor::CollectSnapshotDocument(config, export_service);

        if (options.export_stdout)
        {
            // 표준출력으로만 인쇄합니다. (파일 없이 복사/붙여넣기 → 프론트엔드 업로드)
            std::cout << snapshot.dump(2) << '\n'; // json의 dump 2는 무슨 옵션이지.
            return 0;
        }

        std::error_code directory_error;
        fs::create_directories(offline_directory, directory_error);

        const std::string agent_id =
            config.GetAgentId().empty() ? config.GetAgentName() : config.GetAgentId();
        const offline::ExportResult export_result = offline::ExportSnapshot(
            offline_directory.string(),
            agent_id,
            snapshot.value("collected_at", std::string{}),
            snapshot);

        if (!export_result.saved)
        {
            std::cerr << "[ERROR] 스냅샷을 저장하지 못했습니다: " << export_result.message << '\n';
            std::cerr << "        (--export-dir 또는 SONAR_OFFLINE_DIR 로 경로를 지정하세요)\n";
            return 1;
        }

        std::cout << "[INFO] 스냅샷을 저장했습니다: " << export_result.path << '\n';
        std::cout << "       이 파일을 SonarValidator 프론트엔드의 "
                     "'Import Offline Prober Data' 카드에 끌어다 놓으세요.\n";
        return 0;
    }