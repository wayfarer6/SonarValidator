package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;
import org.sonar.sonarvalidator_backend.Service.cli.ParseSession;
import org.sonar.sonarvalidator_backend.grammar.NftablesRuleBaseVisitor;
import org.sonar.sonarvalidator_backend.grammar.NftablesRuleLexer;
import org.sonar.sonarvalidator_backend.grammar.NftablesRuleParser;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class NftablesVisitor extends NftablesRuleBaseVisitor<Void> {

    private final ParseSession<NftablesRuleLexer, NftablesRuleParser> session;

    public final ArrayNode tables = CliJson.array();

    private ObjectNode currentTable;
    private ObjectNode currentChain;

    public NftablesVisitor(ParseSession<NftablesRuleLexer, NftablesRuleParser> session) {
        this.session = session;
    }

    /* ==================== nft list ruleset ==================== */

    @Override
    public Void visitRulesetItem(NftablesRuleParser.RulesetItemContext ctx) {
        if (ctx.tableBlock() != null) {
            visitTableBlock(ctx.tableBlock());
        }
        return null;
    }

    @Override
    public Void visitTableBlock(NftablesRuleParser.TableBlockContext ctx) {
        ObjectNode table = CliJson.object();
        table.put("family", ctx.familyBlock() == null ? "" : ctx.familyBlock().getText());
        table.put("name", ctx.tableName() == null ? "" : ctx.tableName().getText());
        table.putArray("chains");
        tables.add(table);

        currentTable = table;
        currentChain = null;

        // 자식(bodyBlock → chainBlock) 순회는 ANTLR 에 맡긴다.
        visitChildren(ctx);

        currentTable = null;
        currentChain = null;
        return null;
    }

    @Override
    public Void visitChainBlock(NftablesRuleParser.ChainBlockContext ctx) {
        if (currentTable == null) {
            return null;
        }
        ObjectNode chain = CliJson.object();
        chain.put("name", ctx.chainName() == null ? "" : ctx.chainName().getText());
        chain.putArray("rules");
        currentTable.withArray("chains").add(chain);
        currentChain = chain;

        // 자식(chainBody → chainAttr / ruleLine) 순회도 ANTLR 에 맡긴다.
        visitChildren(ctx);
        return null;
    }

    @Override
    public Void visitChainAttr(NftablesRuleParser.ChainAttrContext ctx) {
        if (currentChain != null) {
            applyChainAttr(ctx, currentChain);
        }
        return null;
    }

    @Override
    public Void visitRuleLine(NftablesRuleParser.RuleLineContext ctx) {
        if (currentChain != null) {
            applyRuleLine(ctx, currentChain);
        }
        return null;
    }

    /* ============= nft -a list chain <family> <table> <chain> ============= */

    @Override
    public Void visitChainDocument(NftablesRuleParser.ChainDocumentContext ctx) {
        ObjectNode table = CliJson.object();
        table.put("family", "unknown");
        table.put("name", "unknown");
        table.putArray("chains");
        tables.add(table);
        currentTable = table;
        currentChain = null;

        // chainItem(→ chainAttr / ruleLine) 순회는 ANTLR 에 맡긴다.
        visitChildren(ctx);
        return null;
    }

    /* ==================== 체인 속성 ==================== */

    public void applyChainAttr(NftablesRuleParser.ChainAttrContext ctx, ObjectNode chain) {
        List<Piece> pieces = piecesOf(ctx);

        for (int i = 0; i < pieces.size(); i++) {
            Piece key = pieces.get(i);
            if (key.type() != NftablesRuleLexer.ATTRKEY) {
                continue;
            }
            if (i + 1 >= pieces.size()) {
                break;
            }
            Piece value = pieces.get(i + 1);
            chain.put(key.text(), CliText.unquote(CliText.trimPunct(value.text())));
            i++;
        }
    }

    /* ==================== 규칙 ==================== */

    private void applyRuleLine(NftablesRuleParser.RuleLineContext ctx, ObjectNode chain) {
        ObjectNode rule = CliJson.object();
        String raw = CliText.trim(session.lineOf(ctx));
        rule.put("raw", raw);

        // `# handle 5` — 삭제/조회에 필요하므로 있으면 정수로 뽑는다.
        // 문법이 HASH / RULEKEY(handle) / NUMBER 를 구분해 주므로 토큰으로 처리한다.
        applyRulePieces(ctx, rule);

        if (!rule.has("handle")) {
            int handlePos = raw.lastIndexOf("# handle ");
            if (handlePos >= 0) {
                String handle = CliText.trim(raw.substring(handlePos + 9));
                if (CliText.isNumber(handle)) {
                    rule.put("handle", Integer.parseInt(handle));
                }
            }
        }

        // 소비자(AbstractDeviceConfigParser)는 `expression` + `action` 문자열을
        // `... chain X <expression> <action>` 로 조립한다.
        if (!rule.has("expression")) {
            String match = rule.has("match") ? rule.path("match").asString("") : "";
            rule.put("expression", match.isEmpty() ? raw : match);
        }

        chain.withArray("rules").add(rule);
    }

    private void applyRulePieces(NftablesRuleParser.RuleLineContext ctx, ObjectNode rule) {
        TokenCursor<NftablesRuleParser.RulePieceContext> cursor =
                TokenCursor.of(ctx, NftablesRuleParser.RulePieceContext.class);

        ArrayNode modifiers = CliJson.array();
        ObjectNode matchPairs = CliJson.object();
        StringBuilder matchText = new StringBuilder();
        boolean counterSeen = false;

        while (cursor.hasNext()) {
            int type = cursor.type();
            String text = cursor.text();

            if (type == NftablesRuleLexer.HASH) {
                cursor.skip(1);
                if (cursor.is(NftablesRuleLexer.RULEKEY)
                        && "handle".equals(cursor.text())
                        && cursor.type(1) == NftablesRuleLexer.NUMBER) {
                    rule.put("handle", Integer.parseInt(cursor.text(1)));
                    cursor.skip(2);
                }
                continue;
            }

            if (type == NftablesRuleLexer.ACTION) {
                String action = CliText.trimPunct(text);
                rule.put("action", action);
                cursor.skip(1);
                // jump/goto 는 대상 체인명을 함께 기록한다.
                if (("jump".equals(action) || "goto".equals(action)) && cursor.hasNext()) {
                    rule.put("jump_target", CliText.unquote(CliText.trimPunct(cursor.take())));
                }
                continue;
            }

            if (type == NftablesRuleLexer.MODIFIER) {
                if ("counter".equals(text)) {
                    counterSeen = true;
                    modifiers.add(text);
                    cursor.skip(1);
                    continue;
                }
                // log — 뒤에 `prefix "..."` 가 붙으면 로그 접두어로 기록한다.
                modifiers.add(text);
                cursor.skip(1);
                if (cursor.is(NftablesRuleLexer.RULEKEY) && "prefix".equals(cursor.text())) {
                    cursor.skip(1);
                    if (cursor.is(NftablesRuleLexer.QUOTED)) {
                        rule.put("log_prefix", CliText.unquote(cursor.take()));
                    }
                }
                continue;
            }

            if (type == NftablesRuleLexer.METRICKEY
                    && cursor.type(1) == NftablesRuleLexer.NUMBER) {
                rule.put(text, Integer.parseInt(cursor.text(1)));
                cursor.skip(2);
                continue;
            }

            if (type == NftablesRuleLexer.PREFIX
                    && cursor.type(1) == NftablesRuleLexer.SUBKEY
                    && cursor.type(2) != org.antlr.v4.runtime.Token.EOF) {
                String key = text + " " + cursor.text(1);
                String value = CliText.unquote(CliText.trimPunct(cursor.text(2)));
                cursor.skip(3);
                addMatch(matchPairs, matchText, key, value);
                continue;
            }

            if ((type == NftablesRuleLexer.IFACEKEY
                    || type == NftablesRuleLexer.SUBKEY
                    || type == NftablesRuleLexer.RULEKEY)
                    && cursor.type(1) != org.antlr.v4.runtime.Token.EOF) {
                String key = CliText.trimPunct(text);
                String value = CliText.unquote(CliText.trimPunct(cursor.text(1)));
                cursor.skip(2);
                addMatch(matchPairs, matchText, key, value);
                continue;
            }

            modifiers.add(CliText.trimPunct(text));
            cursor.skip(1);
        }

        if (matchPairs.size() > 0) {
            rule.set("match_pairs", matchPairs);
        }
        if (matchText.length() > 0) {
            rule.put("match", matchText.toString());
        }
        if (modifiers.size() > 0) {
            rule.set("modifiers", modifiers);
        }
        rule.put("counter", counterSeen);
    }

    private static void addMatch(ObjectNode pairs, StringBuilder text,
                                 String key, String value) {
        pairs.put(key, value);
        if (text.length() > 0) {
            text.append(' ');
        }
        text.append(key).append(' ').append(value);
    }

    /* ==================== 도우미 ==================== */

    private static List<Piece> piecesOf(org.antlr.v4.runtime.ParserRuleContext ctx) {
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode terminal) {
                pieces.add(new Piece(terminal.getSymbol().getType(), terminal.getText()));
            } else if (child instanceof NftablesRuleParser.ElemContext elem) {
                pieces.add(new Piece(elem.getStart().getType(), elem.getText()));
            }
        }
        return pieces;
    }

    private record Piece(int type, String text) {
    }
}
