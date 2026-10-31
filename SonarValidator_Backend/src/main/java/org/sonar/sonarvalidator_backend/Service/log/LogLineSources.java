package org.sonar.sonarvalidator_backend.Service.log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

final class LogLineSources {

    private LogLineSources() {
    }

    static final class LineArray implements LogLineSource {

        @Override
        public boolean matches(Map<String, Object> body, MultipartFile file) {
            return file == null && body != null && body.get("lines") instanceof List<?>;
        }

        @Override
        public String sourceLabel() {
            return "manual";
        }

        @Override
        public List<String> lines(Map<String, Object> body, MultipartFile file) {
            final List<String> lines = new ArrayList<>();
            for (final Object item : (List<?>) body.get("lines")) {
                if (item != null) {
                    // String.valueOf 를 쓰는 이유: 숫자나 불리언이 섞여 와도
                    // "로그 한 줄" 로 읽는 편이, 그 항목만 버리는 것보다 낫습니다.
                    lines.add(String.valueOf(item));
                }
            }
            return lines;
        }
    }

    static final class TextBlock implements LogLineSource {

        @Override
        public boolean matches(Map<String, Object> body, MultipartFile file) {
            return file == null && body != null
                    && !(body.get("lines") instanceof List<?>)
                    && body.get("text") != null;
        }

        @Override
        public String sourceLabel() {
            return "manual";
        }

        @Override
        public List<String> lines(Map<String, Object> body, MultipartFile file) {
            // ⚠️ 쪼개기는 LogLineSource.splitLines 한 곳만 씁니다.
            //    이전에는 이 로직이 여기와 업로드 경로에 복사돼 있었습니다.
            return LogLineSource.splitLines(String.valueOf(body.get("text")));
        }
    }

    static final class UploadedFile implements LogLineSource {

        @Override
        public boolean matches(Map<String, Object> body, MultipartFile file) {
            return file != null;
        }

        @Override
        public String sourceLabel() {
            return "upload";
        }

        @Override
        public List<String> lines(Map<String, Object> body, MultipartFile file) {
            if (file.isEmpty()) {
                // 컨트롤러가 400 으로 바꾸도록 예외를 던집니다.
                // (빈 파일은 "적재할 것이 없다" 가 아니라 잘못된 요청입니다)
                throw new IllegalArgumentException("빈 파일입니다.");
            }
            try {
                final String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                return LogLineSource.splitLines(content);
            } catch (java.io.IOException ex) {
                throw new IllegalArgumentException("파일을 읽지 못했습니다: " + ex.getMessage());
            }
        }
    }

    static final class Unsupported implements LogLineSource {

        @Override
        public boolean matches(Map<String, Object> body, MultipartFile file) {
            return false;
        }

        @Override
        public String sourceLabel() {
            return "manual";
        }

        @Override
        public List<String> lines(Map<String, Object> body, MultipartFile file) {
            return LogLineSource.none();
        }
    }

    static List<LogLineSource> defaults() {
        return List.of(new UploadedFile(), new LineArray(), new TextBlock());
    }
}
