package org.sonar.sonarvalidator_backend.Controller;

import org.sonar.sonarvalidator_backend.Model.dto.AgentMessage;
import org.sonar.sonarvalidator_backend.Service.AgentWebSocketService;
import org.springframework.boot.jackson.autoconfigure.JacksonProperties;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.ArrayList;

@Controller 
public interface AgentController {

    private final AgentWebSocketService agentWebSocketService;
    public ArrayList<JacksonProperties.Json> FetchPolicy();
    public ArrayList<JacksonProperties.Json> FetchSystemInfo(String agent_id);


    
    public AgentController(AgentWebSocketService agentWebSocketService) {
        this.agentWebSocketService = agentWebSocketService;
    }

    @MessageMapping("/agent/send")
    @SendTo("/topic/agent/messages") // 결과를 이 토픽을 구독한 모든 사용자에게 브로드캐스팅
    public AgentMessage handleMessage(AgentMessage message) {
        // Service를 호출해 비즈니스 로직(DB 저장, 가공 등) 수행 가능
        return agentService.processMessage(message);
    }

    @MessageMapping("/agent/private")
    public void sendPrivateMessage(@Payload AgentMessage message) {
        // message 객체 안에 recipient(수신자 ID)가 들어있다고 가정
        String recipient = message.getRecipient(); 
        
        // convertAndSendToUser를 쓰면 Config에서 설정한 "/user" 프리픽스가 붙어서 
        // 해당 사용자(recipient)의 개인 큐("/queue/message")로 안전하게 배달됨
        messagingTemplate.convertAndSendToUser(
                recipient, 
                "/queue/message", 
                message
        );
    }

}
