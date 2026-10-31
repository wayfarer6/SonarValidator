package org.sonar.sonarvalidator_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WebSocketBufferSizeTest {

    private static final int LARGE_PAYLOAD_BYTES = 12 * 1024;

    private static final int TOMCAT_DEFAULT_BUFFER = 8192;

    @Test
    @DisplayName("WebSocket 컨테이너 버퍼가 Tomcat 기본값(8KB)보다 크다")
    void containerBufferExceedsTomcatDefault() {
        final org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean
                container = new org.sonar.sonarvalidator_backend.Config.WebSocketConfig(
                        null, null, null, null)
                        .createWebSocketContainer();

        assertNotNull(container, "container bean must exist");
        assertEquals(1024 * 1024, container.getMaxTextMessageBufferSize(),
                "text buffer must be 1MB so router telemetry (~11.8KB) is accepted");
        assertEquals(1024 * 1024, container.getMaxBinaryMessageBufferSize());

        assertTrue(container.getMaxTextMessageBufferSize() > TOMCAT_DEFAULT_BUFFER,
                "buffer must exceed Tomcat default, otherwise close 1009 drops telemetry");
        assertTrue(container.getMaxTextMessageBufferSize() > LARGE_PAYLOAD_BYTES,
                "buffer must exceed the largest real payload (" + LARGE_PAYLOAD_BYTES + " bytes)");
    }

}
