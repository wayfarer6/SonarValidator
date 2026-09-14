package org.sonar.sonarvalidator_backend.Config;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;


@Configuration 
@EnableWebSocketMessageBroker 
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer{

    @Override 
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Subscribe 경로

        config.enableSimpleBroker("/topic","/queue"); // /topic 1:N , /queue는 1대 1

        //client의 prefix
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 웹소켓 연결을 위한 엔드포인트
        registry.addEndpoint("/agent")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }
}
