package org.sonar.sonarvalidator_backend;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class OperationLoggingTest {
    @Autowired Environment environment;

    @Test
    void operationsAreTimestampedBoundedAndSingleLine(CapturedOutput output) {
        final var log = LoggerFactory.getLogger(OperationLoggingTest.class);
        log.info("작업 검증: 설정 저장\n두 번째 줄\t{}", "x".repeat(3000));
        log.error("실패 검증", new IllegalStateException("연결 실패\n다시 시도"));
        final String text = output.getAll();
        final var operation = text.lines().filter(line -> line.contains("작업 검증")).toList();
        assertThat(operation).hasSize(1);
        assertThat(operation.getFirst()).matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(?:Z|[+-]\\d{2}:\\d{2}) INFO .*설정 저장 두 번째 줄 .*$");
        assertThat(operation.getFirst().length()).isLessThan(450);
        assertThat(text).contains("실패 검증", "IllegalStateException", "연결 실패 다시 시도");
        assertThat(text).doesNotContain("\tat org.sonar", "3000", "[main]");
    }

    @Test
    void queryAndBindLogsStayOff(CapturedOutput output) {
        final var sql = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        final var bind = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.hibernate.orm.jdbc.bind");
        assertThat(sql.getEffectiveLevel()).isEqualTo(ch.qos.logback.classic.Level.OFF);
        assertThat(bind.getEffectiveLevel()).isEqualTo(ch.qos.logback.classic.Level.OFF);
        assertThat(environment.getProperty("debug", Boolean.class)).isFalse();
        assertThat(LoggerFactory.getLogger("org.springframework.web.servlet.DispatcherServlet").isDebugEnabled()).isFalse();
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.show_sql", Boolean.class)).isFalse();
        sql.debug("select LOGGING_QUERY_SENTINEL");
        bind.trace("LOGGING_PARAMETER_SENTINEL");
        assertThat(output.getAll()).doesNotContain("LOGGING_QUERY_SENTINEL", "LOGGING_PARAMETER_SENTINEL");
    }
}
