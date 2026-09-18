
// Generated from grammar/SwitchTopology.g4 by ANTLR 4.13.2


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
      "portItem", "portEntry", "ifname", "genericLine", "blank", "elem"
    },
    std::vector<std::string>{
      "", "", "", "'VLAN'", "", "", "", "", "'unassigned'", "", "", "", 
      "", "", "','", "':'", "'/'"
    },
    std::vector<std::string>{
      "", "NEWLINE", "WS", "VLAN", "INTERFACE", "ADDR", "NUMBER", "PORTNAME", 
      "UNASSIGNED", "DASHES", "STATUSWORD", "IFNAME", "ATTRWORD", "IDENT", 
      "COMMA", "COLON", "SLASH"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,16,225,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,2,19,7,19,2,20,7,20,2,21,7,
  	21,2,22,7,22,2,23,7,23,2,24,7,24,2,25,7,25,1,0,5,0,54,8,0,10,0,12,0,57,
  	9,0,1,0,1,0,1,1,5,1,62,8,1,10,1,12,1,65,9,1,1,1,1,1,1,2,5,2,70,8,2,10,
  	2,12,2,73,9,2,1,2,1,2,1,3,5,3,78,8,3,10,3,12,3,81,9,3,1,3,1,3,1,4,4,4,
  	86,8,4,11,4,12,4,87,1,4,1,4,1,5,1,5,1,5,1,5,1,5,3,5,97,8,5,1,6,1,6,5,
  	6,101,8,6,10,6,12,6,104,9,6,1,6,1,6,1,7,1,7,1,7,1,7,3,7,112,8,7,1,7,5,
  	7,115,8,7,10,7,12,7,118,9,7,1,7,1,7,1,8,1,8,5,8,124,8,8,10,8,12,8,127,
  	9,8,1,8,1,8,1,9,1,9,1,10,1,10,1,11,1,11,1,12,1,12,1,12,5,12,140,8,12,
  	10,12,12,12,143,9,12,1,13,1,13,1,14,1,14,1,15,1,15,1,15,1,15,3,15,153,
  	8,15,1,16,1,16,5,16,157,8,16,10,16,12,16,160,9,16,1,16,1,16,1,17,1,17,
  	3,17,166,8,17,1,17,5,17,169,8,17,10,17,12,17,172,9,17,1,17,5,17,175,8,
  	17,10,17,12,17,178,9,17,1,17,1,17,1,18,1,18,1,19,1,19,1,20,1,20,1,20,
  	3,20,189,8,20,1,21,1,21,1,21,5,21,194,8,21,10,21,12,21,197,9,21,1,21,
  	1,21,1,21,1,21,1,21,5,21,204,8,21,10,21,12,21,207,9,21,1,21,3,21,210,
  	8,21,1,22,1,22,1,23,4,23,215,8,23,11,23,12,23,216,1,23,1,23,1,24,1,24,
  	1,25,1,25,1,25,0,0,26,0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,34,
  	36,38,40,42,44,46,48,50,0,5,3,0,6,7,11,11,13,13,2,0,6,7,11,13,2,0,5,5,
  	8,8,3,0,7,7,11,11,13,13,1,0,1,1,225,0,55,1,0,0,0,2,63,1,0,0,0,4,71,1,
  	0,0,0,6,79,1,0,0,0,8,85,1,0,0,0,10,96,1,0,0,0,12,98,1,0,0,0,14,107,1,
  	0,0,0,16,121,1,0,0,0,18,130,1,0,0,0,20,132,1,0,0,0,22,134,1,0,0,0,24,
  	136,1,0,0,0,26,144,1,0,0,0,28,146,1,0,0,0,30,152,1,0,0,0,32,154,1,0,0,
  	0,34,163,1,0,0,0,36,181,1,0,0,0,38,183,1,0,0,0,40,188,1,0,0,0,42,209,
  	1,0,0,0,44,211,1,0,0,0,46,214,1,0,0,0,48,220,1,0,0,0,50,222,1,0,0,0,52,
  	54,3,8,4,0,53,52,1,0,0,0,54,57,1,0,0,0,55,53,1,0,0,0,55,56,1,0,0,0,56,
  	58,1,0,0,0,57,55,1,0,0,0,58,59,5,0,0,1,59,1,1,0,0,0,60,62,3,10,5,0,61,
  	60,1,0,0,0,62,65,1,0,0,0,63,61,1,0,0,0,63,64,1,0,0,0,64,66,1,0,0,0,65,
  	63,1,0,0,0,66,67,5,0,0,1,67,3,1,0,0,0,68,70,3,30,15,0,69,68,1,0,0,0,70,
  	73,1,0,0,0,71,69,1,0,0,0,71,72,1,0,0,0,72,74,1,0,0,0,73,71,1,0,0,0,74,
  	75,5,0,0,1,75,5,1,0,0,0,76,78,3,40,20,0,77,76,1,0,0,0,78,81,1,0,0,0,79,
  	77,1,0,0,0,79,80,1,0,0,0,80,82,1,0,0,0,81,79,1,0,0,0,82,83,5,0,0,1,83,
  	7,1,0,0,0,84,86,3,50,25,0,85,84,1,0,0,0,86,87,1,0,0,0,87,85,1,0,0,0,87,
  	88,1,0,0,0,88,89,1,0,0,0,89,90,5,1,0,0,90,9,1,0,0,0,91,97,3,12,6,0,92,
  	97,3,14,7,0,93,97,3,16,8,0,94,97,3,46,23,0,95,97,3,48,24,0,96,91,1,0,
  	0,0,96,92,1,0,0,0,96,93,1,0,0,0,96,94,1,0,0,0,96,95,1,0,0,0,97,11,1,0,
  	0,0,98,102,5,3,0,0,99,101,3,50,25,0,100,99,1,0,0,0,101,104,1,0,0,0,102,
  	100,1,0,0,0,102,103,1,0,0,0,103,105,1,0,0,0,104,102,1,0,0,0,105,106,5,
  	1,0,0,106,13,1,0,0,0,107,108,3,18,9,0,108,109,3,20,10,0,109,111,3,22,
  	11,0,110,112,3,24,12,0,111,110,1,0,0,0,111,112,1,0,0,0,112,116,1,0,0,
  	0,113,115,3,50,25,0,114,113,1,0,0,0,115,118,1,0,0,0,116,114,1,0,0,0,116,
  	117,1,0,0,0,117,119,1,0,0,0,118,116,1,0,0,0,119,120,5,1,0,0,120,15,1,
  	0,0,0,121,125,5,9,0,0,122,124,3,50,25,0,123,122,1,0,0,0,124,127,1,0,0,
  	0,125,123,1,0,0,0,125,126,1,0,0,0,126,128,1,0,0,0,127,125,1,0,0,0,128,
  	129,5,1,0,0,129,17,1,0,0,0,130,131,5,6,0,0,131,19,1,0,0,0,132,133,3,28,
  	14,0,133,21,1,0,0,0,134,135,5,10,0,0,135,23,1,0,0,0,136,141,3,26,13,0,
  	137,138,5,14,0,0,138,140,3,26,13,0,139,137,1,0,0,0,140,143,1,0,0,0,141,
  	139,1,0,0,0,141,142,1,0,0,0,142,25,1,0,0,0,143,141,1,0,0,0,144,145,7,
  	0,0,0,145,27,1,0,0,0,146,147,7,1,0,0,147,29,1,0,0,0,148,153,3,32,16,0,
  	149,153,3,34,17,0,150,153,3,46,23,0,151,153,3,48,24,0,152,148,1,0,0,0,
  	152,149,1,0,0,0,152,150,1,0,0,0,152,151,1,0,0,0,153,31,1,0,0,0,154,158,
  	5,4,0,0,155,157,3,50,25,0,156,155,1,0,0,0,157,160,1,0,0,0,158,156,1,0,
  	0,0,158,159,1,0,0,0,159,161,1,0,0,0,160,158,1,0,0,0,161,162,5,1,0,0,162,
  	33,1,0,0,0,163,165,3,44,22,0,164,166,3,36,18,0,165,164,1,0,0,0,165,166,
  	1,0,0,0,166,170,1,0,0,0,167,169,3,38,19,0,168,167,1,0,0,0,169,172,1,0,
  	0,0,170,168,1,0,0,0,170,171,1,0,0,0,171,176,1,0,0,0,172,170,1,0,0,0,173,
  	175,3,50,25,0,174,173,1,0,0,0,175,178,1,0,0,0,176,174,1,0,0,0,176,177,
  	1,0,0,0,177,179,1,0,0,0,178,176,1,0,0,0,179,180,5,1,0,0,180,35,1,0,0,
  	0,181,182,7,2,0,0,182,37,1,0,0,0,183,184,5,10,0,0,184,39,1,0,0,0,185,
  	189,3,42,21,0,186,189,3,46,23,0,187,189,3,48,24,0,188,185,1,0,0,0,188,
  	186,1,0,0,0,188,187,1,0,0,0,189,41,1,0,0,0,190,191,5,4,0,0,191,195,3,
  	44,22,0,192,194,3,50,25,0,193,192,1,0,0,0,194,197,1,0,0,0,195,193,1,0,
  	0,0,195,196,1,0,0,0,196,198,1,0,0,0,197,195,1,0,0,0,198,199,5,1,0,0,199,
  	210,1,0,0,0,200,201,5,12,0,0,201,205,5,15,0,0,202,204,3,50,25,0,203,202,
  	1,0,0,0,204,207,1,0,0,0,205,203,1,0,0,0,205,206,1,0,0,0,206,208,1,0,0,
  	0,207,205,1,0,0,0,208,210,5,1,0,0,209,190,1,0,0,0,209,200,1,0,0,0,210,
  	43,1,0,0,0,211,212,7,3,0,0,212,45,1,0,0,0,213,215,3,50,25,0,214,213,1,
  	0,0,0,215,216,1,0,0,0,216,214,1,0,0,0,216,217,1,0,0,0,217,218,1,0,0,0,
  	218,219,5,1,0,0,219,47,1,0,0,0,220,221,5,1,0,0,221,49,1,0,0,0,222,223,
  	8,4,0,0,223,51,1,0,0,0,21,55,63,71,79,87,96,102,111,116,125,141,152,158,
  	165,170,176,188,195,205,209,216
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
    setState(55);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(52);
      configLine();
      setState(57);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(58);
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
    setState(63);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131070) != 0)) {
      setState(60);
      vlanItem();
      setState(65);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(66);
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
    setState(71);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131070) != 0)) {
      setState(68);
      briefItem();
      setState(73);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(74);
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
    setState(79);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131070) != 0)) {
      setState(76);
      portItem();
      setState(81);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(82);
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
    setState(85); 
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(84);
      elem();
      setState(87); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0));
    setState(89);
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
    setState(96);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 5, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(91);
      vlanHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(92);
      vlanEntry();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(93);
      vlanSeparator();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(94);
      genericLine();
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(95);
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
    setState(98);
    match(SwitchTopologyParser::VLAN);
    setState(102);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(99);
      elem();
      setState(104);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(105);
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
    setState(107);
    vlanId();
    setState(108);
    vlanName();
    setState(109);
    vlanStatus();
    setState(111);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 7, _ctx)) {
    case 1: {
      setState(110);
      portList();
      break;
    }

    default:
      break;
    }
    setState(116);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(113);
      elem();
      setState(118);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(119);
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
    setState(121);
    match(SwitchTopologyParser::DASHES);
    setState(125);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(122);
      elem();
      setState(127);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(128);
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
    setState(130);
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
    setState(132);
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
    setState(134);
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
    setState(136);
    portToken();
    setState(141);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 10, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(137);
        match(SwitchTopologyParser::COMMA);
        setState(138);
        portToken(); 
      }
      setState(143);
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
    setState(144);
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
    setState(146);
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
    setState(152);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 11, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(148);
      briefHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(149);
      briefEntry();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(150);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(151);
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
    setState(154);
    match(SwitchTopologyParser::INTERFACE);
    setState(158);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(155);
      elem();
      setState(160);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(161);
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
    setState(163);
    ifname();
    setState(165);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 13, _ctx)) {
    case 1: {
      setState(164);
      addrOrUnassigned();
      break;
    }

    default:
      break;
    }
    setState(170);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 14, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(167);
        briefField(); 
      }
      setState(172);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 14, _ctx);
    }
    setState(176);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0)) {
      setState(173);
      elem();
      setState(178);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(179);
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
    setState(181);
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
    setState(183);
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
    setState(188);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 16, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(185);
      portEntry();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(186);
      genericLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(187);
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

tree::TerminalNode* SwitchTopologyParser::PortEntryContext::ATTRWORD() {
  return getToken(SwitchTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* SwitchTopologyParser::PortEntryContext::COLON() {
  return getToken(SwitchTopologyParser::COLON, 0);
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
    setState(209);
    _errHandler->sync(this);
    switch (_input->LA(1)) {
      case SwitchTopologyParser::INTERFACE: {
        enterOuterAlt(_localctx, 1);
        setState(190);
        match(SwitchTopologyParser::INTERFACE);
        setState(191);
        ifname();
        setState(195);
        _errHandler->sync(this);
        _la = _input->LA(1);
        while ((((_la & ~ 0x3fULL) == 0) &&
          ((1ULL << _la) & 131068) != 0)) {
          setState(192);
          elem();
          setState(197);
          _errHandler->sync(this);
          _la = _input->LA(1);
        }
        setState(198);
        match(SwitchTopologyParser::NEWLINE);
        break;
      }

      case SwitchTopologyParser::ATTRWORD: {
        enterOuterAlt(_localctx, 2);
        setState(200);
        match(SwitchTopologyParser::ATTRWORD);
        setState(201);
        match(SwitchTopologyParser::COLON);
        setState(205);
        _errHandler->sync(this);
        _la = _input->LA(1);
        while ((((_la & ~ 0x3fULL) == 0) &&
          ((1ULL << _la) & 131068) != 0)) {
          setState(202);
          elem();
          setState(207);
          _errHandler->sync(this);
          _la = _input->LA(1);
        }
        setState(208);
        match(SwitchTopologyParser::NEWLINE);
        break;
      }

    default:
      throw NoViableAltException(this);
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
    setState(211);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 10368) != 0))) {
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
  enterRule(_localctx, 46, SwitchTopologyParser::RuleGenericLine);
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
    setState(214); 
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(213);
      elem();
      setState(216); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 131068) != 0));
    setState(218);
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
  enterRule(_localctx, 48, SwitchTopologyParser::RuleBlank);

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
  enterRule(_localctx, 50, SwitchTopologyParser::RuleElem);
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
    setState(222);
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
