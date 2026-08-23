#include <stdio.h>
#include <stdlib.h>

int main() {
    FILE *fp;
    char buffer[256];
    
    // 명령어 실행 및 stdout을 읽기 모("r")로 연결
    fp = popen("ls -l", "r");
    if (fp == NULL) {
        perror("popen failed");
        return 1;
    }

    // 한 줄씩 결과를 읽어서 출력하거나 문자열에 저장
    while (fgets(buffer, sizeof(buffer), fp) != NULL) {
        printf("출력 결과: %s", buffer);
        // 만약 문자열 배열(동적 할당 등)에 담고 싶다면 여기서 복사해두면 됩니다.
    }

    // 닫으면서 자식 프로세스 종료 상태 확인
    pclose(fp);
    return 0;
}