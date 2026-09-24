// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast", "CheckReturnValue"})
public class SwitchTopologyParser extends Parser {
	static { RuntimeMetaData.checkVersion("4.13.1", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		NEWLINE=1, WS=2, VLAN=3, INTERFACE=4, ADDR=5, NUMBER=6, PORTNAME=7, UNASSIGNED=8, 
		DASHES=9, STATUSWORD=10, IFNAME=11, ATTRWORD=12, IDENT=13, COMMA=14, COLON=15, 
		SLASH=16, LPAREN=17, RPAREN=18;
	public static final int
		RULE_runningDocument = 0, RULE_vlanDocument = 1, RULE_briefDocument = 2, 
		RULE_portDocument = 3, RULE_configLine = 4, RULE_vlanItem = 5, RULE_vlanHeader = 6, 
		RULE_vlanEntry = 7, RULE_vlanSeparator = 8, RULE_vlanId = 9, RULE_vlanName = 10, 
		RULE_vlanStatus = 11, RULE_portList = 12, RULE_portToken = 13, RULE_nameToken = 14, 
		RULE_briefItem = 15, RULE_briefHeader = 16, RULE_briefEntry = 17, RULE_addrOrUnassigned = 18, 
		RULE_briefField = 19, RULE_portItem = 20, RULE_portEntry = 21, RULE_ifname = 22, 
		RULE_keyWord = 23, RULE_genericLine = 24, RULE_blank = 25, RULE_elem = 26;
	private static String[] makeRuleNames() {
		return new String[] {
			"runningDocument", "vlanDocument", "briefDocument", "portDocument", "configLine", 
			"vlanItem", "vlanHeader", "vlanEntry", "vlanSeparator", "vlanId", "vlanName", 
			"vlanStatus", "portList", "portToken", "nameToken", "briefItem", "briefHeader", 
			"briefEntry", "addrOrUnassigned", "briefField", "portItem", "portEntry", 
			"ifname", "keyWord", "genericLine", "blank", "elem"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, null, null, "'VLAN'", null, null, null, null, "'unassigned'", null, 
			null, null, null, null, "','", "':'", "'/'", "'('", "')'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, "NEWLINE", "WS", "VLAN", "INTERFACE", "ADDR", "NUMBER", "PORTNAME", 
			"UNASSIGNED", "DASHES", "STATUSWORD", "IFNAME", "ATTRWORD", "IDENT", 
			"COMMA", "COLON", "SLASH", "LPAREN", "RPAREN"
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
	public String getGrammarFileName() { return "SwitchTopology.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public SwitchTopologyParser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@SuppressWarnings("CheckReturnValue")
	public static class RunningDocumentContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SwitchTopologyParser.EOF, 0); }
		public List<ConfigLineContext> configLine() {
			return getRuleContexts(ConfigLineContext.class);
		}
		public ConfigLineContext configLine(int i) {
			return getRuleContext(ConfigLineContext.class,i);
		}
		public RunningDocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_runningDocument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterRunningDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitRunningDocument(this);
		}
	}

	public final RunningDocumentContext runningDocument() throws RecognitionException {
		RunningDocumentContext _localctx = new RunningDocumentContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_runningDocument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(57);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(54);
				configLine();
				}
				}
				setState(59);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(60);
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
	public static class VlanDocumentContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SwitchTopologyParser.EOF, 0); }
		public List<VlanItemContext> vlanItem() {
			return getRuleContexts(VlanItemContext.class);
		}
		public VlanItemContext vlanItem(int i) {
			return getRuleContext(VlanItemContext.class,i);
		}
		public VlanDocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanDocument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanDocument(this);
		}
	}

	public final VlanDocumentContext vlanDocument() throws RecognitionException {
		VlanDocumentContext _localctx = new VlanDocumentContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_vlanDocument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(65);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524286L) != 0)) {
				{
				{
				setState(62);
				vlanItem();
				}
				}
				setState(67);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(68);
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
	public static class BriefDocumentContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SwitchTopologyParser.EOF, 0); }
		public List<BriefItemContext> briefItem() {
			return getRuleContexts(BriefItemContext.class);
		}
		public BriefItemContext briefItem(int i) {
			return getRuleContext(BriefItemContext.class,i);
		}
		public BriefDocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_briefDocument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBriefDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBriefDocument(this);
		}
	}

	public final BriefDocumentContext briefDocument() throws RecognitionException {
		BriefDocumentContext _localctx = new BriefDocumentContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_briefDocument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(73);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524286L) != 0)) {
				{
				{
				setState(70);
				briefItem();
				}
				}
				setState(75);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(76);
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
	public static class PortDocumentContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(SwitchTopologyParser.EOF, 0); }
		public List<PortItemContext> portItem() {
			return getRuleContexts(PortItemContext.class);
		}
		public PortItemContext portItem(int i) {
			return getRuleContext(PortItemContext.class,i);
		}
		public PortDocumentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_portDocument; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterPortDocument(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitPortDocument(this);
		}
	}

	public final PortDocumentContext portDocument() throws RecognitionException {
		PortDocumentContext _localctx = new PortDocumentContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_portDocument);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(81);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524286L) != 0)) {
				{
				{
				setState(78);
				portItem();
				}
				}
				setState(83);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(84);
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
	public static class ConfigLineContext extends ParserRuleContext {
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public ConfigLineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_configLine; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterConfigLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitConfigLine(this);
		}
	}

	public final ConfigLineContext configLine() throws RecognitionException {
		ConfigLineContext _localctx = new ConfigLineContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_configLine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(87); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(86);
				elem();
				}
				}
				setState(89); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0) );
			setState(91);
			match(NEWLINE);
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
	public static class VlanItemContext extends ParserRuleContext {
		public VlanHeaderContext vlanHeader() {
			return getRuleContext(VlanHeaderContext.class,0);
		}
		public VlanEntryContext vlanEntry() {
			return getRuleContext(VlanEntryContext.class,0);
		}
		public VlanSeparatorContext vlanSeparator() {
			return getRuleContext(VlanSeparatorContext.class,0);
		}
		public GenericLineContext genericLine() {
			return getRuleContext(GenericLineContext.class,0);
		}
		public BlankContext blank() {
			return getRuleContext(BlankContext.class,0);
		}
		public VlanItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanItem(this);
		}
	}

	public final VlanItemContext vlanItem() throws RecognitionException {
		VlanItemContext _localctx = new VlanItemContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_vlanItem);
		try {
			setState(98);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(93);
				vlanHeader();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(94);
				vlanEntry();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(95);
				vlanSeparator();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(96);
				genericLine();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(97);
				blank();
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

	@SuppressWarnings("CheckReturnValue")
	public static class VlanHeaderContext extends ParserRuleContext {
		public TerminalNode VLAN() { return getToken(SwitchTopologyParser.VLAN, 0); }
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public VlanHeaderContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanHeader; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanHeader(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanHeader(this);
		}
	}

	public final VlanHeaderContext vlanHeader() throws RecognitionException {
		VlanHeaderContext _localctx = new VlanHeaderContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_vlanHeader);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(100);
			match(VLAN);
			setState(104);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(101);
				elem();
				}
				}
				setState(106);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(107);
			match(NEWLINE);
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
	public static class VlanEntryContext extends ParserRuleContext {
		public VlanIdContext vlanId() {
			return getRuleContext(VlanIdContext.class,0);
		}
		public VlanNameContext vlanName() {
			return getRuleContext(VlanNameContext.class,0);
		}
		public VlanStatusContext vlanStatus() {
			return getRuleContext(VlanStatusContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public PortListContext portList() {
			return getRuleContext(PortListContext.class,0);
		}
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public VlanEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanEntry(this);
		}
	}

	public final VlanEntryContext vlanEntry() throws RecognitionException {
		VlanEntryContext _localctx = new VlanEntryContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_vlanEntry);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(109);
			vlanId();
			setState(110);
			vlanName();
			setState(111);
			vlanStatus();
			setState(113);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				{
				setState(112);
				portList();
				}
				break;
			}
			setState(118);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(115);
				elem();
				}
				}
				setState(120);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(121);
			match(NEWLINE);
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
	public static class VlanSeparatorContext extends ParserRuleContext {
		public TerminalNode DASHES() { return getToken(SwitchTopologyParser.DASHES, 0); }
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public VlanSeparatorContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanSeparator; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanSeparator(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanSeparator(this);
		}
	}

	public final VlanSeparatorContext vlanSeparator() throws RecognitionException {
		VlanSeparatorContext _localctx = new VlanSeparatorContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_vlanSeparator);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(123);
			match(DASHES);
			setState(127);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(124);
				elem();
				}
				}
				setState(129);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(130);
			match(NEWLINE);
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
	public static class VlanIdContext extends ParserRuleContext {
		public TerminalNode NUMBER() { return getToken(SwitchTopologyParser.NUMBER, 0); }
		public VlanIdContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanId; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanId(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanId(this);
		}
	}

	public final VlanIdContext vlanId() throws RecognitionException {
		VlanIdContext _localctx = new VlanIdContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_vlanId);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(132);
			match(NUMBER);
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
	public static class VlanNameContext extends ParserRuleContext {
		public NameTokenContext nameToken() {
			return getRuleContext(NameTokenContext.class,0);
		}
		public VlanNameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanName; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanName(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanName(this);
		}
	}

	public final VlanNameContext vlanName() throws RecognitionException {
		VlanNameContext _localctx = new VlanNameContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_vlanName);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(134);
			nameToken();
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
	public static class VlanStatusContext extends ParserRuleContext {
		public TerminalNode STATUSWORD() { return getToken(SwitchTopologyParser.STATUSWORD, 0); }
		public VlanStatusContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vlanStatus; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterVlanStatus(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitVlanStatus(this);
		}
	}

	public final VlanStatusContext vlanStatus() throws RecognitionException {
		VlanStatusContext _localctx = new VlanStatusContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_vlanStatus);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(136);
			match(STATUSWORD);
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
	public static class PortListContext extends ParserRuleContext {
		public List<PortTokenContext> portToken() {
			return getRuleContexts(PortTokenContext.class);
		}
		public PortTokenContext portToken(int i) {
			return getRuleContext(PortTokenContext.class,i);
		}
		public List<TerminalNode> COMMA() { return getTokens(SwitchTopologyParser.COMMA); }
		public TerminalNode COMMA(int i) {
			return getToken(SwitchTopologyParser.COMMA, i);
		}
		public PortListContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_portList; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterPortList(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitPortList(this);
		}
	}

	public final PortListContext portList() throws RecognitionException {
		PortListContext _localctx = new PortListContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_portList);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(138);
			portToken();
			setState(143);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(139);
					match(COMMA);
					setState(140);
					portToken();
					}
					} 
				}
				setState(145);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,10,_ctx);
			}
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
	public static class PortTokenContext extends ParserRuleContext {
		public TerminalNode PORTNAME() { return getToken(SwitchTopologyParser.PORTNAME, 0); }
		public TerminalNode IFNAME() { return getToken(SwitchTopologyParser.IFNAME, 0); }
		public TerminalNode IDENT() { return getToken(SwitchTopologyParser.IDENT, 0); }
		public TerminalNode NUMBER() { return getToken(SwitchTopologyParser.NUMBER, 0); }
		public PortTokenContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_portToken; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterPortToken(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitPortToken(this);
		}
	}

	public final PortTokenContext portToken() throws RecognitionException {
		PortTokenContext _localctx = new PortTokenContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_portToken);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(146);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 10432L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
	public static class NameTokenContext extends ParserRuleContext {
		public TerminalNode IDENT() { return getToken(SwitchTopologyParser.IDENT, 0); }
		public TerminalNode IFNAME() { return getToken(SwitchTopologyParser.IFNAME, 0); }
		public TerminalNode PORTNAME() { return getToken(SwitchTopologyParser.PORTNAME, 0); }
		public TerminalNode ATTRWORD() { return getToken(SwitchTopologyParser.ATTRWORD, 0); }
		public TerminalNode NUMBER() { return getToken(SwitchTopologyParser.NUMBER, 0); }
		public NameTokenContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_nameToken; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterNameToken(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitNameToken(this);
		}
	}

	public final NameTokenContext nameToken() throws RecognitionException {
		NameTokenContext _localctx = new NameTokenContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_nameToken);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(148);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 14528L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
	public static class BriefItemContext extends ParserRuleContext {
		public BriefHeaderContext briefHeader() {
			return getRuleContext(BriefHeaderContext.class,0);
		}
		public BriefEntryContext briefEntry() {
			return getRuleContext(BriefEntryContext.class,0);
		}
		public GenericLineContext genericLine() {
			return getRuleContext(GenericLineContext.class,0);
		}
		public BlankContext blank() {
			return getRuleContext(BlankContext.class,0);
		}
		public BriefItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_briefItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBriefItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBriefItem(this);
		}
	}

	public final BriefItemContext briefItem() throws RecognitionException {
		BriefItemContext _localctx = new BriefItemContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_briefItem);
		try {
			setState(154);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(150);
				briefHeader();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(151);
				briefEntry();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(152);
				genericLine();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(153);
				blank();
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

	@SuppressWarnings("CheckReturnValue")
	public static class BriefHeaderContext extends ParserRuleContext {
		public TerminalNode INTERFACE() { return getToken(SwitchTopologyParser.INTERFACE, 0); }
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public BriefHeaderContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_briefHeader; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBriefHeader(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBriefHeader(this);
		}
	}

	public final BriefHeaderContext briefHeader() throws RecognitionException {
		BriefHeaderContext _localctx = new BriefHeaderContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_briefHeader);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(156);
			match(INTERFACE);
			setState(160);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(157);
				elem();
				}
				}
				setState(162);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(163);
			match(NEWLINE);
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
	public static class BriefEntryContext extends ParserRuleContext {
		public IfnameContext ifname() {
			return getRuleContext(IfnameContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public AddrOrUnassignedContext addrOrUnassigned() {
			return getRuleContext(AddrOrUnassignedContext.class,0);
		}
		public List<BriefFieldContext> briefField() {
			return getRuleContexts(BriefFieldContext.class);
		}
		public BriefFieldContext briefField(int i) {
			return getRuleContext(BriefFieldContext.class,i);
		}
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public BriefEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_briefEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBriefEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBriefEntry(this);
		}
	}

	public final BriefEntryContext briefEntry() throws RecognitionException {
		BriefEntryContext _localctx = new BriefEntryContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_briefEntry);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(165);
			ifname();
			setState(167);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
			case 1:
				{
				setState(166);
				addrOrUnassigned();
				}
				break;
			}
			setState(172);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,14,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(169);
					briefField();
					}
					} 
				}
				setState(174);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,14,_ctx);
			}
			setState(178);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
				{
				{
				setState(175);
				elem();
				}
				}
				setState(180);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(181);
			match(NEWLINE);
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
	public static class AddrOrUnassignedContext extends ParserRuleContext {
		public TerminalNode ADDR() { return getToken(SwitchTopologyParser.ADDR, 0); }
		public TerminalNode UNASSIGNED() { return getToken(SwitchTopologyParser.UNASSIGNED, 0); }
		public AddrOrUnassignedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_addrOrUnassigned; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterAddrOrUnassigned(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitAddrOrUnassigned(this);
		}
	}

	public final AddrOrUnassignedContext addrOrUnassigned() throws RecognitionException {
		AddrOrUnassignedContext _localctx = new AddrOrUnassignedContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_addrOrUnassigned);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(183);
			_la = _input.LA(1);
			if ( !(_la==ADDR || _la==UNASSIGNED) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
	public static class BriefFieldContext extends ParserRuleContext {
		public TerminalNode STATUSWORD() { return getToken(SwitchTopologyParser.STATUSWORD, 0); }
		public BriefFieldContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_briefField; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBriefField(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBriefField(this);
		}
	}

	public final BriefFieldContext briefField() throws RecognitionException {
		BriefFieldContext _localctx = new BriefFieldContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_briefField);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(185);
			match(STATUSWORD);
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
	public static class PortItemContext extends ParserRuleContext {
		public PortEntryContext portEntry() {
			return getRuleContext(PortEntryContext.class,0);
		}
		public GenericLineContext genericLine() {
			return getRuleContext(GenericLineContext.class,0);
		}
		public BlankContext blank() {
			return getRuleContext(BlankContext.class,0);
		}
		public PortItemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_portItem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterPortItem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitPortItem(this);
		}
	}

	public final PortItemContext portItem() throws RecognitionException {
		PortItemContext _localctx = new PortItemContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_portItem);
		try {
			setState(190);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(187);
				portEntry();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(188);
				genericLine();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(189);
				blank();
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

	@SuppressWarnings("CheckReturnValue")
	public static class PortEntryContext extends ParserRuleContext {
		public TerminalNode INTERFACE() { return getToken(SwitchTopologyParser.INTERFACE, 0); }
		public IfnameContext ifname() {
			return getRuleContext(IfnameContext.class,0);
		}
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public TerminalNode COLON() { return getToken(SwitchTopologyParser.COLON, 0); }
		public List<KeyWordContext> keyWord() {
			return getRuleContexts(KeyWordContext.class);
		}
		public KeyWordContext keyWord(int i) {
			return getRuleContext(KeyWordContext.class,i);
		}
		public PortEntryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_portEntry; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterPortEntry(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitPortEntry(this);
		}
	}

	public final PortEntryContext portEntry() throws RecognitionException {
		PortEntryContext _localctx = new PortEntryContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_portEntry);
		int _la;
		try {
			setState(216);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,20,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(192);
				match(INTERFACE);
				setState(193);
				ifname();
				setState(197);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
					{
					{
					setState(194);
					elem();
					}
					}
					setState(199);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(200);
				match(NEWLINE);
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(203); 
				_errHandler.sync(this);
				_la = _input.LA(1);
				do {
					{
					{
					setState(202);
					keyWord();
					}
					}
					setState(205); 
					_errHandler.sync(this);
					_la = _input.LA(1);
				} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 15448L) != 0) );
				setState(207);
				match(COLON);
				setState(211);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while ((((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0)) {
					{
					{
					setState(208);
					elem();
					}
					}
					setState(213);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				setState(214);
				match(NEWLINE);
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

	@SuppressWarnings("CheckReturnValue")
	public static class IfnameContext extends ParserRuleContext {
		public TerminalNode IFNAME() { return getToken(SwitchTopologyParser.IFNAME, 0); }
		public TerminalNode ATTRWORD() { return getToken(SwitchTopologyParser.ATTRWORD, 0); }
		public TerminalNode PORTNAME() { return getToken(SwitchTopologyParser.PORTNAME, 0); }
		public TerminalNode IDENT() { return getToken(SwitchTopologyParser.IDENT, 0); }
		public IfnameContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_ifname; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterIfname(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitIfname(this);
		}
	}

	public final IfnameContext ifname() throws RecognitionException {
		IfnameContext _localctx = new IfnameContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_ifname);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(218);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 14464L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
	public static class KeyWordContext extends ParserRuleContext {
		public TerminalNode ATTRWORD() { return getToken(SwitchTopologyParser.ATTRWORD, 0); }
		public TerminalNode IFNAME() { return getToken(SwitchTopologyParser.IFNAME, 0); }
		public TerminalNode IDENT() { return getToken(SwitchTopologyParser.IDENT, 0); }
		public TerminalNode VLAN() { return getToken(SwitchTopologyParser.VLAN, 0); }
		public TerminalNode INTERFACE() { return getToken(SwitchTopologyParser.INTERFACE, 0); }
		public TerminalNode STATUSWORD() { return getToken(SwitchTopologyParser.STATUSWORD, 0); }
		public TerminalNode NUMBER() { return getToken(SwitchTopologyParser.NUMBER, 0); }
		public KeyWordContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_keyWord; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterKeyWord(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitKeyWord(this);
		}
	}

	public final KeyWordContext keyWord() throws RecognitionException {
		KeyWordContext _localctx = new KeyWordContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_keyWord);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(220);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & 15448L) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
	public static class GenericLineContext extends ParserRuleContext {
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public List<ElemContext> elem() {
			return getRuleContexts(ElemContext.class);
		}
		public ElemContext elem(int i) {
			return getRuleContext(ElemContext.class,i);
		}
		public GenericLineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_genericLine; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterGenericLine(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitGenericLine(this);
		}
	}

	public final GenericLineContext genericLine() throws RecognitionException {
		GenericLineContext _localctx = new GenericLineContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_genericLine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(223); 
			_errHandler.sync(this);
			_la = _input.LA(1);
			do {
				{
				{
				setState(222);
				elem();
				}
				}
				setState(225); 
				_errHandler.sync(this);
				_la = _input.LA(1);
			} while ( (((_la) & ~0x3f) == 0 && ((1L << _la) & 524284L) != 0) );
			setState(227);
			match(NEWLINE);
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
	public static class BlankContext extends ParserRuleContext {
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public BlankContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_blank; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterBlank(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitBlank(this);
		}
	}

	public final BlankContext blank() throws RecognitionException {
		BlankContext _localctx = new BlankContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_blank);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(229);
			match(NEWLINE);
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
	public static class ElemContext extends ParserRuleContext {
		public TerminalNode NEWLINE() { return getToken(SwitchTopologyParser.NEWLINE, 0); }
		public ElemContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_elem; }
		@Override
		public void enterRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).enterElem(this);
		}
		@Override
		public void exitRule(ParseTreeListener listener) {
			if ( listener instanceof SwitchTopologyListener ) ((SwitchTopologyListener)listener).exitElem(this);
		}
	}

	public final ElemContext elem() throws RecognitionException {
		ElemContext _localctx = new ElemContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_elem);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(231);
			_la = _input.LA(1);
			if ( _la <= 0 || (_la==NEWLINE) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
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
		"\u0004\u0001\u0012\u00ea\u0002\u0000\u0007\u0000\u0002\u0001\u0007\u0001"+
		"\u0002\u0002\u0007\u0002\u0002\u0003\u0007\u0003\u0002\u0004\u0007\u0004"+
		"\u0002\u0005\u0007\u0005\u0002\u0006\u0007\u0006\u0002\u0007\u0007\u0007"+
		"\u0002\b\u0007\b\u0002\t\u0007\t\u0002\n\u0007\n\u0002\u000b\u0007\u000b"+
		"\u0002\f\u0007\f\u0002\r\u0007\r\u0002\u000e\u0007\u000e\u0002\u000f\u0007"+
		"\u000f\u0002\u0010\u0007\u0010\u0002\u0011\u0007\u0011\u0002\u0012\u0007"+
		"\u0012\u0002\u0013\u0007\u0013\u0002\u0014\u0007\u0014\u0002\u0015\u0007"+
		"\u0015\u0002\u0016\u0007\u0016\u0002\u0017\u0007\u0017\u0002\u0018\u0007"+
		"\u0018\u0002\u0019\u0007\u0019\u0002\u001a\u0007\u001a\u0001\u0000\u0005"+
		"\u00008\b\u0000\n\u0000\f\u0000;\t\u0000\u0001\u0000\u0001\u0000\u0001"+
		"\u0001\u0005\u0001@\b\u0001\n\u0001\f\u0001C\t\u0001\u0001\u0001\u0001"+
		"\u0001\u0001\u0002\u0005\u0002H\b\u0002\n\u0002\f\u0002K\t\u0002\u0001"+
		"\u0002\u0001\u0002\u0001\u0003\u0005\u0003P\b\u0003\n\u0003\f\u0003S\t"+
		"\u0003\u0001\u0003\u0001\u0003\u0001\u0004\u0004\u0004X\b\u0004\u000b"+
		"\u0004\f\u0004Y\u0001\u0004\u0001\u0004\u0001\u0005\u0001\u0005\u0001"+
		"\u0005\u0001\u0005\u0001\u0005\u0003\u0005c\b\u0005\u0001\u0006\u0001"+
		"\u0006\u0005\u0006g\b\u0006\n\u0006\f\u0006j\t\u0006\u0001\u0006\u0001"+
		"\u0006\u0001\u0007\u0001\u0007\u0001\u0007\u0001\u0007\u0003\u0007r\b"+
		"\u0007\u0001\u0007\u0005\u0007u\b\u0007\n\u0007\f\u0007x\t\u0007\u0001"+
		"\u0007\u0001\u0007\u0001\b\u0001\b\u0005\b~\b\b\n\b\f\b\u0081\t\b\u0001"+
		"\b\u0001\b\u0001\t\u0001\t\u0001\n\u0001\n\u0001\u000b\u0001\u000b\u0001"+
		"\f\u0001\f\u0001\f\u0005\f\u008e\b\f\n\f\f\f\u0091\t\f\u0001\r\u0001\r"+
		"\u0001\u000e\u0001\u000e\u0001\u000f\u0001\u000f\u0001\u000f\u0001\u000f"+
		"\u0003\u000f\u009b\b\u000f\u0001\u0010\u0001\u0010\u0005\u0010\u009f\b"+
		"\u0010\n\u0010\f\u0010\u00a2\t\u0010\u0001\u0010\u0001\u0010\u0001\u0011"+
		"\u0001\u0011\u0003\u0011\u00a8\b\u0011\u0001\u0011\u0005\u0011\u00ab\b"+
		"\u0011\n\u0011\f\u0011\u00ae\t\u0011\u0001\u0011\u0005\u0011\u00b1\b\u0011"+
		"\n\u0011\f\u0011\u00b4\t\u0011\u0001\u0011\u0001\u0011\u0001\u0012\u0001"+
		"\u0012\u0001\u0013\u0001\u0013\u0001\u0014\u0001\u0014\u0001\u0014\u0003"+
		"\u0014\u00bf\b\u0014\u0001\u0015\u0001\u0015\u0001\u0015\u0005\u0015\u00c4"+
		"\b\u0015\n\u0015\f\u0015\u00c7\t\u0015\u0001\u0015\u0001\u0015\u0001\u0015"+
		"\u0004\u0015\u00cc\b\u0015\u000b\u0015\f\u0015\u00cd\u0001\u0015\u0001"+
		"\u0015\u0005\u0015\u00d2\b\u0015\n\u0015\f\u0015\u00d5\t\u0015\u0001\u0015"+
		"\u0001\u0015\u0003\u0015\u00d9\b\u0015\u0001\u0016\u0001\u0016\u0001\u0017"+
		"\u0001\u0017\u0001\u0018\u0004\u0018\u00e0\b\u0018\u000b\u0018\f\u0018"+
		"\u00e1\u0001\u0018\u0001\u0018\u0001\u0019\u0001\u0019\u0001\u001a\u0001"+
		"\u001a\u0001\u001a\u0000\u0000\u001b\u0000\u0002\u0004\u0006\b\n\f\u000e"+
		"\u0010\u0012\u0014\u0016\u0018\u001a\u001c\u001e \"$&(*,.024\u0000\u0006"+
		"\u0003\u0000\u0006\u0007\u000b\u000b\r\r\u0002\u0000\u0006\u0007\u000b"+
		"\r\u0002\u0000\u0005\u0005\b\b\u0002\u0000\u0007\u0007\u000b\r\u0003\u0000"+
		"\u0003\u0004\u0006\u0006\n\r\u0001\u0000\u0001\u0001\u00ea\u00009\u0001"+
		"\u0000\u0000\u0000\u0002A\u0001\u0000\u0000\u0000\u0004I\u0001\u0000\u0000"+
		"\u0000\u0006Q\u0001\u0000\u0000\u0000\bW\u0001\u0000\u0000\u0000\nb\u0001"+
		"\u0000\u0000\u0000\fd\u0001\u0000\u0000\u0000\u000em\u0001\u0000\u0000"+
		"\u0000\u0010{\u0001\u0000\u0000\u0000\u0012\u0084\u0001\u0000\u0000\u0000"+
		"\u0014\u0086\u0001\u0000\u0000\u0000\u0016\u0088\u0001\u0000\u0000\u0000"+
		"\u0018\u008a\u0001\u0000\u0000\u0000\u001a\u0092\u0001\u0000\u0000\u0000"+
		"\u001c\u0094\u0001\u0000\u0000\u0000\u001e\u009a\u0001\u0000\u0000\u0000"+
		" \u009c\u0001\u0000\u0000\u0000\"\u00a5\u0001\u0000\u0000\u0000$\u00b7"+
		"\u0001\u0000\u0000\u0000&\u00b9\u0001\u0000\u0000\u0000(\u00be\u0001\u0000"+
		"\u0000\u0000*\u00d8\u0001\u0000\u0000\u0000,\u00da\u0001\u0000\u0000\u0000"+
		".\u00dc\u0001\u0000\u0000\u00000\u00df\u0001\u0000\u0000\u00002\u00e5"+
		"\u0001\u0000\u0000\u00004\u00e7\u0001\u0000\u0000\u000068\u0003\b\u0004"+
		"\u000076\u0001\u0000\u0000\u00008;\u0001\u0000\u0000\u000097\u0001\u0000"+
		"\u0000\u00009:\u0001\u0000\u0000\u0000:<\u0001\u0000\u0000\u0000;9\u0001"+
		"\u0000\u0000\u0000<=\u0005\u0000\u0000\u0001=\u0001\u0001\u0000\u0000"+
		"\u0000>@\u0003\n\u0005\u0000?>\u0001\u0000\u0000\u0000@C\u0001\u0000\u0000"+
		"\u0000A?\u0001\u0000\u0000\u0000AB\u0001\u0000\u0000\u0000BD\u0001\u0000"+
		"\u0000\u0000CA\u0001\u0000\u0000\u0000DE\u0005\u0000\u0000\u0001E\u0003"+
		"\u0001\u0000\u0000\u0000FH\u0003\u001e\u000f\u0000GF\u0001\u0000\u0000"+
		"\u0000HK\u0001\u0000\u0000\u0000IG\u0001\u0000\u0000\u0000IJ\u0001\u0000"+
		"\u0000\u0000JL\u0001\u0000\u0000\u0000KI\u0001\u0000\u0000\u0000LM\u0005"+
		"\u0000\u0000\u0001M\u0005\u0001\u0000\u0000\u0000NP\u0003(\u0014\u0000"+
		"ON\u0001\u0000\u0000\u0000PS\u0001\u0000\u0000\u0000QO\u0001\u0000\u0000"+
		"\u0000QR\u0001\u0000\u0000\u0000RT\u0001\u0000\u0000\u0000SQ\u0001\u0000"+
		"\u0000\u0000TU\u0005\u0000\u0000\u0001U\u0007\u0001\u0000\u0000\u0000"+
		"VX\u00034\u001a\u0000WV\u0001\u0000\u0000\u0000XY\u0001\u0000\u0000\u0000"+
		"YW\u0001\u0000\u0000\u0000YZ\u0001\u0000\u0000\u0000Z[\u0001\u0000\u0000"+
		"\u0000[\\\u0005\u0001\u0000\u0000\\\t\u0001\u0000\u0000\u0000]c\u0003"+
		"\f\u0006\u0000^c\u0003\u000e\u0007\u0000_c\u0003\u0010\b\u0000`c\u0003"+
		"0\u0018\u0000ac\u00032\u0019\u0000b]\u0001\u0000\u0000\u0000b^\u0001\u0000"+
		"\u0000\u0000b_\u0001\u0000\u0000\u0000b`\u0001\u0000\u0000\u0000ba\u0001"+
		"\u0000\u0000\u0000c\u000b\u0001\u0000\u0000\u0000dh\u0005\u0003\u0000"+
		"\u0000eg\u00034\u001a\u0000fe\u0001\u0000\u0000\u0000gj\u0001\u0000\u0000"+
		"\u0000hf\u0001\u0000\u0000\u0000hi\u0001\u0000\u0000\u0000ik\u0001\u0000"+
		"\u0000\u0000jh\u0001\u0000\u0000\u0000kl\u0005\u0001\u0000\u0000l\r\u0001"+
		"\u0000\u0000\u0000mn\u0003\u0012\t\u0000no\u0003\u0014\n\u0000oq\u0003"+
		"\u0016\u000b\u0000pr\u0003\u0018\f\u0000qp\u0001\u0000\u0000\u0000qr\u0001"+
		"\u0000\u0000\u0000rv\u0001\u0000\u0000\u0000su\u00034\u001a\u0000ts\u0001"+
		"\u0000\u0000\u0000ux\u0001\u0000\u0000\u0000vt\u0001\u0000\u0000\u0000"+
		"vw\u0001\u0000\u0000\u0000wy\u0001\u0000\u0000\u0000xv\u0001\u0000\u0000"+
		"\u0000yz\u0005\u0001\u0000\u0000z\u000f\u0001\u0000\u0000\u0000{\u007f"+
		"\u0005\t\u0000\u0000|~\u00034\u001a\u0000}|\u0001\u0000\u0000\u0000~\u0081"+
		"\u0001\u0000\u0000\u0000\u007f}\u0001\u0000\u0000\u0000\u007f\u0080\u0001"+
		"\u0000\u0000\u0000\u0080\u0082\u0001\u0000\u0000\u0000\u0081\u007f\u0001"+
		"\u0000\u0000\u0000\u0082\u0083\u0005\u0001\u0000\u0000\u0083\u0011\u0001"+
		"\u0000\u0000\u0000\u0084\u0085\u0005\u0006\u0000\u0000\u0085\u0013\u0001"+
		"\u0000\u0000\u0000\u0086\u0087\u0003\u001c\u000e\u0000\u0087\u0015\u0001"+
		"\u0000\u0000\u0000\u0088\u0089\u0005\n\u0000\u0000\u0089\u0017\u0001\u0000"+
		"\u0000\u0000\u008a\u008f\u0003\u001a\r\u0000\u008b\u008c\u0005\u000e\u0000"+
		"\u0000\u008c\u008e\u0003\u001a\r\u0000\u008d\u008b\u0001\u0000\u0000\u0000"+
		"\u008e\u0091\u0001\u0000\u0000\u0000\u008f\u008d\u0001\u0000\u0000\u0000"+
		"\u008f\u0090\u0001\u0000\u0000\u0000\u0090\u0019\u0001\u0000\u0000\u0000"+
		"\u0091\u008f\u0001\u0000\u0000\u0000\u0092\u0093\u0007\u0000\u0000\u0000"+
		"\u0093\u001b\u0001\u0000\u0000\u0000\u0094\u0095\u0007\u0001\u0000\u0000"+
		"\u0095\u001d\u0001\u0000\u0000\u0000\u0096\u009b\u0003 \u0010\u0000\u0097"+
		"\u009b\u0003\"\u0011\u0000\u0098\u009b\u00030\u0018\u0000\u0099\u009b"+
		"\u00032\u0019\u0000\u009a\u0096\u0001\u0000\u0000\u0000\u009a\u0097\u0001"+
		"\u0000\u0000\u0000\u009a\u0098\u0001\u0000\u0000\u0000\u009a\u0099\u0001"+
		"\u0000\u0000\u0000\u009b\u001f\u0001\u0000\u0000\u0000\u009c\u00a0\u0005"+
		"\u0004\u0000\u0000\u009d\u009f\u00034\u001a\u0000\u009e\u009d\u0001\u0000"+
		"\u0000\u0000\u009f\u00a2\u0001\u0000\u0000\u0000\u00a0\u009e\u0001\u0000"+
		"\u0000\u0000\u00a0\u00a1\u0001\u0000\u0000\u0000\u00a1\u00a3\u0001\u0000"+
		"\u0000\u0000\u00a2\u00a0\u0001\u0000\u0000\u0000\u00a3\u00a4\u0005\u0001"+
		"\u0000\u0000\u00a4!\u0001\u0000\u0000\u0000\u00a5\u00a7\u0003,\u0016\u0000"+
		"\u00a6\u00a8\u0003$\u0012\u0000\u00a7\u00a6\u0001\u0000\u0000\u0000\u00a7"+
		"\u00a8\u0001\u0000\u0000\u0000\u00a8\u00ac\u0001\u0000\u0000\u0000\u00a9"+
		"\u00ab\u0003&\u0013\u0000\u00aa\u00a9\u0001\u0000\u0000\u0000\u00ab\u00ae"+
		"\u0001\u0000\u0000\u0000\u00ac\u00aa\u0001\u0000\u0000\u0000\u00ac\u00ad"+
		"\u0001\u0000\u0000\u0000\u00ad\u00b2\u0001\u0000\u0000\u0000\u00ae\u00ac"+
		"\u0001\u0000\u0000\u0000\u00af\u00b1\u00034\u001a\u0000\u00b0\u00af\u0001"+
		"\u0000\u0000\u0000\u00b1\u00b4\u0001\u0000\u0000\u0000\u00b2\u00b0\u0001"+
		"\u0000\u0000\u0000\u00b2\u00b3\u0001\u0000\u0000\u0000\u00b3\u00b5\u0001"+
		"\u0000\u0000\u0000\u00b4\u00b2\u0001\u0000\u0000\u0000\u00b5\u00b6\u0005"+
		"\u0001\u0000\u0000\u00b6#\u0001\u0000\u0000\u0000\u00b7\u00b8\u0007\u0002"+
		"\u0000\u0000\u00b8%\u0001\u0000\u0000\u0000\u00b9\u00ba\u0005\n\u0000"+
		"\u0000\u00ba\'\u0001\u0000\u0000\u0000\u00bb\u00bf\u0003*\u0015\u0000"+
		"\u00bc\u00bf\u00030\u0018\u0000\u00bd\u00bf\u00032\u0019\u0000\u00be\u00bb"+
		"\u0001\u0000\u0000\u0000\u00be\u00bc\u0001\u0000\u0000\u0000\u00be\u00bd"+
		"\u0001\u0000\u0000\u0000\u00bf)\u0001\u0000\u0000\u0000\u00c0\u00c1\u0005"+
		"\u0004\u0000\u0000\u00c1\u00c5\u0003,\u0016\u0000\u00c2\u00c4\u00034\u001a"+
		"\u0000\u00c3\u00c2\u0001\u0000\u0000\u0000\u00c4\u00c7\u0001\u0000\u0000"+
		"\u0000\u00c5\u00c3\u0001\u0000\u0000\u0000\u00c5\u00c6\u0001\u0000\u0000"+
		"\u0000\u00c6\u00c8\u0001\u0000\u0000\u0000\u00c7\u00c5\u0001\u0000\u0000"+
		"\u0000\u00c8\u00c9\u0005\u0001\u0000\u0000\u00c9\u00d9\u0001\u0000\u0000"+
		"\u0000\u00ca\u00cc\u0003.\u0017\u0000\u00cb\u00ca\u0001\u0000\u0000\u0000"+
		"\u00cc\u00cd\u0001\u0000\u0000\u0000\u00cd\u00cb\u0001\u0000\u0000\u0000"+
		"\u00cd\u00ce\u0001\u0000\u0000\u0000\u00ce\u00cf\u0001\u0000\u0000\u0000"+
		"\u00cf\u00d3\u0005\u000f\u0000\u0000\u00d0\u00d2\u00034\u001a\u0000\u00d1"+
		"\u00d0\u0001\u0000\u0000\u0000\u00d2\u00d5\u0001\u0000\u0000\u0000\u00d3"+
		"\u00d1\u0001\u0000\u0000\u0000\u00d3\u00d4\u0001\u0000\u0000\u0000\u00d4"+
		"\u00d6\u0001\u0000\u0000\u0000\u00d5\u00d3\u0001\u0000\u0000\u0000\u00d6"+
		"\u00d7\u0005\u0001\u0000\u0000\u00d7\u00d9\u0001\u0000\u0000\u0000\u00d8"+
		"\u00c0\u0001\u0000\u0000\u0000\u00d8\u00cb\u0001\u0000\u0000\u0000\u00d9"+
		"+\u0001\u0000\u0000\u0000\u00da\u00db\u0007\u0003\u0000\u0000\u00db-\u0001"+
		"\u0000\u0000\u0000\u00dc\u00dd\u0007\u0004\u0000\u0000\u00dd/\u0001\u0000"+
		"\u0000\u0000\u00de\u00e0\u00034\u001a\u0000\u00df\u00de\u0001\u0000\u0000"+
		"\u0000\u00e0\u00e1\u0001\u0000\u0000\u0000\u00e1\u00df\u0001\u0000\u0000"+
		"\u0000\u00e1\u00e2\u0001\u0000\u0000\u0000\u00e2\u00e3\u0001\u0000\u0000"+
		"\u0000\u00e3\u00e4\u0005\u0001\u0000\u0000\u00e41\u0001\u0000\u0000\u0000"+
		"\u00e5\u00e6\u0005\u0001\u0000\u0000\u00e63\u0001\u0000\u0000\u0000\u00e7"+
		"\u00e8\b\u0005\u0000\u0000\u00e85\u0001\u0000\u0000\u0000\u00169AIQYb"+
		"hqv\u007f\u008f\u009a\u00a0\u00a7\u00ac\u00b2\u00be\u00c5\u00cd\u00d3"+
		"\u00d8\u00e1";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}