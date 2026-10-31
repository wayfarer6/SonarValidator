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

public class ParseSession<L extends Lexer, P extends Parser> {

    private static final int MAX_REPORTED = 5;

    private final String normalized;
    private final List<String> lines;

    private final L lexer;
    private final P parser;
    private final CollectingErrorListener listener = new CollectingErrorListener();

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

    public P parser() {
        return parser;
    }

    public int errorCount() {
        return listener.errorCount;
    }

    public String errorMessage() {
        return listener.message.toString();
    }

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

    public List<String> tokensOf(ParserRuleContext ctx) {
        return CliText.splitTokens(lineOf(ctx));
    }

    public int indentOf(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return 0;
        }
        return ctx.getStart().getCharPositionInLine();
    }

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

    @FunctionalInterface
    public interface LexerFactory<L extends Lexer> {
        L create(org.antlr.v4.runtime.CharStream input);
    }

    @FunctionalInterface
    public interface ParserFactory<P extends Parser> {
        P create(org.antlr.v4.runtime.TokenStream tokens);
    }

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
