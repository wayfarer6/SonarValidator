package org.sonar.sonarvalidator_backend.Service.cli;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * CLI 파서가 쓰는 JSON 생성/마감 헬퍼입니다.
 *
 * <h2>실패 계약</h2>
 * <p>수집 경로에서 예외는 곧 텔레메트리 유실입니다. 그래서 파서는 <b>절대 예외를
 * 던지지 않고</b>, 실패하면 {@link #failure} 로 만든
 * {@code {"parsed": false, "parse_error": ..., "raw": ...}} 를 돌려줍니다.
 * 부분적으로라도 뽑은 정보는 버리지 않고 {@code parsed:false} 만 달아 반환합니다.
 */
public final class CliJson {

    /** Jackson 3 노드 팩터리. */
    public static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private CliJson() {
    }

    /**
     * 빈 객체 노드를 만듭니다.
     *
     * @return 새 객체 노드
     */
    public static ObjectNode object() {
        return JSON.objectNode();
    }

    /**
     * 빈 배열 노드를 만듭니다.
     *
     * @return 새 배열 노드
     */
    public static ArrayNode array() {
        return JSON.arrayNode();
    }

    /**
     * 파싱 품질 정보를 결과에 덧붙입니다.
     *
     * <p>문법 오류가 하나도 없을 때만 {@code parsed:true} 입니다. 오류가 있으면
     * 이미 뽑은 정보는 그대로 두고 {@code parse_warnings}/{@code parse_error} 를
     * 더합니다.
     *
     * @param body    결과 본문
     * @param session 파싱 세션 (오류 수/메시지 제공)
     * @return 같은 본문 (체이닝 편의)
     */
    public static ObjectNode attachParseInfo(ObjectNode body, ParseSession<?, ?> session) {
        body.put("parsed", session.errorCount() == 0);
        if (session.errorCount() > 0) {
            body.put("parse_warnings", session.errorCount());
            body.put("parse_error", session.errorMessage());
        }
        return body;
    }

    /**
     * 파싱 실패 결과를 만듭니다.
     *
     * @param raw   원문 (진단용)
     * @param error 오류 메시지
     * @return 실패 노드
     */
    public static ObjectNode failure(String raw, String error) {
        final ObjectNode failure = object();
        failure.put("parsed", false);
        failure.put("parse_error", error == null ? "unknown error" : error);
        failure.put("raw", raw == null ? "" : raw);
        return failure;
    }
}