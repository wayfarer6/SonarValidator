package org.sonar.sonarvalidator_backend.Service.cli;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliText;

/**
 * {@code elem} 목록을 <b>토큰 타입</b> 기준으로 훑는 커서입니다.
 *
 * <h2>왜 필요한가</h2>
 * <p>다섯 문법 모두 형식이 가변적인 꼬리 부분을 {@code elem : ~NEWLINE} 으로
 * 위임합니다. 즉 <b>{@code elem} 하나 = 토큰 하나</b>이므로
 * {@code elem.getStart().getType()} 로 그 조각의 종류를 알 수 있습니다.
 *
 * <p>이 성질 덕분에 visitor 는 문자열을 눈으로 훑을 필요가 없습니다.
 * <pre>
 *   // 나쁜 예 — 텍스트를 보고 추측한다
 *   if (token.startsWith("link/")) ...
 *   if (token.equals("via") || token.equals("dev")) ...
 *
 *   // 좋은 예 — 문법이 붙여 준 타입만 본다
 *   if (cursor.is(LINK)) ...
 *   switch (cursor.type()) { case VIA: ... case DEV: ... }
 * </pre>
 *
 * <p>문법에 키워드 토큰을 선언해 두면 그 판별이 파서 생성 시점에 끝나므로,
 * 런타임에는 분기만 남습니다.
 *
 * @param <E> 각 문법의 {@code ElemContext} 타입
 */
public final class TokenCursor<E extends ParserRuleContext> {

    private final List<E> elems;
    private int index;

    /**
     * @param elems 훑을 elem 목록 (원문 순서)
     */
    public TokenCursor(List<E> elems) {
        this.elems = elems;
    }

    /**
     * 컨텍스트의 직계 자식 중 {@code elem} 만 순서대로 모아 커서를 만듭니다.
     *
     * @param ctx   부모 컨텍스트
     * @param clazz elem 컨텍스트 클래스
     * @param <E>   elem 컨텍스트 타입
     * @return 커서
     */
    public static <E extends ParserRuleContext> TokenCursor<E> of(ParserRuleContext ctx,
                                                                  Class<E> clazz) {
        final List<E> found = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (clazz.isInstance(child)) {
                found.add(clazz.cast(child));
            }
        }
        return new TokenCursor<>(found);
    }

    /**
     * 남은 조각이 있는지 확인합니다.
     *
     * @return 남았으면 참
     */
    public boolean hasNext() {
        return index < elems.size();
    }

    /**
     * 현재 조각의 토큰 타입입니다.
     *
     * @return 토큰 타입 상수
     */
    public int type() {
        return type(0);
    }

    /**
     * 앞으로 {@code ahead} 칸 뒤 조각의 토큰 타입입니다.
     *
     * @param ahead 0 이면 현재
     * @return 토큰 타입 (범위를 벗어나면 {@link Token#EOF})
     */
    public int type(int ahead) {
        final int at = index + ahead;
        if (at < 0 || at >= elems.size()) {
            return Token.EOF;
        }
        final Token token = elems.get(at).getStart();
        return token == null ? Token.EOF : token.getType();
    }

    /**
     * 현재 조각의 원문 텍스트입니다.
     *
     * @return 텍스트
     */
    public String text() {
        return text(0);
    }

    /**
     * 앞으로 {@code ahead} 칸 뒤 조각의 원문 텍스트입니다.
     *
     * @param ahead 0 이면 현재
     * @return 텍스트 (범위를 벗어나면 빈 문자열)
     */
    public String text(int ahead) {
        final int at = index + ahead;
        if (at < 0 || at >= elems.size()) {
            return "";
        }
        return elems.get(at).getText();
    }

    /**
     * 현재 조각이 주어진 타입인지 확인합니다.
     *
     * @param tokenType 토큰 타입 상수
     * @return 일치 여부
     */
    public boolean is(int tokenType) {
        return hasNext() && type() == tokenType;
    }

    /**
     * 현재 조각이 여러 타입 중 하나인지 확인합니다.
     *
     * @param tokenTypes 토큰 타입 상수들
     * @return 일치 여부
     */
    public boolean isAny(int... tokenTypes) {
        if (!hasNext()) {
            return false;
        }
        final int current = type();
        for (final int candidate : tokenTypes) {
            if (current == candidate) {
                return true;
            }
        }
        return false;
    }

    /**
     * 현재 조각이 주어진 타입이면 소비합니다.
     *
     * @param tokenType 토큰 타입 상수
     * @return 소비했으면 참
     */
    public boolean accept(int tokenType) {
        if (is(tokenType)) {
            index++;
            return true;
        }
        return false;
    }

    /**
     * 현재 조각을 소비하고 텍스트를 돌려줍니다.
     *
     * @return 텍스트 (남은 조각이 없으면 빈 문자열)
     */
    public String take() {
        if (!hasNext()) {
            return "";
        }
        return elems.get(index++).getText();
    }

    /**
     * 현재 조각을 소비하고 구두점을 뗀 값을 돌려줍니다.
     *
     * <p>{@code eth0,} 처럼 값에 쉼표가 붙어 오는 경우가 있어 값 정리만 합니다.
     * (종류 판별이 아니라 표기 정리이므로 문법을 건드릴 필요는 없습니다.)
     *
     * @return 값
     */
    public String takeValue() {
        return CliText.trimPunct(take());
    }

    /**
     * 다음 조각을 <b>소비하지 않고</b> 구두점만 뗀 값으로 돌려줍니다.
     *
     * @return 다음 값 (없으면 빈 문자열)
     */
    public String peekValue() {
        return CliText.trimPunct(text(1));
    }

    /**
     * 현재 위치를 건너뜁니다.
     *
     * @param count 건너뛸 조각 수
     */
    public void skip(int count) {
        index = Math.min(elems.size(), index + Math.max(0, count));
    }

    /**
     * 아직 소비하지 않은 조각들을 순서대로 돌려줍니다.
     *
     * @return 남은 elem 목록
     */
    public List<E> remaining() {
        return new ArrayList<>(elems.subList(Math.min(index, elems.size()), elems.size()));
    }

    /**
     * 남은 조각 수입니다.
     *
     * @return 남은 개수
     */
    public int remainingCount() {
        return elems.size() - index;
    }
}