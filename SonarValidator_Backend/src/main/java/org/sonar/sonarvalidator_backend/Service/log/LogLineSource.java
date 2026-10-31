package org.sonar.sonarvalidator_backend.Service.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

public interface LogLineSource {

    boolean matches(Map<String, Object> body, MultipartFile file);

    String sourceLabel();

    List<String> lines(Map<String, Object> body, MultipartFile file);

    static List<String> splitLines(String content) {
        final List<String> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return lines;
        }
        for (final String line : content.split("\\R")) {
            lines.add(line);
        }
        return lines;
    }

    static List<String> none() {
        return List.of();
    }
}
