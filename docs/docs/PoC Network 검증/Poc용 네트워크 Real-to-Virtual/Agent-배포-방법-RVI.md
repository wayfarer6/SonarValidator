default.conf 설정의 경우 

# agent 생성시 서버측에서 ip, port 인증서 등을 지정함
SERVER_IP=10.20.0.3;
SERVER_PORT=3000;
NODE_TYPE=Switch;
AGENT_NAME=Arista-Switch;
TERMINAL_SHARED_SECRET=a300630c5f3502b7ee0384a3927c11d489ba46a0b93770334f7ea8392a9ed184 ; // 터미널과 통신을 위한 시크릿 키 (이걸로 터미널 문제는 해결!)


scp -P 2222 -r /home/osboxes/SonarValidator/SonarValidator_Prober/Installer guestshell@10.20.0.1:/home/guestshell

# agent 생성시 서버측에서 ip, port 인증서 등을 지정함
SERVER_IP=10.20.0.3;
SERVER_PORT=3000;
NODE_TYPE=Router;
AGENT_NAME=Cisco-Router;
TERMINAL_SHARED_SECRET=a300630c5f3502b7ee0384a3927c11d489ba46a0b93770334f7ea8392a9ed184 ; // 터미널과 통신을 위한 시크릿 키 (이걸로 터미널 문제는 해결!)

- 터미널 안되면 secret 문제임



