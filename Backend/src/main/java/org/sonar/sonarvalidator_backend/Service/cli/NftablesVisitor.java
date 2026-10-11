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

/**
 * {@code nft list ruleset} / {@code nft -a list chain ...} 출력을 JSON 으로 옮기는 방문자.
 *
 * <h2>문법이 이미 해 준 일</h2>
 * nft 표기는 {@code ip saddr 1.2.3.0/24} 처럼 <b>두 토큰이 하나의 키</b>를 이룬다.
 * 예전 구현은 문자열 목록을 훑으며 "ip" 다음에 "saddr" 이 오면 합치는 식으로
 * 단어를 비교해야 했다.
 *
 * <p>이제 문법이 그 단어들을 전용 토큰으로 확정해 둔다.
 * <pre>
 *   PREFIX   : ip | ip6 | tcp | udp | ct | meta | icmp | icmpv6
 *   SUBKEY   : saddr | daddr | state | dport | sport | ...
 *   IFACEKEY : iif | oif | iifname | oifname
 *   RULEKEY  : handle | comment | prefix
 *   MODIFIER : counter | log
 *   METRICKEY: packets | bytes
 *   ACTION   : accept | drop | reject | return | jump | goto | ...
 *   HASH     : '#'
 * </pre>
 * 따라서 이 방문자는 {@code rulePiece} 의 <b>토큰 타입</b>만 보고 분기하며,
 * 단어 비교는 전혀 하지 않는다.
 *
 * <h2>출력 계약</h2>
 * {@code tables[].family}, {@code tables[].name},
 * {@code tables[].chains[].name}, {@code tables[].chains[].policy},
 * {@code tables[].chains[].rules[].expression}, {@code …rules[].action}
 *
 * <p>순회는 ANTLR 이 자동 생성한 {@code NftablesRuleBaseVisitor} 를 그대로
 * 씁니다. C++ 기준 구현({@code cli_output_parser.cpp})도 같은 방식으로
 * 생성된 {@code visitChainBlock}/{@code visitChainDocument} 를 덮어씁니다.
 */
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

    /**
     * 테이블 블록 없이 체인만 오는 출력 형태.
     * family/table 을 알 수 없으므로 {@code unknown} 으로 채운다(소비자는 그대로 통과시킨다).
     */
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

    /**
     * {@code type filter hook forward priority filter; policy accept;}
     *
     * <p>한 줄에 {@code key value} 쌍이 세미콜론으로 이어진다.
     * 문법에서 키({@code type}, {@code hook}, {@code priority}, {@code policy},
     * {@code device}, {@code comment})는 모두 {@code ATTRKEY} 토큰이므로,
     * "ATTRKEY 를 만나면 다음 조각이 값" 이라는 규칙만으로 정확히 쌍을 뽑는다.
     */
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

    /**
     * 규칙 조각을 <b>토큰 타입</b>으로 분류한다.
     *
     * <pre>
     *   ip saddr 1.2.3.0/24  counter log prefix "NFT: "  drop
     *   └ PREFIX └ SUBKEY    └ MODIFIER └ MODIFIER └ RULEKEY └ QUOTED  └ ACTION
     * </pre>
     *
     * <p>해석하지 못한 조각도 버리지 않고 {@code modifiers} 에 남긴다(정보 보존).
     */
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

            // ---- handle 주석: `#` `handle` `5` ----
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

            // ---- 동작 ----
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

            // ---- 수식어 ----
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

            // ---- 카운터 값 ----
            if (type == NftablesRuleLexer.METRICKEY
                    && cursor.type(1) == NftablesRuleLexer.NUMBER) {
                rule.put(text, Integer.parseInt(cursor.text(1)));
                cursor.skip(2);
                continue;
            }

            // ---- `ip saddr <값>` : 접두어 + 하위키 ----
            if (type == NftablesRuleLexer.PREFIX
                    && cursor.type(1) == NftablesRuleLexer.SUBKEY
                    && cursor.type(2) != org.antlr.v4.runtime.Token.EOF) {
                String key = text + " " + cursor.text(1);
                String value = CliText.unquote(CliText.trimPunct(cursor.text(2)));
                cursor.skip(3);
                addMatch(matchPairs, matchText, key, value);
                continue;
            }

            // ---- 단독 키 (`iifname "eth0"`, `handle 5`, `comment "..."`) ----
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

            // ---- 그 밖의 조각은 보존 ----
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

    /** 컨텍스트의 직계 자식을 (토큰 타입, 텍스트) 조각 목록으로 평탄화한다. */
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