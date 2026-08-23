#include <csignal>
#include <atomic>
#include <iostream>

std::atomic<bool> g_running(true);

void signalHandler(int signum)
{
    std::cout << "[INFO] Signal (" << signum << ") received. Stopping..." << std::endl;
    g_running = false;
}

int main()
{
    std::signal(SIGINT, signalHandler);  // Ctrl+C
    std::signal(SIGTERM, signalHandler);  // kill, systemd stop 등

	// first launching detected
	
	conf_file_handler = fopen("~/.config/sonar_prover_agent/db.conf");
	
    if(conf_file_handler == NULL)
	{
		// create db.conf
		
		if(conf_file_handler_create method failed)
		{
			cerr << " Cant create conf file, Program exited! " << endl;
		}
		
		// init db (sqlite based)

		{
		 cerr << " DB init Failed Program exited !" endl; 
		}

		// call utility function

		get_nic_info();
		get_sys_info();
		

		// network log 

	}


    while (g_running)
    {
        // 메인 루프
        // ...

	create_thread_monitor();
	create_thread_connection();
	create_thread_db_manage();

	if (SIGTERM occured)
	{
		// log  exited
		exit; // escape loop;
	}
    }

    // 여기서 자원 정리 (파일/소켓/스레드 등)
    std::cout << "[INFO] Clean shutdown complete." << std::endl;
    return 0;
}
