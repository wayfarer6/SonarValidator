// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/OvsTopology.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue", "this-escape"})
public class OvsTopologyLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		NEWLINE=1, WS=2, BRIDGE=3, PORT=4, INTERFACE=5, FLOWTOKEN=6, ATTRWORD=7, 
		QUOTED=8, DASHES=9, WORD=10, COLON=11;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"NEWLINE", "WS", "BRIDGE", "PORT", "INTERFACE", "FLOWTOKEN", "ATTRWORD", 
			"QUOTED", "DASHES", "WORD", "COLON"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, "'Bridge'", "'Port'", "'Interface'", null, null, null, 
			null, null, "':'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "NEWLINE", "WS", "BRIDGE", "PORT", "INTERFACE", "FLOWTOKEN", "ATTRWORD", 
			"QUOTED", "DASHES", "WORD", "COLON"
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


	public OvsTopologyLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "OvsTopology.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\u0004\u0000\u000b`\u0006\uffff\uffff\u0002\u0000\u0007\u0000\u0002\u0001"+
		"\u0007\u0001\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004"+
		"\u0007\u0004\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007"+
		"\u0007\u0007\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0001\u0000"+
		"\u0003\u0000\u0019\b\u0000\u0001\u0000\u0001\u0000\u0001\u0001\u0004\u0001"+
		"\u001e\b\u0001\u000b\u0001\f\u0001\u001f\u0001\u0001\u0001\u0001\u0001"+
		"\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001\u0002\u0001"+
		"\u0002\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001\u0003\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001"+
		"\u0004\u0001\u0004\u0001\u0004\u0001\u0004\u0001\u0005\u0004\u0005;\b"+
		"\u0005\u000b\u0005\f\u0005<\u0001\u0005\u0001\u0005\u0004\u0005A\b\u0005"+
		"\u000b\u0005\f\u0005B\u0001\u0006\u0001\u0006\u0005\u0006G\b\u0006\n\u0006"+
		"\f\u0006J\t\u0006\u0001\u0007\u0001\u0007\u0005\u0007N\b\u0007\n\u0007"+
		"\f\u0007Q\t\u0007\u0001\u0007\u0001\u0007\u0001\b\u0004\bV\b\b\u000b\b"+
		"\f\bW\u0001\t\u0004\t[\b\t\u000b\t\f\t\\\u0001\n\u0001\n\u0000\u0000\u000b"+
		"\u0001\u0001\u0003\u0002\u0005\u0003\u0007\u0004\t\u0005\u000b\u0006\r"+
		"\u0007\u000f\b\u0011\t\u0013\n\u0015\u000b\u0001\u0000\u0006\u0002\u0000"+
		"\t\t  \u0003\u0000AZ__az\u0005\u0000\t\n\r\r  ,,^^\u0005\u0000-.09AZ_"+
		"_az\u0003\u0000\n\n\r\r\"\"\u0004\u0000\t\n\r\r  ::g\u0000\u0001\u0001"+
		"\u0000\u0000\u0000\u0000\u0003\u0001\u0000\u0000\u0000\u0000\u0005\u0001"+
		"\u0000\u0000\u0000\u0000\u0007\u0001\u0000\u0000\u0000\u0000\t\u0001\u0000"+
		"\u0000\u0000\u0000\u000b\u0001\u0000\u0000\u0000\u0000\r\u0001\u0000\u0000"+
		"\u0000\u0000\u000f\u0001\u0000\u0000\u0000\u0000\u0011\u0001\u0000\u0000"+
		"\u0000\u0000\u0013\u0001\u0000\u0000\u0000\u0000\u0015\u0001\u0000\u0000"+
		"\u0000\u0001\u0018\u0001\u0000\u0000\u0000\u0003\u001d\u0001\u0000\u0000"+
		"\u0000\u0005#\u0001\u0000\u0000\u0000\u0007*\u0001\u0000\u0000\u0000\t"+
		"/\u0001\u0000\u0000\u0000\u000b:\u0001\u0000\u0000\u0000\rD\u0001\u0000"+
		"\u0000\u0000\u000fK\u0001\u0000\u0000\u0000\u0011U\u0001\u0000\u0000\u0000"+
		"\u0013Z\u0001\u0000\u0000\u0000\u0015^\u0001\u0000\u0000\u0000\u0017\u0019"+
		"\u0005\r\u0000\u0000\u0018\u0017\u0001\u0000\u0000\u0000\u0018\u0019\u0001"+
		"\u0000\u0000\u0000\u0019\u001a\u0001\u0000\u0000\u0000\u001a\u001b\u0005"+
		"\n\u0000\u0000\u001b\u0002\u0001\u0000\u0000\u0000\u001c\u001e\u0007\u0000"+
		"\u0000\u0000\u001d\u001c\u0001\u0000\u0000\u0000\u001e\u001f\u0001\u0000"+
		"\u0000\u0000\u001f\u001d\u0001\u0000\u0000\u0000\u001f \u0001\u0000\u0000"+
		"\u0000 !\u0001\u0000\u0000\u0000!\"\u0006\u0001\u0000\u0000\"\u0004\u0001"+
		"\u0000\u0000\u0000#$\u0005B\u0000\u0000$%\u0005r\u0000\u0000%&\u0005i"+
		"\u0000\u0000&\'\u0005d\u0000\u0000\'(\u0005g\u0000\u0000()\u0005e\u0000"+
		"\u0000)\u0006\u0001\u0000\u0000\u0000*+\u0005P\u0000\u0000+,\u0005o\u0000"+
		"\u0000,-\u0005r\u0000\u0000-.\u0005t\u0000\u0000.\b\u0001\u0000\u0000"+
		"\u0000/0\u0005I\u0000\u000001\u0005n\u0000\u000012\u0005t\u0000\u0000"+
		"23\u0005e\u0000\u000034\u0005r\u0000\u000045\u0005f\u0000\u000056\u0005"+
		"a\u0000\u000067\u0005c\u0000\u000078\u0005e\u0000\u00008\n\u0001\u0000"+
		"\u0000\u00009;\u0007\u0001\u0000\u0000:9\u0001\u0000\u0000\u0000;<\u0001"+
		"\u0000\u0000\u0000<:\u0001\u0000\u0000\u0000<=\u0001\u0000\u0000\u0000"+
		"=>\u0001\u0000\u0000\u0000>@\u0005=\u0000\u0000?A\u0007\u0002\u0000\u0000"+
		"@?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000B@\u0001\u0000\u0000"+
		"\u0000BC\u0001\u0000\u0000\u0000C\f\u0001\u0000\u0000\u0000DH\u0007\u0001"+
		"\u0000\u0000EG\u0007\u0003\u0000\u0000FE\u0001\u0000\u0000\u0000GJ\u0001"+
		"\u0000\u0000\u0000HF\u0001\u0000\u0000\u0000HI\u0001\u0000\u0000\u0000"+
		"I\u000e\u0001\u0000\u0000\u0000JH\u0001\u0000\u0000\u0000KO\u0005\"\u0000"+
		"\u0000LN\b\u0004\u0000\u0000ML\u0001\u0000\u0000\u0000NQ\u0001\u0000\u0000"+
		"\u0000OM\u0001\u0000\u0000\u0000OP\u0001\u0000\u0000\u0000PR\u0001\u0000"+
		"\u0000\u0000QO\u0001\u0000\u0000\u0000RS\u0005\"\u0000\u0000S\u0010\u0001"+
		"\u0000\u0000\u0000TV\u0005-\u0000\u0000UT\u0001\u0000\u0000\u0000VW\u0001"+
		"\u0000\u0000\u0000WU\u0001\u0000\u0000\u0000WX\u0001\u0000\u0000\u0000"+
		"X\u0012\u0001\u0000\u0000\u0000Y[\b\u0005\u0000\u0000ZY\u0001\u0000\u0000"+
		"\u0000[\\\u0001\u0000\u0000\u0000\\Z\u0001\u0000\u0000\u0000\\]\u0001"+
		"\u0000\u0000\u0000]\u0014\u0001\u0000\u0000\u0000^_\u0005:\u0000\u0000"+
		"_\u0016\u0001\u0000\u0000\u0000\t\u0000\u0018\u001f<BHOW\\\u0001\u0006"+
		"\u0000\u0000";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}