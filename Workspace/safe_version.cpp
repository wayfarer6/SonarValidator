#include <iostream>
#include <memory>
#include <cstdio>

// pclose용 커스텀 삭제자
struct ClosePFile {
    void operator()(FILE* fp) const {
        if (fp) {
            pclose(fp);
        }
    }
};

using SafePFile = std::unique_ptr<FILE, ClosePFile>;

using SafeShell = std::unique_ptr<FILE,ClosePFile>;

int main() {
    // popen 결과를 스마트 포인터로 안전하게 관리
    //SafePFile fp(popen("ls -l", "r"));
    
    SafeShell shell(popen("ip -o link show | awk -F': ' '$2 != \"lo\" {print $2}'", "r"));
    SafeShell shell2(popen("ip -o -4 address show | awk '$2 != \"lo\" {print $4}' | awk -F'/' '{print $0}'", "r"));
    if (!shell) {
        std::cerr << "popen failed\n";
        return 1;
    }

    char buffer[1024];
    /*
    while (fgets(buffer, sizeof(buffer), shell.get()) != NULL) {
        std::cout << buffer;
    }
    */

    while (fgets(buffer, sizeof(buffer), shell2.get()) != NULL) {
        std::cout << buffer;
    }


    // 함수를 벗어나거나 에외가 발생해도 알아서 pclose(fp)가 호출됩니다!
    return 0;
}