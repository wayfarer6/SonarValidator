package org.sonar.sonarvalidator_backend.Service.log;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 적재 요청에서 줄 목록을 뽑아내는 <b>입력 해석기</b>입니다.
 *
 * <h2>⚠️ 컨트롤러에서 쪼개기를 걷어낸다</h2>
 * <p>{@code LogController.ingest} 와 {@code upload} 가 각각
 * {@code split("\\R")} 을 갖고 있었습니다. 같은 파일을 붙여넣기와 업로드로
 * 넣었을 때 <b>줄 수가 다르게 나올 수 있는</b> 상태였습니다.
 *
 * <p>지금 컨트롤러는 요청과 파일을 넘기고 {@link LogLines} 를 받습니다.
 * 쪼개기 규칙을 고치려면 {@link LogLineSource#splitLines} 한 곳만 봅니다.
 *
 * <h2>⚠️ 예외를 삼키지 않는다</h2>
 * <p>빈 파일이나 읽기 실패는 <b>잘못된 요청</b>입니다. 조용히 빈 목록을
 * 돌려주면 운영자는 "적재했는데 0건" 을 보고 원인을 알 수 없습니다.
 * 구현체가 던진 {@link IllegalArgumentException} 을 그대로 올려
 * 컨트롤러가 400 으로 변환하게 합니다.
 */
@Service
public class LogLineReader {

    private static final Logger log = LoggerFactory.getLogger(LogLineReader.class);

    private final List<LogLineSource> sources;

    private final LogLineSources.Unsupported unsupported = new LogLineSources.Unsupported();

    /** 기본 전략 목록으로 만듭니다. */
    public LogLineReader() {
        this(LogLineSources.defaults());
    }

    /**
     * 테스트/확장용 생성자입니다.
     *
     * @param sources 순서대로 시도할 입력 전략
     */
    public LogLineReader(List<LogLineSource> sources) {
        this.sources = List.copyOf(sources);
    }

    /**
     * 요청과 파일에서 줄 목록을 뽑습니다.
     *
     * @param body 요청 본문 (파일 경로에서는 null)
     * @param file 업로드 파일 (JSON 경로에서는 null)
     * @return 해석 결과 (출처 + 줄 목록)
     * @throws IllegalArgumentException 빈 파일 등 잘못된 요청
     */
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

    /**
     * 알림/응답에 쓸 출처 이름을 조회합니다.
     *
     * @param body 요청 본문
     * @param file 업로드 파일
     * @return 출처 표기 ({@code manual}/{@code upload})
     */
    public String sourceLabelOf(Map<String, Object> body, MultipartFile file) {
        for (final LogLineSource source : sources) {
            if (source.matches(body, file)) {
                return source.sourceLabel();
            }
        }
        return unsupported.sourceLabel();
    }

    /** @return 등록된 전략 목록 (진단용) */
    public List<LogLineSource> sources() {
        return sources;
    }

    /**
     * 해석 결과입니다.
     *
     * <h2>⚠️ {@code recognized} 가 필요한 이유</h2>
     * <p>줄이 0건인 경우가 두 가지입니다.
     * <ul>
     *   <li>요청 모양은 알겠는데 내용이 비었음 (▸ {@code recognized:true})</li>
     *   <li>요청 모양 자체를 모르겠음 (▸ {@code recognized:false})</li>
     * </ul>
     * 둘을 구분하지 않으면 "로그가 0건" 과 "잘못된 요청" 이 같은 응답이
     * 되어, 운영자가 어느 쪽을 고쳐야 할지 알 수 없습니다.
     *
     * @param source     출처 표기
     * @param lines      줄 목록
     * @param recognized 요청 모양을 해석했는지
     */
    public record LogLines(String source, List<String> lines, boolean recognized) {

        /** @return 줄이 하나도 없는지 */
        public boolean isEmpty() {
            return lines == null || lines.isEmpty();
        }

        /** @return 줄 수 */
        public int count() {
            return lines == null ? 0 : lines.size();
        }

        /** @return 불변 사본 */
        public LogLines immutable() {
            return new LogLines(source, List.copyOf(lines == null ? List.of() : lines), recognized);
        }
    }

    /** @return 해석하지 못했다는 사실을 명시한 빈 결과 */
    public static Optional<LogLines> none() {
        return Optional.empty();
    }
}