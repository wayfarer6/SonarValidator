
// Generated from /home/osboxes/SonarValidator/SonarValidator_Prober/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.2


#include "SwitchTopologyVisitor.h"

#include "SwitchTopologyParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct SwitchTopologyParserStaticData final {
  SwitchTopologyParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  SwitchTopologyParserStaticData(const SwitchTopologyParserStaticData&) = delete;
  SwitchTopologyParserStaticData(SwitchTopologyParserStaticData&&) = delete;
  SwitchTopologyParserStaticData& operator=(const SwitchTopologyParserStaticData&) = delete;
  SwitchTopologyParserStaticData& operator=(SwitchTopologyParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag switchtopologyParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<SwitchTopologyParserStaticData> switchtopologyParserStaticData = nullptr;

void switchtopologyParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (switchtopologyParserStaticData != nullptr) {
    return;
  }
#else
  assert(switchtopologyParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<SwitchTopologyParserStaticData>(
    std::vector<std::string>{
      "runningDocument", "vlanDocument", "briefDocument", "portDocument", 
      "configLine", "vlanItem", "vlanHeader", "vlanEntry", "vlanSeparator", 
      "vlanId", "vlanName", "vlanStatus", "portList", "portToken", "nameToken", 
      "briefItem", "briefHeader", "briefEntry", "addrOrUnassigned", "briefField", 
      "portItem", "portEntry", "ifname", "keyWord", "genericLine", "blank", 
      "elem"
    },
    std::vector<std::string>{
      "", "", "", "'VLAN'", "", "", "", "", "'unassigned'", "", "", "", 
      "", "", "','", "':'", "'/'", "'('", "')'"
    },
    std::vector<std::string>{
      "", "NEWLINE", "WS", "VLAN", "INTERFACE", "ADDR", "NUMBER", "PORTNAME", 
      "UNASSIGNED", "DASHES", "STATUSWORD", "IFNAME", "ATTRWORD", "IDENT", 
      "COMMA", "COLON", "SLASH", "LPAREN", "RPAREN"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,18,234,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,2,19,7,19,2,20,7,20,2,21,7,
  	21,2,22,7,22,2,23,7,23,2,24,7,24,2,25,7,25,2,26,7,26,1,0,5,0,56,8,0,10,
  	0,12,0,59,9,0,1,0,1,0,1,1,5,1,64,8,1,10,1,12,1,67,9,1,1,1,1,1,1,2,5,2,
  	72,8,2,10,2,12,2,75,9,2,1,2,1,2,1,3,5,3,80,8,3,10,3,12,3,83,9,3,1,3,1,
  	3,1,4,4,4,88,8,4,11,4,12,4,89,1,4,1,4,1,5,1,5,1,5,1,5,1,5,3,5,99,8,5,
  	1,6,1,6,5,6,103,8,6,10,6,12,6,106,9,6,1,6,1,6,1,7,1,7,1,7,1,7,3,7,114,
  	8,7,1,7,5,7,117,8,7,10,7,12,7,120,9,7,1,7,1,7,1,8,1,8,5,8,126,8,8,10,
  	8,12,8,129,9,8,1,8,1,8,1,9,1,9,1,10,1,10,1,11,1,11,1,12,1,12,1,12,5,12,
  	142,8,12,10,12,12,12,145,9,12,1,13,1,13,1,14,1,14,1,15,1,15,1,15,1,15,
  	3,15,155,8,15,1,16,1,16,5,16,159,8,16,10,16,12,16,162,9,16,1,16,1,16,
  	1,17,1,17,3,17,168,8,17,1,17,5,17,171,8,17,10,17,12,17,174,9,17,1,17,
  	5,17,177,8,17,10,17,12,17,180,9,17,1,17,1,17,1,18,1,18,1,19,1,19,1,20,
  	1,20,1,20,3,20,191,8,20,1,21,1,21,1,21,5,21,196,8,21,10,21,12,21,199,
  	9,21,1,21,1,21,1,21,4,21,204,8,21,11,21,12,21,205,1,21,1,21,5,21,210,
  	8,21,10,21,12,21,213,9,21,1,21,1,21,3,21,217,8,21,1,22,1,22,1,23,1,23,
  	1,24,4,24,224,8,24,11,24,12,24,225,1,24,1,24,1,25,1,25,1,26,1,26,1,26,
  	0,0,27,0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,34,36,38,40,42,44,
  	46,48,50,52,0,6,3,0,6,7,11,11,13,13,2,0,6,7,11,13,2,0,5,5,8,8,2,0,7,7,
  	11,13,3,0,3,4,6,6,10,13,1,0,1,1,234,0,57,1,0,0,0,2,65,1,0,0,0,4,73,1,
  	0,0,0,6,81,1,0,0,0,8,87,1,0,0,0,10,98,1,0,0,0,12,100,1,0,0,0,14,109,1,
  	0,0,0,16,123,1,0,0,0,18,132,1,0,0,0,20,134,1,0,0,0,22,136,1,0,0,0,24,
  	138,1,0,0,0,26,146,1,0,0,0,28,148,1,0,0,0,30,154,1,0,0,0,32,156,1,0,0,
  	0,34,165,1,0,0,0,36,183,1,0,0,0,38,185,1,0,0,0,40,190,1,0,0,0,42,216,
  	1,0,0,0,44,218,1,0,0,0,46,220,1,0,0,0,48,223,1,0,0,0,50,229,1,0,0,0,52,
  	231,1,0,0,0,54,56,3,8,4,0,55,54,1,0,0,0,56,59,1,0,0,0,57,55,1,0,0,0,57,
  	58,1,0,0,0,58,60,1,0,0,0,59,57,1,0,0,0,60,61,5,0,0,1,61,1,1,0,0,0,62,
  	64,3,10,5,0,63,62,1,0,0,0,64,67,1,0,0,0,65,63,1,0,0,0,65,66,1,0,0,0,66,
  	68,1,0,0,0,67,65,1,0,0,0,68,69,5,0,0,1,69,3,1,0,0,0,70,72,3,30,15,0,71,
  	70,1,0,0,0,72,75,1,0,0,0,73,71,1,0,0,0,73,74,1,0,0,0,74,76,1,0,0,0,75,
  	73,1,0,0,0,76,77,5,0,0,1,77,5,1,0,0,0,78,80,3,40,20,0,79,78,1,0,0,0,80,
  	83,1,0,0,0,81,79,1,0,0,0,81,82,1,0,0,0,82,84,1,0,0,0,83,81,1,0,0,0,84,
  	85,5,0,0,1,85,7,1,0,0,0,86,88,3,52,26,0,87,86,1,0,0,0,88,89,1,0,0,0,89,
  	87,1,0,0,0,89,90,1,0,0,0,90,91,1,0,0,0,91,92,5,1,0,0,92,9,1,0,0,0,93,
  	99,3,12,6,0,94,99,3,14,7,0,95,99,3,16,8,0,96,99,3,48,24,0,97,99,3,50,
  	25,0,98,93,1,0,0,0,98,94,1,0,0,0,98,95,1,0,0,0,98,96,1,0,0,0,98,97,1,
  	0,0,0,99,11,1,0,0,0,100,104,5,3,0,0,101,103,3,52,26,0,102,101,1,0,0,0,
  	103,106,1,0,0,0,104,102,1,0,0,0,104,105,1,0,0,0,105,107,1,0,0,0,106,104,
  	1,0,0,0,107,108,5,1,0,0,108,13,1,0,0,0,109,110,3,18,9,0,110,111,3,20,
  	10,0,111,113,3,22,11,0,112,114,3,24,12,0,113,112,1,0,0,0,113,114,1,0,
  	0,0,114,118,1,0,0,0,115,117,3,52,26,0,116,115,1,0,0,0,117,120,1,0,0,0,
  	118,116,1,0,0,0,118,119,1,0,0,0,119,121,1,0,0,0,120,118,1,0,0,0,121,122,
  	5,1,0,0,122,15,1,0,0,0,123,127,5,9,0,0,124,126,3,52,26,0,125,124,1,0,
  	0,0,126,129,1,0,0,0,127,125,1,0,0,0,127,128,1,0,0,0,128,130,1,0,0,0,129,
  	127,1,0,0,0,130,131,5,1,0,0,131,17,1,0,0,0,132,133,5,6,0,0,133,19,1,0,
  	0,0,134,135,3,28,14,0,135,21,1,0,0,0,136,137,5,10,0,0,137,23,1,0,0,0,
  	138,143,3,26,13,0,139,140,5,14,0,0,140,142,3,26,13,0,141,139,1,0,0,0,
  	142,145,1,0,0,0,143,141,1,0,0,0,143,144,1,0,0,0,144,25,1,0,0,0,145,143,
  	1,0,0,0,146,147,7,0,0,0,147,27,1,0,0,0,148,149,7,1,0,0,149,29,1,0,0,0,
  	150,155,3,32,16,0,151,155,3,34,17,0,152,155,3,48,24,0,153,155,3,50,25,
  	0,154,150,1,0,0,0,154,151,1,0,0,0,154,152,1,0,0,0,154,153,1,0,0,0,155,
  	31,1,0,0,0,156,160,5,4,0,0,157,159,3,52,26,0,158,157,1,0,0,0,159,162,
  	1,0,0,0,160,158,1,0,0,0,160,161,1,0,0,0,161,163,1,0,0,0,162,160,1,0,0,
  	0,163,164,5,1,0,0,164,33,1,0,0,0,165,167,3,44,22,0,166,168,3,36,18,0,
  	167,166,1,0,0,0,167,168,1,0,0,0,168,172,1,0,0,0,169,171,3,38,19,0,170,
  	169,1,0,0,0,171,174,1,0,0,0,172,170,1,0,0,0,172,173,1,0,0,0,173,178,1,
  	0,0,0,174,172,1,0,0,0,175,177,3,52,26,0,176,175,1,0,0,0,177,180,1,0,0,
  	0,178,176,1,0,0,0,178,179,1,0,0,0,179,181,1,0,0,0,180,178,1,0,0,0,181,
  	182,5,1,0,0,182,35,1,0,0,0,183,184,7,2,0,0,184,37,1,0,0,0,185,186,5,10,
  	0,0,186,39,1,0,0,0,187,191,3,42,21,0,188,191,3,48,24,0,189,191,3,50,25,
  	0,190,187,1,0,0,0,190,188,1,0,0,0,190,189,1,0,0,0,191,41,1,0,0,0,192,
  	193,5,4,0,0,193,197,3,44,22,0,194,196,3,52,26,0,195,194,1,0,0,0,196,199,
  	1,0,0,0,197,195,1,0,0,0,197,198,1,0,0,0,198,200,1,0,0,0,199,197,1,0,0,
  	0,200,201,5,1,0,0,201,217,1,0,0,0,202,204,3,46,23,0,203,202,1,0,0,0,204,
  	205,1,0,0,0,205,203,1,0,0,0,205,206,1,0,0,0,206,207,1,0,0,0,207,211,5,
  	15,0,0,208,210,3,52,26,0,209,208,1,0,0,0,210,213,1,0,0,0,211,209,1,0,
  	0,0,211,212,1,0,0,0,212,214,1,0,0,0,213,211,1,0,0,0,214,215,5,1,0,0,215,
  	217,1,0,0,0,216,192,1,0,0,0,216,203,1,0,0,0,217,43,1,0,0,0,218,219,7,
  	3,0,0,219,45,1,0,0,0,220,221,7,4,0,0,221,47,1,0,0,0,222,224,3,52,26,0,
  	223,222,1,0,0,0,224,225,1,0,0,0,225,223,1,0,0,0,225,226,1,0,0,0,226,227,
  	1,0,0,0,227,228,5,1,0,0,228,49,1,0,0,0,229,230,5,1,0,0,230,51,1,0,0,0,
  	231,232,8,5,0,0,232,53,1,0,0,0,22,57,65,73,81,89,98,104,113,118,127,143,
  	154,160,167,172,178,190,197,205,211,216,225
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  switchtopologyParserStaticData = std::move(staticData);
}

}

SwitchTopologyParser::SwitchTopologyParser(TokenStream *input) : SwitchTopologyParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

SwitchTopologyParser::SwitchTopologyParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  SwitchTopologyParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *switchtopologyParserStaticData->atn, switchtopologyParserStaticData->decisionToDFA, switchtopologyParserStaticData->sharedContextCache, options);
}

SwitchTopologyParser::~SwitchTopologyParser() {
  delete _interpreter;
}

const atn::ATN& SwitchTopologyParser::getATN() const {
  return *switchtopologyParserStaticData->atn;
}

std::string SwitchTopologyParser::getGrammarFileName() const {
  return "SwitchTopology.g4";
}

const std::vector<std::string>& SwitchTopologyParser::getRuleNames() const {
  return switchtopologyParserStaticData->ruleNames;
}

const dfa::Vocabulary& SwitchTopologyParser::getVocabulary() const {
  return switchtopologyParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView SwitchTopologyParser::getSerializedATN() const {
  return switchtopologyParserStaticData->serializedATN;
}


//----------------- RunningDocumentContext ------------------------------------------------------------------

SwitchTopologyParser::RunningDocumentContext::RunningDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::RunningDocumentContext::EOF() {
  return getToken(SwitchTopologyParser::EOF, 0);
}

std::vector<SwitchTopologyParser::ConfigLineContext *> SwitchTopologyParser::RunningDocumentContext::configLine() {
  return getRuleContexts<SwitchTopologyParser::ConfigLineContext>();
}

SwitchTopologyParser::ConfigLineContext* SwitchTopologyParser::RunningDocumentContext::configLine(size_t i) {
  return getRuleContext<SwitchTopologyParser::ConfigLineContext>(i);
}


size_t SwitchTopologyParser::RunningDocumentContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleRunningDocument;
}


std::any SwitchTopologyParser::RunningDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitRunningDocument(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::RunningDocumentContext* SwitchTopologyParser::runningDocument() {
  RunningDocumentContext *_localctx = _tracker.createInstance<RunningDocumentContext>(_ctx, getState());
  enterRule(_localctx, 0, SwitchTopologyParser::RuleRunningDocument);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(57);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(54);
      configLine();
      setState(59);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(60);
    match(SwitchTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanDocumentContext ------------------------------------------------------------------

SwitchTopologyParser::VlanDocumentContext::VlanDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::VlanDocumentContext::EOF() {
  return getToken(SwitchTopologyParser::EOF, 0);
}

std::vector<SwitchTopologyParser::VlanItemContext *> SwitchTopologyParser::VlanDocumentContext::vlanItem() {
  return getRuleContexts<SwitchTopologyParser::VlanItemContext>();
}

SwitchTopologyParser::VlanItemContext* SwitchTopologyParser::VlanDocumentContext::vlanItem(size_t i) {
  return getRuleContext<SwitchTopologyParser::VlanItemContext>(i);
}


size_t SwitchTopologyParser::VlanDocumentContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanDocument;
}


std::any SwitchTopologyParser::VlanDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanDocument(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanDocumentContext* SwitchTopologyParser::vlanDocument() {
  VlanDocumentContext *_localctx = _tracker.createInstance<VlanDocumentContext>(_ctx, getState());
  enterRule(_localctx, 2, SwitchTopologyParser::RuleVlanDocument);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(65);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524286) != 0)) {
      setState(62);
      vlanItem();
      setState(67);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(68);
    match(SwitchTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefDocumentContext ------------------------------------------------------------------

SwitchTopologyParser::BriefDocumentContext::BriefDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::BriefDocumentContext::EOF() {
  return getToken(SwitchTopologyParser::EOF, 0);
}

std::vector<SwitchTopologyParser::BriefItemContext *> SwitchTopologyParser::BriefDocumentContext::briefItem() {
  return getRuleContexts<SwitchTopologyParser::BriefItemContext>();
}

SwitchTopologyParser::BriefItemContext* SwitchTopologyParser::BriefDocumentContext::briefItem(size_t i) {
  return getRuleContext<SwitchTopologyParser::BriefItemContext>(i);
}


size_t SwitchTopologyParser::BriefDocumentContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBriefDocument;
}


std::any SwitchTopologyParser::BriefDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBriefDocument(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BriefDocumentContext* SwitchTopologyParser::briefDocument() {
  BriefDocumentContext *_localctx = _tracker.createInstance<BriefDocumentContext>(_ctx, getState());
  enterRule(_localctx, 4, SwitchTopologyParser::RuleBriefDocument);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(73);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524286) != 0)) {
      setState(70);
      briefItem();
      setState(75);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(76);
    match(SwitchTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortDocumentContext ------------------------------------------------------------------

SwitchTopologyParser::PortDocumentContext::PortDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::PortDocumentContext::EOF() {
  return getToken(SwitchTopologyParser::EOF, 0);
}

std::vector<SwitchTopologyParser::PortItemContext *> SwitchTopologyParser::PortDocumentContext::portItem() {
  return getRuleContexts<SwitchTopologyParser::PortItemContext>();
}

SwitchTopologyParser::PortItemContext* SwitchTopologyParser::PortDocumentContext::portItem(size_t i) {
  return getRuleContext<SwitchTopologyParser::PortItemContext>(i);
}


size_t SwitchTopologyParser::PortDocumentContext::getRuleIndex() const {
  return SwitchTopologyParser::RulePortDocument;
}


std::any SwitchTopologyParser::PortDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitPortDocument(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::PortDocumentContext* SwitchTopologyParser::portDocument() {
  PortDocumentContext *_localctx = _tracker.createInstance<PortDocumentContext>(_ctx, getState());
  enterRule(_localctx, 6, SwitchTopologyParser::RulePortDocument);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(81);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524286) != 0)) {
      setState(78);
      portItem();
      setState(83);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(84);
    match(SwitchTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ConfigLineContext ------------------------------------------------------------------

SwitchTopologyParser::ConfigLineContext::ConfigLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::ConfigLineContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::ConfigLineContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::ConfigLineContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::ConfigLineContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleConfigLine;
}


std::any SwitchTopologyParser::ConfigLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitConfigLine(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::ConfigLineContext* SwitchTopologyParser::configLine() {
  ConfigLineContext *_localctx = _tracker.createInstance<ConfigLineContext>(_ctx, getState());
  enterRule(_localctx, 8, SwitchTopologyParser::RuleConfigLine);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(87); 
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(86);
      elem();
      setState(89); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0));
    setState(91);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanItemContext ------------------------------------------------------------------

SwitchTopologyParser::VlanItemContext::VlanItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::VlanHeaderContext* SwitchTopologyParser::VlanItemContext::vlanHeader() {
  return getRuleContext<SwitchTopologyParser::VlanHeaderContext>(0);
}

SwitchTopologyParser::VlanEntryContext* SwitchTopologyParser::VlanItemContext::vlanEntry() {
  return getRuleContext<SwitchTopologyParser::VlanEntryContext>(0);
}

SwitchTopologyParser::VlanSeparatorContext* SwitchTopologyParser::VlanItemContext::vlanSeparator() {
  return getRuleContext<SwitchTopologyParser::VlanSeparatorContext>(0);
}

SwitchTopologyParser::GenericLineContext* SwitchTopologyParser::VlanItemContext::genericLine() {
  return getRuleContext<SwitchTopologyParser::GenericLineContext>(0);
}

SwitchTopologyParser::BlankContext* SwitchTopologyParser::VlanItemContext::blank() {
  return getRuleContext<SwitchTopologyParser::BlankContext>(0);
}


size_t SwitchTopologyParser::VlanItemContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanItem;
}


std::any SwitchTopologyParser::VlanItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanItem(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanItemContext* SwitchTopologyParser::vlanItem() {
  VlanItemContext *_localctx = _tracker.createInstance<VlanItemContext>(_ctx, getState());
  enterRule(_localctx, 10, SwitchTopologyParser::RuleVlanItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(98);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 5, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(93);
      vlanHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(94);
      vlanEntry();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(95);
      vlanSeparator();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(96);
      genericLine();
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(97);
      blank();
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanHeaderContext ------------------------------------------------------------------

SwitchTopologyParser::VlanHeaderContext::VlanHeaderContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::VlanHeaderContext::VLAN() {
  return getToken(SwitchTopologyParser::VLAN, 0);
}

tree::TerminalNode* SwitchTopologyParser::VlanHeaderContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::VlanHeaderContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::VlanHeaderContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::VlanHeaderContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanHeader;
}


std::any SwitchTopologyParser::VlanHeaderContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanHeader(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanHeaderContext* SwitchTopologyParser::vlanHeader() {
  VlanHeaderContext *_localctx = _tracker.createInstance<VlanHeaderContext>(_ctx, getState());
  enterRule(_localctx, 12, SwitchTopologyParser::RuleVlanHeader);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(100);
    match(SwitchTopologyParser::VLAN);
    setState(104);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(101);
      elem();
      setState(106);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(107);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanEntryContext ------------------------------------------------------------------

SwitchTopologyParser::VlanEntryContext::VlanEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::VlanIdContext* SwitchTopologyParser::VlanEntryContext::vlanId() {
  return getRuleContext<SwitchTopologyParser::VlanIdContext>(0);
}

SwitchTopologyParser::VlanNameContext* SwitchTopologyParser::VlanEntryContext::vlanName() {
  return getRuleContext<SwitchTopologyParser::VlanNameContext>(0);
}

SwitchTopologyParser::VlanStatusContext* SwitchTopologyParser::VlanEntryContext::vlanStatus() {
  return getRuleContext<SwitchTopologyParser::VlanStatusContext>(0);
}

tree::TerminalNode* SwitchTopologyParser::VlanEntryContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

SwitchTopologyParser::PortListContext* SwitchTopologyParser::VlanEntryContext::portList() {
  return getRuleContext<SwitchTopologyParser::PortListContext>(0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::VlanEntryContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::VlanEntryContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::VlanEntryContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanEntry;
}


std::any SwitchTopologyParser::VlanEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanEntry(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanEntryContext* SwitchTopologyParser::vlanEntry() {
  VlanEntryContext *_localctx = _tracker.createInstance<VlanEntryContext>(_ctx, getState());
  enterRule(_localctx, 14, SwitchTopologyParser::RuleVlanEntry);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(109);
    vlanId();
    setState(110);
    vlanName();
    setState(111);
    vlanStatus();
    setState(113);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 7, _ctx)) {
    case 1: {
      setState(112);
      portList();
      break;
    }

    default:
      break;
    }
    setState(118);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(115);
      elem();
      setState(120);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(121);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanSeparatorContext ------------------------------------------------------------------

SwitchTopologyParser::VlanSeparatorContext::VlanSeparatorContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::VlanSeparatorContext::DASHES() {
  return getToken(SwitchTopologyParser::DASHES, 0);
}

tree::TerminalNode* SwitchTopologyParser::VlanSeparatorContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::VlanSeparatorContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::VlanSeparatorContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::VlanSeparatorContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanSeparator;
}


std::any SwitchTopologyParser::VlanSeparatorContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanSeparator(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanSeparatorContext* SwitchTopologyParser::vlanSeparator() {
  VlanSeparatorContext *_localctx = _tracker.createInstance<VlanSeparatorContext>(_ctx, getState());
  enterRule(_localctx, 16, SwitchTopologyParser::RuleVlanSeparator);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(123);
    match(SwitchTopologyParser::DASHES);
    setState(127);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(124);
      elem();
      setState(129);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(130);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanIdContext ------------------------------------------------------------------

SwitchTopologyParser::VlanIdContext::VlanIdContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::VlanIdContext::NUMBER() {
  return getToken(SwitchTopologyParser::NUMBER, 0);
}


size_t SwitchTopologyParser::VlanIdContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanId;
}


std::any SwitchTopologyParser::VlanIdContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanId(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanIdContext* SwitchTopologyParser::vlanId() {
  VlanIdContext *_localctx = _tracker.createInstance<VlanIdContext>(_ctx, getState());
  enterRule(_localctx, 18, SwitchTopologyParser::RuleVlanId);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(132);
    match(SwitchTopologyParser::NUMBER);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanNameContext ------------------------------------------------------------------

SwitchTopologyParser::VlanNameContext::VlanNameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::NameTokenContext* SwitchTopologyParser::VlanNameContext::nameToken() {
  return getRuleContext<SwitchTopologyParser::NameTokenContext>(0);
}


size_t SwitchTopologyParser::VlanNameContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanName;
}


std::any SwitchTopologyParser::VlanNameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanName(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanNameContext* SwitchTopologyParser::vlanName() {
  VlanNameContext *_localctx = _tracker.createInstance<VlanNameContext>(_ctx, getState());
  enterRule(_localctx, 20, SwitchTopologyParser::RuleVlanName);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(134);
    nameToken();
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- VlanStatusContext ------------------------------------------------------------------

SwitchTopologyParser::VlanStatusContext::VlanStatusContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::VlanStatusContext::STATUSWORD() {
  return getToken(SwitchTopologyParser::STATUSWORD, 0);
}


size_t SwitchTopologyParser::VlanStatusContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleVlanStatus;
}


std::any SwitchTopologyParser::VlanStatusContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitVlanStatus(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::VlanStatusContext* SwitchTopologyParser::vlanStatus() {
  VlanStatusContext *_localctx = _tracker.createInstance<VlanStatusContext>(_ctx, getState());
  enterRule(_localctx, 22, SwitchTopologyParser::RuleVlanStatus);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(136);
    match(SwitchTopologyParser::STATUSWORD);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortListContext ------------------------------------------------------------------

SwitchTopologyParser::PortListContext::PortListContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

std::vector<SwitchTopologyParser::PortTokenContext *> SwitchTopologyParser::PortListContext::portToken() {
  return getRuleContexts<SwitchTopologyParser::PortTokenContext>();
}

SwitchTopologyParser::PortTokenContext* SwitchTopologyParser::PortListContext::portToken(size_t i) {
  return getRuleContext<SwitchTopologyParser::PortTokenContext>(i);
}

std::vector<tree::TerminalNode *> SwitchTopologyParser::PortListContext::COMMA() {
  return getTokens(SwitchTopologyParser::COMMA);
}

tree::TerminalNode* SwitchTopologyParser::PortListContext::COMMA(size_t i) {
  return getToken(SwitchTopologyParser::COMMA, i);
}


size_t SwitchTopologyParser::PortListContext::getRuleIndex() const {
  return SwitchTopologyParser::RulePortList;
}


std::any SwitchTopologyParser::PortListContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitPortList(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::PortListContext* SwitchTopologyParser::portList() {
  PortListContext *_localctx = _tracker.createInstance<PortListContext>(_ctx, getState());
  enterRule(_localctx, 24, SwitchTopologyParser::RulePortList);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    setState(138);
    portToken();
    setState(143);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 10, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(139);
        match(SwitchTopologyParser::COMMA);
        setState(140);
        portToken(); 
      }
      setState(145);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 10, _ctx);
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortTokenContext ------------------------------------------------------------------

SwitchTopologyParser::PortTokenContext::PortTokenContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::PortTokenContext::PORTNAME() {
  return getToken(SwitchTopologyParser::PORTNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::PortTokenContext::IFNAME() {
  return getToken(SwitchTopologyParser::IFNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::PortTokenContext::IDENT() {
  return getToken(SwitchTopologyParser::IDENT, 0);
}

tree::TerminalNode* SwitchTopologyParser::PortTokenContext::NUMBER() {
  return getToken(SwitchTopologyParser::NUMBER, 0);
}


size_t SwitchTopologyParser::PortTokenContext::getRuleIndex() const {
  return SwitchTopologyParser::RulePortToken;
}


std::any SwitchTopologyParser::PortTokenContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitPortToken(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::PortTokenContext* SwitchTopologyParser::portToken() {
  PortTokenContext *_localctx = _tracker.createInstance<PortTokenContext>(_ctx, getState());
  enterRule(_localctx, 26, SwitchTopologyParser::RulePortToken);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(146);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 10432) != 0))) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- NameTokenContext ------------------------------------------------------------------

SwitchTopologyParser::NameTokenContext::NameTokenContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::NameTokenContext::IDENT() {
  return getToken(SwitchTopologyParser::IDENT, 0);
}

tree::TerminalNode* SwitchTopologyParser::NameTokenContext::IFNAME() {
  return getToken(SwitchTopologyParser::IFNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::NameTokenContext::PORTNAME() {
  return getToken(SwitchTopologyParser::PORTNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::NameTokenContext::ATTRWORD() {
  return getToken(SwitchTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* SwitchTopologyParser::NameTokenContext::NUMBER() {
  return getToken(SwitchTopologyParser::NUMBER, 0);
}


size_t SwitchTopologyParser::NameTokenContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleNameToken;
}


std::any SwitchTopologyParser::NameTokenContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitNameToken(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::NameTokenContext* SwitchTopologyParser::nameToken() {
  NameTokenContext *_localctx = _tracker.createInstance<NameTokenContext>(_ctx, getState());
  enterRule(_localctx, 28, SwitchTopologyParser::RuleNameToken);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(148);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 14528) != 0))) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefItemContext ------------------------------------------------------------------

SwitchTopologyParser::BriefItemContext::BriefItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::BriefHeaderContext* SwitchTopologyParser::BriefItemContext::briefHeader() {
  return getRuleContext<SwitchTopologyParser::BriefHeaderContext>(0);
}

SwitchTopologyParser::BriefEntryContext* SwitchTopologyParser::BriefItemContext::briefEntry() {
  return getRuleContext<SwitchTopologyParser::BriefEntryContext>(0);
}

SwitchTopologyParser::GenericLineContext* SwitchTopologyParser::BriefItemContext::genericLine() {
  return getRuleContext<SwitchTopologyParser::GenericLineContext>(0);
}

SwitchTopologyParser::BlankContext* SwitchTopologyParser::BriefItemContext::blank() {
  return getRuleContext<SwitchTopologyParser::BlankContext>(0);
}


size_t SwitchTopologyParser::BriefItemContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBriefItem;
}


std::any SwitchTopologyParser::BriefItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBriefItem(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BriefItemContext* SwitchTopologyParser::briefItem() {
  BriefItemContext *_localctx = _tracker.createInstance<BriefItemContext>(_ctx, getState());
  enterRule(_localctx, 30, SwitchTopologyParser::RuleBriefItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(154);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 11, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(150);
      briefHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(151);
      briefEntry();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(152);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(153);
      blank();
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefHeaderContext ------------------------------------------------------------------

SwitchTopologyParser::BriefHeaderContext::BriefHeaderContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::BriefHeaderContext::INTERFACE() {
  return getToken(SwitchTopologyParser::INTERFACE, 0);
}

tree::TerminalNode* SwitchTopologyParser::BriefHeaderContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::BriefHeaderContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::BriefHeaderContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::BriefHeaderContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBriefHeader;
}


std::any SwitchTopologyParser::BriefHeaderContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBriefHeader(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BriefHeaderContext* SwitchTopologyParser::briefHeader() {
  BriefHeaderContext *_localctx = _tracker.createInstance<BriefHeaderContext>(_ctx, getState());
  enterRule(_localctx, 32, SwitchTopologyParser::RuleBriefHeader);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(156);
    match(SwitchTopologyParser::INTERFACE);
    setState(160);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(157);
      elem();
      setState(162);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(163);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefEntryContext ------------------------------------------------------------------

SwitchTopologyParser::BriefEntryContext::BriefEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::IfnameContext* SwitchTopologyParser::BriefEntryContext::ifname() {
  return getRuleContext<SwitchTopologyParser::IfnameContext>(0);
}

tree::TerminalNode* SwitchTopologyParser::BriefEntryContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

SwitchTopologyParser::AddrOrUnassignedContext* SwitchTopologyParser::BriefEntryContext::addrOrUnassigned() {
  return getRuleContext<SwitchTopologyParser::AddrOrUnassignedContext>(0);
}

std::vector<SwitchTopologyParser::BriefFieldContext *> SwitchTopologyParser::BriefEntryContext::briefField() {
  return getRuleContexts<SwitchTopologyParser::BriefFieldContext>();
}

SwitchTopologyParser::BriefFieldContext* SwitchTopologyParser::BriefEntryContext::briefField(size_t i) {
  return getRuleContext<SwitchTopologyParser::BriefFieldContext>(i);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::BriefEntryContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::BriefEntryContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::BriefEntryContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBriefEntry;
}


std::any SwitchTopologyParser::BriefEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBriefEntry(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BriefEntryContext* SwitchTopologyParser::briefEntry() {
  BriefEntryContext *_localctx = _tracker.createInstance<BriefEntryContext>(_ctx, getState());
  enterRule(_localctx, 34, SwitchTopologyParser::RuleBriefEntry);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    size_t alt;
    enterOuterAlt(_localctx, 1);
    setState(165);
    ifname();
    setState(167);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 13, _ctx)) {
    case 1: {
      setState(166);
      addrOrUnassigned();
      break;
    }

    default:
      break;
    }
    setState(172);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 14, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(169);
        briefField(); 
      }
      setState(174);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 14, _ctx);
    }
    setState(178);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0)) {
      setState(175);
      elem();
      setState(180);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(181);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- AddrOrUnassignedContext ------------------------------------------------------------------

SwitchTopologyParser::AddrOrUnassignedContext::AddrOrUnassignedContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::AddrOrUnassignedContext::ADDR() {
  return getToken(SwitchTopologyParser::ADDR, 0);
}

tree::TerminalNode* SwitchTopologyParser::AddrOrUnassignedContext::UNASSIGNED() {
  return getToken(SwitchTopologyParser::UNASSIGNED, 0);
}


size_t SwitchTopologyParser::AddrOrUnassignedContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleAddrOrUnassigned;
}


std::any SwitchTopologyParser::AddrOrUnassignedContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitAddrOrUnassigned(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::AddrOrUnassignedContext* SwitchTopologyParser::addrOrUnassigned() {
  AddrOrUnassignedContext *_localctx = _tracker.createInstance<AddrOrUnassignedContext>(_ctx, getState());
  enterRule(_localctx, 36, SwitchTopologyParser::RuleAddrOrUnassigned);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(183);
    _la = _input->LA(1);
    if (!(_la == SwitchTopologyParser::ADDR

    || _la == SwitchTopologyParser::UNASSIGNED)) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefFieldContext ------------------------------------------------------------------

SwitchTopologyParser::BriefFieldContext::BriefFieldContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::BriefFieldContext::STATUSWORD() {
  return getToken(SwitchTopologyParser::STATUSWORD, 0);
}


size_t SwitchTopologyParser::BriefFieldContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBriefField;
}


std::any SwitchTopologyParser::BriefFieldContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBriefField(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BriefFieldContext* SwitchTopologyParser::briefField() {
  BriefFieldContext *_localctx = _tracker.createInstance<BriefFieldContext>(_ctx, getState());
  enterRule(_localctx, 38, SwitchTopologyParser::RuleBriefField);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(185);
    match(SwitchTopologyParser::STATUSWORD);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortItemContext ------------------------------------------------------------------

SwitchTopologyParser::PortItemContext::PortItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

SwitchTopologyParser::PortEntryContext* SwitchTopologyParser::PortItemContext::portEntry() {
  return getRuleContext<SwitchTopologyParser::PortEntryContext>(0);
}

SwitchTopologyParser::GenericLineContext* SwitchTopologyParser::PortItemContext::genericLine() {
  return getRuleContext<SwitchTopologyParser::GenericLineContext>(0);
}

SwitchTopologyParser::BlankContext* SwitchTopologyParser::PortItemContext::blank() {
  return getRuleContext<SwitchTopologyParser::BlankContext>(0);
}


size_t SwitchTopologyParser::PortItemContext::getRuleIndex() const {
  return SwitchTopologyParser::RulePortItem;
}


std::any SwitchTopologyParser::PortItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitPortItem(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::PortItemContext* SwitchTopologyParser::portItem() {
  PortItemContext *_localctx = _tracker.createInstance<PortItemContext>(_ctx, getState());
  enterRule(_localctx, 40, SwitchTopologyParser::RulePortItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(190);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 16, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(187);
      portEntry();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(188);
      genericLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(189);
      blank();
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortEntryContext ------------------------------------------------------------------

SwitchTopologyParser::PortEntryContext::PortEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::PortEntryContext::INTERFACE() {
  return getToken(SwitchTopologyParser::INTERFACE, 0);
}

SwitchTopologyParser::IfnameContext* SwitchTopologyParser::PortEntryContext::ifname() {
  return getRuleContext<SwitchTopologyParser::IfnameContext>(0);
}

tree::TerminalNode* SwitchTopologyParser::PortEntryContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::PortEntryContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::PortEntryContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}

tree::TerminalNode* SwitchTopologyParser::PortEntryContext::COLON() {
  return getToken(SwitchTopologyParser::COLON, 0);
}

std::vector<SwitchTopologyParser::KeyWordContext *> SwitchTopologyParser::PortEntryContext::keyWord() {
  return getRuleContexts<SwitchTopologyParser::KeyWordContext>();
}

SwitchTopologyParser::KeyWordContext* SwitchTopologyParser::PortEntryContext::keyWord(size_t i) {
  return getRuleContext<SwitchTopologyParser::KeyWordContext>(i);
}


size_t SwitchTopologyParser::PortEntryContext::getRuleIndex() const {
  return SwitchTopologyParser::RulePortEntry;
}


std::any SwitchTopologyParser::PortEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitPortEntry(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::PortEntryContext* SwitchTopologyParser::portEntry() {
  PortEntryContext *_localctx = _tracker.createInstance<PortEntryContext>(_ctx, getState());
  enterRule(_localctx, 42, SwitchTopologyParser::RulePortEntry);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(216);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 20, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(192);
      match(SwitchTopologyParser::INTERFACE);
      setState(193);
      ifname();
      setState(197);
      _errHandler->sync(this);
      _la = _input->LA(1);
      while ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 524284) != 0)) {
        setState(194);
        elem();
        setState(199);
        _errHandler->sync(this);
        _la = _input->LA(1);
      }
      setState(200);
      match(SwitchTopologyParser::NEWLINE);
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(203); 
      _errHandler->sync(this);
      _la = _input->LA(1);
      do {
        setState(202);
        keyWord();
        setState(205); 
        _errHandler->sync(this);
        _la = _input->LA(1);
      } while ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 15448) != 0));
      setState(207);
      match(SwitchTopologyParser::COLON);
      setState(211);
      _errHandler->sync(this);
      _la = _input->LA(1);
      while ((((_la & ~ 0x3fULL) == 0) &&
        ((1ULL << _la) & 524284) != 0)) {
        setState(208);
        elem();
        setState(213);
        _errHandler->sync(this);
        _la = _input->LA(1);
      }
      setState(214);
      match(SwitchTopologyParser::NEWLINE);
      break;
    }

    default:
      break;
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfnameContext ------------------------------------------------------------------

SwitchTopologyParser::IfnameContext::IfnameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::IfnameContext::IFNAME() {
  return getToken(SwitchTopologyParser::IFNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::IfnameContext::ATTRWORD() {
  return getToken(SwitchTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* SwitchTopologyParser::IfnameContext::PORTNAME() {
  return getToken(SwitchTopologyParser::PORTNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::IfnameContext::IDENT() {
  return getToken(SwitchTopologyParser::IDENT, 0);
}


size_t SwitchTopologyParser::IfnameContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleIfname;
}


std::any SwitchTopologyParser::IfnameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitIfname(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::IfnameContext* SwitchTopologyParser::ifname() {
  IfnameContext *_localctx = _tracker.createInstance<IfnameContext>(_ctx, getState());
  enterRule(_localctx, 44, SwitchTopologyParser::RuleIfname);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(218);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 14464) != 0))) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- KeyWordContext ------------------------------------------------------------------

SwitchTopologyParser::KeyWordContext::KeyWordContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::ATTRWORD() {
  return getToken(SwitchTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::IFNAME() {
  return getToken(SwitchTopologyParser::IFNAME, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::IDENT() {
  return getToken(SwitchTopologyParser::IDENT, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::VLAN() {
  return getToken(SwitchTopologyParser::VLAN, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::INTERFACE() {
  return getToken(SwitchTopologyParser::INTERFACE, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::STATUSWORD() {
  return getToken(SwitchTopologyParser::STATUSWORD, 0);
}

tree::TerminalNode* SwitchTopologyParser::KeyWordContext::NUMBER() {
  return getToken(SwitchTopologyParser::NUMBER, 0);
}


size_t SwitchTopologyParser::KeyWordContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleKeyWord;
}


std::any SwitchTopologyParser::KeyWordContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitKeyWord(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::KeyWordContext* SwitchTopologyParser::keyWord() {
  KeyWordContext *_localctx = _tracker.createInstance<KeyWordContext>(_ctx, getState());
  enterRule(_localctx, 46, SwitchTopologyParser::RuleKeyWord);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(220);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 15448) != 0))) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- GenericLineContext ------------------------------------------------------------------

SwitchTopologyParser::GenericLineContext::GenericLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::GenericLineContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}

std::vector<SwitchTopologyParser::ElemContext *> SwitchTopologyParser::GenericLineContext::elem() {
  return getRuleContexts<SwitchTopologyParser::ElemContext>();
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::GenericLineContext::elem(size_t i) {
  return getRuleContext<SwitchTopologyParser::ElemContext>(i);
}


size_t SwitchTopologyParser::GenericLineContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleGenericLine;
}


std::any SwitchTopologyParser::GenericLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitGenericLine(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::GenericLineContext* SwitchTopologyParser::genericLine() {
  GenericLineContext *_localctx = _tracker.createInstance<GenericLineContext>(_ctx, getState());
  enterRule(_localctx, 48, SwitchTopologyParser::RuleGenericLine);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(223); 
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(222);
      elem();
      setState(225); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 524284) != 0));
    setState(227);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlankContext ------------------------------------------------------------------

SwitchTopologyParser::BlankContext::BlankContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::BlankContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}


size_t SwitchTopologyParser::BlankContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleBlank;
}


std::any SwitchTopologyParser::BlankContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitBlank(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::BlankContext* SwitchTopologyParser::blank() {
  BlankContext *_localctx = _tracker.createInstance<BlankContext>(_ctx, getState());
  enterRule(_localctx, 50, SwitchTopologyParser::RuleBlank);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(229);
    match(SwitchTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ElemContext ------------------------------------------------------------------

SwitchTopologyParser::ElemContext::ElemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* SwitchTopologyParser::ElemContext::NEWLINE() {
  return getToken(SwitchTopologyParser::NEWLINE, 0);
}


size_t SwitchTopologyParser::ElemContext::getRuleIndex() const {
  return SwitchTopologyParser::RuleElem;
}


std::any SwitchTopologyParser::ElemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<SwitchTopologyVisitor*>(visitor))
    return parserVisitor->visitElem(this);
  else
    return visitor->visitChildren(this);
}

SwitchTopologyParser::ElemContext* SwitchTopologyParser::elem() {
  ElemContext *_localctx = _tracker.createInstance<ElemContext>(_ctx, getState());
  enterRule(_localctx, 52, SwitchTopologyParser::RuleElem);
  size_t _la = 0;

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(231);
    _la = _input->LA(1);
    if (_la == 0 || _la == Token::EOF || (_la == SwitchTopologyParser::NEWLINE)) {
    _errHandler->recoverInline(this);
    }
    else {
      _errHandler->reportMatch(this);
      consume();
    }
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

void SwitchTopologyParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  switchtopologyParserInitialize();
#else
  ::antlr4::internal::call_once(switchtopologyParserOnceFlag, switchtopologyParserInitialize);
#endif
}
