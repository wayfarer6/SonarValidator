package org.sonar.sonarvalidator_backend.Service.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

/**
 * 로그 적재 요청에서 <b>줄 목록</b>을 뽑아냅니다.
 *
 * <h2>⚠️ 왜 전략 패턴인가 — 같은 쪼개기가 두 벗에 있었다</h2>
 * <p>적재 경로가 세 가지인데, 각각이 "무엇을 줄로 볼 것인가" 를 스스로
 * 정하고 있었습니다.
 *
 * <pre>
 *   POST /ingest  {"lines": [...]}   → 배열을 그대로
 *   POST /ingest  {"text": "..."}    → split("\\R")  ← 여기
 *   POST /upload  파일               → split("\\R")  ← 그리고 여기
 * </pre>
 *
 * <p>{@code split("\\R")} 이 두 벗에 복사되어 있었습니다. 한쪽만 바꾸면
 * <b>같은 파일을 붙여넣기와 업로드로 넣었을 때 줄 수가 다르게</b> 나옵니다.
 * 게다가 {@code \R} 은 빈 줄을 남기므로, 어느 경로로 넣었는지에 따라
 * 로그 개수가 달라집니다.
 *
 * <p>지금은 쪼개기가 {@link PlainTextLineSource} 한 곳에 있고, 세 경로가
 * 그 구현을 함께 씁니다.
 *
 * <h2>⚠️ 빈 줄을 버리지 않는다</h2>
 * <p>장비 로그에서 빈 줄은 구분자 역할을 합니다. 버리면 "설정 블록 A" 와
 * "설정 블록 B" 가 붙어 한 덩어리로 보입니다. 대신 <b>개수</b>는 정확히
 * 보고합니다 — 운영자가 "왜 100줄을 넣었는데 80줄인가" 를 알 수 있어야 합니다.
 */
public interface LogLineSource {

    /**
     * 이 요청 모양을 처리할 수 있는지 판단합니다.
     *
     * @param body 요청 본문 (null 허용 — 파일 업로드 경로)
     * @param file 업로드된 파일 (null 허용 — JSON 경로)
     * @return 처리 가능하면 true
     */
    boolean matches(Map<String, Object> body, MultipartFile file);

    /** @return 로그 출처 표기 ({@code manual}/{@code upload} 등) */
    String sourceLabel();

    /**
     * 줄 목록을 뽑습니다.
     *
     * @param body 요청 본문 (null 허용)
     * @param file 업로드 파일 (null 허용)
     * @return 줄 목록 (null 아님)
     * @throws IllegalArgumentException 읽을 수 없을 때 (컨트롤러가 400 으로 변환)
     */
    List<String> lines(Map<String, Object> body, MultipartFile file);

    /**
     * 원문 텍스트를 줄로 쪼갭니다.
     *
     * <h2>⚠️ 여기에 모아 둔 이유</h2>
     * <p>{@code \\R} (모든 개행 문자) 을 쓰는 규칙과 "첫 줄이 비면 버린다"
     * 같은 미세한 처리가 경로마다 달라지지 않게 합니다.
     *
     * @param content 원문 (null 이면 빈 목록)
     * @return 줄 목록
     */
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

    /**
     * 알 수 없는 요청 모양일 때 쓰는 빈 결과입니다.
     *
     * <p>예외를 던지지 않는 이유: 적재할 것이 없다는 사실은 오류가
     * 아닙니다. 호출자가 {@code accepted:false} 로 응답하고 사유를
     * 설명하는 편이 화면에 유리합니다.
     *
     * @return 빈 목록 (불변)
     */
    static List<String> none() {
        return List.of();
    }
}