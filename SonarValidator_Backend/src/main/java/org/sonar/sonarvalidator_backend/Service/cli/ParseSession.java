package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

/**
 * 하나의 CLI 출력에 대한 lexer/token stream/parser/오류 리스너의 수명을 묶습니다.
 *
 * <h2>왜 줄 단위 정보가 필요한가</h2>
 * <p>문법은 공백({@code WS})을 skip 합니다. 그래서 {@code ctx.getText()} 에는
 * 공백이 사라지지만, <b>토큰의 줄 번호와 열 위치는 그대로 보존</b>됩니다. 이 점을
 * 이용해 "줄 종류 판정은 문법으로, 필드 값은 원문 라인으로" 처리합니다.
 *
 * <h2>들여쓰기로 계층 복원</h2>
 * <p>OpenVSwitch 문법은 들여쓰기를 강제하지 않습니다(WS skip). 대신
 * {@link #indentOf} 로 각 줄 선두 토큰의 열 위치를 읽어 bridge/port/interface
 * 계층을 복원합니다.
 *
 * @param <L> lexer 타입
 * @param <P> parser 타입
 */
public class ParseSession<L extends Lexer, P extends Parser> {

    /** 오류 메시지에 담을 최대 개수. (로그 폭주 방지) */
    private static final int MAX_REPORTED = 5;

    private final String normalized;
    private final List<String> lines;

    private final L lexer;
    private final P parser;
    private final CollectingErrorListener listener = new CollectingErrorListener();

    /**
     * 파싱 세션을 만듭니다. lexer/parser 를 만들어 오류 리스너를 교체합니다.
     *
     * @param lexerFactory  입력 스트림으로 lexer 를 만드는 팩터리
     * @param parserFactory 토큰 스트림으로 parser 를 만드는 팩터리
     * @param raw           원문 CLI 출력
     */
    public ParseSession(LexerFactory<L> lexerFactory,
                        ParserFactory<P> parserFactory,
                        String raw) {
        this.normalized = normalize(raw);
        // 원문 라인을 1-기준으로 접근하기 위해 맨 앞에 빈 줄을 둔다.
        // (ANTLR 줄 번호는 1부터 시작한다)
        this.lines = splitLines("\n" + normalized);

        final L createdLexer = lexerFactory.create(CharStreams.fromString(normalized));
        createdLexer.removeErrorListeners();
        createdLexer.addErrorListener(listener);
        this.lexer = createdLexer;

        final CommonTokenStream tokens = new CommonTokenStream(this.lexer);
        final P createdParser = parserFactory.create(tokens);
        createdParser.removeErrorListeners();
        createdParser.addErrorListener(listener);
        this.parser = createdParser;
    }

    /**
     * 파서를 돌려줍니다. (진입 규칙 호출용)
     *
     * @return parser
     */
    public P parser() {
        return parser;
    }

    /**
     * 문법 오류 개수입니다.
     *
     * @return 오류 개수
     */
    public int errorCount() {
        return listener.errorCount;
    }

    /**
     * 누적된 오류 메시지입니다.
     *
     * @return 오류 메시지 (없으면 빈 문자열)
     */
    public String errorMessage() {
        return listener.message.toString();
    }

    /**
     * 컨텍스트가 시작하는 <b>원문 라인</b>을 그대로 돌려줍니다.
     *
     * @param ctx 파서 컨텍스트 (null 허용)
     * @return 원문 라인 (없으면 빈 문자열)
     */
    public String lineOf(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return "";
        }
        final int line = ctx.getStart().getLine();
        if (line <= 0 || line >= lines.size()) {
            return "";
        }
        return lines.get(line);
    }

    /**
     * 컨텍스트가 시작하는 원문 라인의 토큰 목록을 돌려줍니다.
     *
     * @param ctx 파서 컨텍스트
     * @return 토큰 목록 (null 이 아님)
     */
    public List<String> tokensOf(ParserRuleContext ctx) {
        return CliText.splitTokens(lineOf(ctx));
    }

    /**
     * 줄 선두 토큰의 열 위치 = 들여쓰기 깊이입니다.
     *
     * <p>공백을 skip 해도 위치 정보는 보존되므로 계층 복원에 쓸 수 있습니다.
     *
     * @param ctx 파서 컨텍스트
     * @return 들여쓰기 열 번호
     */
    public int indentOf(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return 0;
        }
        return ctx.getStart().getCharPositionInLine();
    }

    /**
     * CLI 출력은 마지막 개행이 없을 수 있습니다. 문법이 {@code NEWLINE} 을
     * 요구하므로 보정하고, CR({@code \r})도 제거합니다.
     *
     * @param raw 원문
     * @return 정규화된 텍스트
     */
    private static String normalize(String raw) {
        final String text = (raw == null ? "" : raw).replace("\r", "");
        if (text.isEmpty() || text.charAt(text.length() - 1) != '\n') {
            return text + "\n";
        }
        return text;
    }

    private static List<String> splitLines(String text) {
        final List<String> result = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                result.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            result.add(text.substring(start));
        }
        return result;
    }

    /**
     * 문법 오류를 모으는 리스너입니다. <b>예외를 던지지 않습니다.</b>
     */
    private static final class CollectingErrorListener extends BaseErrorListener {

        private int errorCount;
        private final StringBuilder message = new StringBuilder();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer,
                                Object offendingSymbol,
                                int line,
                                int charPositionInLine,
                                String msg,
                                RecognitionException e) {
            errorCount++;
            if (errorCount <= MAX_REPORTED) {
                if (message.length() > 0) {
                    message.append("; ");
                }
                message.append("line ").append(line).append(':')
                        .append(charPositionInLine).append(' ').append(msg);
            }
        }
    }

    /**
     * lexer 생성 팩터리입니다.
     *
     * @param <L> lexer 타입
     */
    @FunctionalInterface
    public interface LexerFactory<L extends Lexer> {
        /**
         * lexer 를 만듭니다.
         *
         * @param input 문자 스트림
         * @return lexer
         */
        L create(org.antlr.v4.runtime.CharStream input);
    }

    /**
     * parser 생성 팩터리입니다.
     *
     * @param <P> parser 타입
     */
    @FunctionalInterface
    public interface ParserFactory<P extends Parser> {
        /**
         * parser 를 만듭니다.
         *
         * @param tokens 토큰 스트림
         * @return parser
         */
        P create(org.antlr.v4.runtime.TokenStream tokens);
    }

    /**
     * 디버그용: 토큰 종류 목록을 문자열로 돌려줍니다.
     *
     * @param ctx 파서 컨텍스트
     * @return {@code type:text} 를 공백으로 이은 문자열
     */
    public String tokenDebug(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        final org.antlr.v4.runtime.TokenStream stream = parser.getTokenStream();
        for (int i = ctx.getStart().getTokenIndex();
                i <= ctx.getStop().getTokenIndex() && i < stream.size(); i++) {
            final Token token = stream.get(i);
            if (token.getType() == Token.EOF) {
                break;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(token.getType()).append(':').append(token.getText());
        }
        return sb.toString();
    }
}