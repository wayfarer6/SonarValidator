package org.sonar.sonarvalidator_backend.Service.cli;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliText;

public final class TokenCursor<E extends ParserRuleContext> {

    private final List<E> elems;
    private int index;

    public TokenCursor(List<E> elems) {
        this.elems = elems;
    }

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

    public boolean hasNext() {
        return index < elems.size();
    }

    public int type() {
        return type(0);
    }

    public int type(int ahead) {
        final int at = index + ahead;
        if (at < 0 || at >= elems.size()) {
            return Token.EOF;
        }
        final Token token = elems.get(at).getStart();
        return token == null ? Token.EOF : token.getType();
    }

    public String text() {
        return text(0);
    }

    public String text(int ahead) {
        final int at = index + ahead;
        if (at < 0 || at >= elems.size()) {
            return "";
        }
        return elems.get(at).getText();
    }

    public boolean is(int tokenType) {
        return hasNext() && type() == tokenType;
    }

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

    public boolean accept(int tokenType) {
        if (is(tokenType)) {
            index++;
            return true;
        }
        return false;
    }

    public String take() {
        if (!hasNext()) {
            return "";
        }
        return elems.get(index++).getText();
    }

    public String takeValue() {
        return CliText.trimPunct(take());
    }

    public String peekValue() {
        return CliText.trimPunct(text(1));
    }

    public void skip(int count) {
        index = Math.min(elems.size(), index + Math.max(0, count));
    }

    public List<E> remaining() {
        return new ArrayList<>(elems.subList(Math.min(index, elems.size()), elems.size()));
    }

    public int remainingCount() {
        return elems.size() - index;
    }
}
