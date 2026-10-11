package org.sonar.sonarvalidator_backend.Service.log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

/**
 * 로그 적재 줄 추출 전략 3개를 한 파일에 모았습니다.
 *
 * <h2>⚠️ 순서가 곧 우선순위다</h2>
 * <p>{@link LogLineSources} 가 <b>앞에서부터</b> 첫 매칭을 씁니다.
 * {@link LineArray} 와 {@link TextBlock} 은 같은 JSON 본문을 받으므로,
 * 판단 조건이 겹치지 않게 갈라 두었습니다.
 * <pre>
 *   LineArray.matches  → body.get("lines") instanceof List
 *   TextBlock.matches  → body.get("lines") 가 List 가 아니고 text 가 있음
 * </pre>
 *
 * <h2>⚠️ 출처 표기가 여기 있는 이유</h2>
 * <p>{@code "manual"} / {@code "upload"} 는 로그 저장 시 남는 값이고,
 * 화면에서 "어디서 들어온 로그인가" 를 보여줍니다. 컨트롤러가 각 경로에서
 * 문자열을 하드코딩하면 오타가 나도 컴파일이 통과합니다.
 */
final class LogLineSources {

    private LogLineSources() {
    }

    /**
     * {@code {"lines": ["...", "..."]}} — 이미 줄 단위로 온 요청입니다.
     *
     * <p>가장 정확한 경로입니다. 호출자가 줄 경계를 알고 있으므로 서버가
     * 다시 쪼개지 않습니다. 원문에 개행이 들어 있어도 한 줄로 취급합니다 —
     * 에이전트가 보낸 구조를 존중하는 편이 안전합니다.
     */
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

    /**
     * {@code {"text": "여러\n줄"}} — 한 덩어리로 온 요청입니다.
     *
     * <p>운영자가 콘솔에서 붙여넣을 때 쓰는 경로입니다. 줄 경계가 없으므로
     * 서버가 {@code \\R} 로 쪼갭니다.
     */
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

    /**
     * 멀티파트 파일 업로드입니다.
     *
     * <p>장비에 접속할 수 없는 환경에서 로그 파일만 가져온 경우입니다.
     * 인코딩은 UTF-8 로 고정합니다 — 장비 로그에 한글이 섞여 있을 수 있고,
     * 플랫폼 기본 인코딩에 기대면 서버 OS 에 따라 깨집니다.
     */
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

    /**
     * 어느 전략에도 해당하지 않을 때의 안전망입니다.
     *
     * <p>빈 목록을 돌려주고 {@code accepted:false} 로 이어집니다.
     * {@link #matches} 가 항상 {@code false} 인 이유: 이 전략이 이름으로
     * 선택되면 다른 경로가 가려집니다.
     */
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

    /** 등록 순서대로 시도할 기본 목록입니다. */
    static List<LogLineSource> defaults() {
        return List.of(new UploadedFile(), new LineArray(), new TextBlock());
    }
}