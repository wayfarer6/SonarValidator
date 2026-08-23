#include <iostream>
#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>

extern char **environ;
// 환경 변수 없으면 실행이 안되서 스폰을 호출한 자식 프로세스가 가진 환경변수를 당겨와야함, extern 키워드를 써야한다고 함.
int main() {
    // 실행할 Bash 명령어
    const char* command = "echo 'Hello from C++23 via bash' && ls -l";

    char *argv[] = {
        (char*)"sh",
        (char*)"-c",
        (char*)command,
        nullptr
    };

    pid_t pid;
    // posix_spawn으로 /bin/sh 실행
    int status = posix_spawn(&pid, "/bin/sh", nullptr, nullptr, argv, environ);

    if (status == 0) {
        std::cout << "Process spawned with PID: " << pid << std::endl;
        // 자식 프로세스 종료 대기
        int ws;
        waitpid(pid, &ws, 0);
    } else {
        std::cerr << "posix_spawn failed: " << status << std::endl;
    }

    return 0;
}
