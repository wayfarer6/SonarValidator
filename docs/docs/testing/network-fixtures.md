# 네트워크 시험 환경

현재 Lab 1은 Cisco 두 대를 Gi2로 연결하고 Gi1 관리망과 R1 Gi3 인터넷을 분리합니다. 두 Guest Shell의 내부 IP가 같아도 각 라우터 NAT로 구분됩니다. [현재 IP와 배포](../deployment/lab1.md)를 사용합니다.

이전 D-AI-PBL PoC는 FRR·OVS·Linux 방화벽·VM에서 VLAN·등급·인터넷 격리를 시험했습니다. 서브넷 39개·VLAN 행 23개는 UI 규모 검토의 근거입니다. 이전 IP 명령과 자격증명을 현재 초기 설정에 사용하지 않습니다. 원본은 Git의 docs/blog/2026-09-22-poc-network-* 이력에 있습니다.

Lab 2–4는 별도 토폴로지입니다. Lab 1 결과를 다른 Lab의 검증 완료로 확대하지 않습니다.
