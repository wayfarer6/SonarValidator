package org.sonar.sonarvalidator_backend.Service;


import org.sonar.sonarvalidator_backend.Model.dto.AgentMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;


@Service 
public class AgentWebSocketService {
    private WebSocketSession clientSession;
    private String targeturl;
    private final SimpMessagingTemplate messagingTemplate;

    // 외부(예: 스케줄러, 비동기 이벤트 등)에서 임의로 메시지를 푸시할 때 사용
    public AgentWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

     public AgentMessage processMessage(AgentMessage message) {
        // 필요한 데이터 검증 및 비즈니스 로직 처리
        message.setContent(message.getContent() + " (Processed by Server)");
        return message;
    }

    // 예시: 특정 시점에 서버에서 클라이언트로 일방향 푸시를 하고 싶을 때
    public void sendPeriodicNotification(String destination, AgentMessage message) {
        messagingTemplate.convertAndSend(destination, message);
    }
   
}
