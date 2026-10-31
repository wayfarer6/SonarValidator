package org.sonar.sonarvalidator_backend.Service.log;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class LogLineReader {

    private static final Logger log = LoggerFactory.getLogger(LogLineReader.class);

    private final List<LogLineSource> sources;

    private final LogLineSources.Unsupported unsupported = new LogLineSources.Unsupported();

    public LogLineReader() {
        this(LogLineSources.defaults());
    }

    public LogLineReader(List<LogLineSource> sources) {
        this.sources = List.copyOf(sources);
    }

    public LogLines read(Map<String, Object> body, MultipartFile file) {
        for (final LogLineSource source : sources) {
            if (source.matches(body, file)) {
                final List<String> lines = source.lines(body, file);
                log.debug("log lines read: source={} count={}", source.sourceLabel(), lines.size());
                return new LogLines(source.sourceLabel(), lines, true);
            }
        }
        // 어느 경로에도 맞지 않습니다. 오류가 아니라 "적재할 것이 없음" 입니다.
        return new LogLines(unsupported.sourceLabel(), unsupported.lines(body, file), false);
    }

    public String sourceLabelOf(Map<String, Object> body, MultipartFile file) {
        for (final LogLineSource source : sources) {
            if (source.matches(body, file)) {
                return source.sourceLabel();
            }
        }
        return unsupported.sourceLabel();
    }

    public List<LogLineSource> sources() {
        return sources;
    }

    public record LogLines(String source, List<String> lines, boolean recognized) {

        public boolean isEmpty() {
            return lines == null || lines.isEmpty();
        }

        public int count() {
            return lines == null ? 0 : lines.size();
        }

        public LogLines immutable() {
            return new LogLines(source, List.copyOf(lines == null ? List.of() : lines), recognized);
        }
    }

    public static Optional<LogLines> none() {
        return Optional.empty();
    }
}
