package org.sonar.sonarvalidator_backend.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OperationLogLifecycle {
    private static final Logger log = LoggerFactory.getLogger(OperationLogLifecycle.class);

    @EventListener
    public void started(ApplicationReadyEvent event) {
        final var environment = event.getApplicationContext().getEnvironment();
        log.info("백엔드 시작: 포트={}", environment.getProperty("local.server.port",
                environment.getProperty("server.port", "3000")));
    }

    @EventListener
    public void stopped(ContextClosedEvent event) {
        log.info("백엔드 종료");
    }
}
