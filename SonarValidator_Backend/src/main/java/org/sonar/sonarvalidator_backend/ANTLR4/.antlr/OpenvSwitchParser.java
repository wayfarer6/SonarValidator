// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Backend/src/main/java/org/sonar/sonarvalidator_backend/ANTLR4/OpenvSwitch.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class OpenvSwitchParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, INET=3, IP_ADDR=4, NUMBER=5, ID=6, WS=7, ANY_CHAR=8;
	public static final int
		RULE_result = 0, RULE_item = 1;
	private static String[] makeRuleNames() {
		return new String[] {
			"result", "item"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "':'", "'/'", "'inet'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, "INET", "IP_ADDR", "NUMBER", "ID", "WS", "ANY_CHAR"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "OpenvSwitch.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public OpenvSwitchParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ResultContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(OpenvSwitchParser.EOF, 0); }
		public List<ItemContext> item() {
			return getRuleContexts(ItemContext.class);
		}
		public ItemContext item(int i) {
			return getRuleContext(ItemContext.class,i);
		}
		public ResultContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_result; }
	}

	public final ResultContext result() throws RecognitionException {
		ResultContext _localctx = new ResultContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_result);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(7);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 510L) != 0)) {
				{
				{
				setState(4);
				item();
				}
				}
				setState(9);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(10);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	@SuppressWarnings("CheckReturnValue")
	public static class ItemContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(OpenvSwitchParser.NUMBER, 0); }
		public TerminalNode ID() { return getToken(OpenvSwitchParser.ID, 0); }
		public TerminalNode INET() { return getToken(OpenvSwitchParser.INET, 0); }
		public TerminalNode IP_ADDR() { return getToken(OpenvSwitchParser.IP_ADDR, 0); }
		public ItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_item; }
	}

	public final ItemContext item() throws RecognitionException {
		ItemContext _localctx = new ItemContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_item);
		try {
			setState(23);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,2,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(12);
				match(NUMBER);
				setState(13);
				match(T__0);
				setState(14);
				match(ID);
				setState(15);
				match(T__0);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(16);
				match(INET);
				setState(17);
				match(IP_ADDR);
				setState(20);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,1,_ctx) ) {
				case 1:
					{
					setState(18);
					match(T__1);
					setState(19);
					match(NUMBER);
					}
					break;
				}
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(22);
				matchWildcard();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static final String _serializedATN =
		"\u0004\u0001\b\u001a\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001\u0001"+
		"\u0000\u0005\u0000\u0006\b\u0000\n\u0000\f\u0000\t\t\u0000\u0001\u0000"+
		"\u0001\u0000\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\u0015\b\u0001\u0001\u0001"+
		"\u0003\u0001\u0018\b\u0001\u0001\u0001\u0000\u0000\u0002\u0000\u0002\u0000"+
		"\u0000\u001b\u0000\u0007\u0001\u0000\u0000\u0000\u0002\u0017\u0001\u0000"+
		"\u0000\u0000\u0004\u0006\u0003\u0002\u0001\u0000\u0005\u0004\u0001\u0000"+
		"\u0000\u0000\u0006\t\u0001\u0000\u0000\u0000\u0007\u0005\u0001\u0000\u0000"+
		"\u0000\u0007\b\u0001\u0000\u0000\u0000\b\n\u0001\u0000\u0000\u0000\t\u0007"+
		"\u0001\u0000\u0000\u0000\n\u000b\u0005\u0000\u0000\u0001\u000b\u0001\u0001"+
		"\u0000\u0000\u0000\f\r\u0005\u0005\u0000\u0000\r\u000e\u0005\u0001\u0000"+
		"\u0000\u000e\u000f\u0005\u0006\u0000\u0000\u000f\u0018\u0005\u0001\u0000"+
		"\u0000\u0010\u0011\u0005\u0003\u0000\u0000\u0011\u0014\u0005\u0004\u0000"+
		"\u0000\u0012\u0013\u0005\u0002\u0000\u0000\u0013\u0015\u0005\u0005\u0000"+
		"\u0000\u0014\u0012\u0001\u0000\u0000\u0000\u0014\u0015\u0001\u0000\u0000"+
		"\u0000\u0015\u0018\u0001\u0000\u0000\u0000\u0016\u0018\t\u0000\u0000\u0000"+
		"\u0017\f\u0001\u0000\u0000\u0000\u0017\u0010\u0001\u0000\u0000\u0000\u0017"+
		"\u0016\u0001\u0000\u0000\u0000\u0018\u0003\u0001\u0000\u0000\u0000\u0003"+
		"\u0007\u0014\u0017";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}