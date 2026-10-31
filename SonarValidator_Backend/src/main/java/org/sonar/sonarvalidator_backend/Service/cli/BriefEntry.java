package org.sonar.sonarvalidator_backend.Service.cli;

import java.util.List;

import org.sonar.sonarvalidator_backend.Service.cli.CliJson;
import org.sonar.sonarvalidator_backend.Service.cli.CliText;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

public final class BriefEntry {

    public enum Kind {
        NAME,
        ADDRESS,
        UNASSIGNED,
        METHOD,
        STATUS,
        EXTRA
    }

    public record Piece(Kind kind, String text) {
    }

    private BriefEntry() {
    }

    public static ObjectNode parse(List<Piece> pieces) {
        final ObjectNode entry = CliJson.object();
        if (pieces.isEmpty()) {
            return entry;
        }

        final ArrayNode extras = CliJson.array();

        for (final Piece piece : pieces) {
            final String token = CliText.trimPunct(piece.text());
            if (token.isEmpty() && piece.kind() != Kind.UNASSIGNED) {
                continue;
            }

            switch (piece.kind()) {
                case NAME -> {
                    if (!entry.has("name")) {
                        entry.put("name", token);
                    } else {
                        extras.add(token);
                    }
                }
                case ADDRESS -> {
                    if (!entry.has("ip_address")) {
                        final String[] bare = new String[1];
                        final int[] prefixLen = new int[1];
                        if (CliText.splitCidr(token, bare, prefixLen)) {
                            entry.put("ip_address", bare[0]);
                            if (prefixLen[0] >= 0) {
                                entry.put("prefix_len", prefixLen[0]);
                            }
                        } else {
                            entry.put("ip_address", token);
                        }
                    } else {
                        extras.add(token);
                    }
                }
                case UNASSIGNED -> {
                    entry.put("ip_address", "");
                    entry.put("unassigned", true);
                }
                case METHOD -> entry.put("method", token);
                case STATUS -> {
                    // 첫 상태 단어는 link 상태, 두 번째는 line protocol 상태다.
                    if (!entry.has("status")) {
                        entry.put("status", token);
                    } else if (!entry.has("protocol")) {
                        entry.put("protocol", token);
                    } else {
                        extras.add(token);
                    }
                }
                case EXTRA -> extras.add(token);
            }
        }

        if (!extras.isEmpty()) {
            entry.set("extras", extras);
        }
        return entry;
    }
}
