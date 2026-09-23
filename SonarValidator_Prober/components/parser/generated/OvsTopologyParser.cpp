
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/OvsTopology.g4 by ANTLR 4.13.1


#include "OvsTopologyVisitor.h"

#include "OvsTopologyParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct OvsTopologyParserStaticData final {
  OvsTopologyParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  OvsTopologyParserStaticData(const OvsTopologyParserStaticData&) = delete;
  OvsTopologyParserStaticData(OvsTopologyParserStaticData&&) = delete;
  OvsTopologyParserStaticData& operator=(const OvsTopologyParserStaticData&) = delete;
  OvsTopologyParserStaticData& operator=(OvsTopologyParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag ovstopologyParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
OvsTopologyParserStaticData *ovstopologyParserStaticData = nullptr;

void ovstopologyParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (ovstopologyParserStaticData != nullptr) {
    return;
  }
#else
  assert(ovstopologyParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<OvsTopologyParserStaticData>(
    std::vector<std::string>{
      "showDocument", "listDocument", "flowDocument", "showVlanDocument", 
      "showItem", "bridgeLine", "portLine", "ifaceLine", "attrLine", "nameToken", 
      "listItem", "listRecord", "recordSep", "flowItem", "flowLine", "genericLine", 
      "blank", "elem"
    },
    std::vector<std::string>{
      "", "", "", "'Bridge'", "'Port'", "'Interface'", "", "", "", "", "", 
      "':'"
    },
    std::vector<std::string>{
      "", "NEWLINE", "WS", "BRIDGE", "PORT", "INTERFACE", "FLOWTOKEN", "ATTRWORD", 
      "QUOTED", "DASHES", "WORD", "COLON"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,11,167,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,1,0,5,0,38,8,0,10,0,12,0,41,9,0,1,0,
  	1,0,1,1,5,1,46,8,1,10,1,12,1,49,9,1,1,1,1,1,1,2,5,2,54,8,2,10,2,12,2,
  	57,9,2,1,2,1,2,1,3,5,3,62,8,3,10,3,12,3,65,9,3,1,3,1,3,1,4,1,4,1,4,1,
  	4,1,4,1,4,3,4,75,8,4,1,5,1,5,1,5,5,5,80,8,5,10,5,12,5,83,9,5,1,5,1,5,
  	1,6,1,6,1,6,5,6,90,8,6,10,6,12,6,93,9,6,1,6,1,6,1,7,1,7,1,7,5,7,100,8,
  	7,10,7,12,7,103,9,7,1,7,1,7,1,8,1,8,1,8,5,8,110,8,8,10,8,12,8,113,9,8,
  	1,8,1,8,1,9,1,9,1,10,1,10,1,10,1,10,3,10,123,8,10,1,11,1,11,1,11,5,11,
  	128,8,11,10,11,12,11,131,9,11,1,11,1,11,1,12,1,12,1,12,1,13,1,13,1,13,
  	3,13,141,8,13,1,14,4,14,144,8,14,11,14,12,14,145,1,14,5,14,149,8,14,10,
  	14,12,14,152,9,14,1,14,1,14,1,15,4,15,157,8,15,11,15,12,15,158,1,15,1,
  	15,1,16,1,16,1,17,1,17,1,17,0,0,18,0,2,4,6,8,10,12,14,16,18,20,22,24,
  	26,28,30,32,34,0,2,2,0,7,8,10,10,1,0,1,1,170,0,39,1,0,0,0,2,47,1,0,0,
  	0,4,55,1,0,0,0,6,63,1,0,0,0,8,74,1,0,0,0,10,76,1,0,0,0,12,86,1,0,0,0,
  	14,96,1,0,0,0,16,106,1,0,0,0,18,116,1,0,0,0,20,122,1,0,0,0,22,124,1,0,
  	0,0,24,134,1,0,0,0,26,140,1,0,0,0,28,143,1,0,0,0,30,156,1,0,0,0,32,162,
  	1,0,0,0,34,164,1,0,0,0,36,38,3,8,4,0,37,36,1,0,0,0,38,41,1,0,0,0,39,37,
  	1,0,0,0,39,40,1,0,0,0,40,42,1,0,0,0,41,39,1,0,0,0,42,43,5,0,0,1,43,1,
  	1,0,0,0,44,46,3,20,10,0,45,44,1,0,0,0,46,49,1,0,0,0,47,45,1,0,0,0,47,
  	48,1,0,0,0,48,50,1,0,0,0,49,47,1,0,0,0,50,51,5,0,0,1,51,3,1,0,0,0,52,
  	54,3,26,13,0,53,52,1,0,0,0,54,57,1,0,0,0,55,53,1,0,0,0,55,56,1,0,0,0,
  	56,58,1,0,0,0,57,55,1,0,0,0,58,59,5,0,0,1,59,5,1,0,0,0,60,62,3,8,4,0,
  	61,60,1,0,0,0,62,65,1,0,0,0,63,61,1,0,0,0,63,64,1,0,0,0,64,66,1,0,0,0,
  	65,63,1,0,0,0,66,67,5,0,0,1,67,7,1,0,0,0,68,75,3,10,5,0,69,75,3,12,6,
  	0,70,75,3,14,7,0,71,75,3,16,8,0,72,75,3,30,15,0,73,75,3,32,16,0,74,68,
  	1,0,0,0,74,69,1,0,0,0,74,70,1,0,0,0,74,71,1,0,0,0,74,72,1,0,0,0,74,73,
  	1,0,0,0,75,9,1,0,0,0,76,77,5,3,0,0,77,81,3,18,9,0,78,80,3,34,17,0,79,
  	78,1,0,0,0,80,83,1,0,0,0,81,79,1,0,0,0,81,82,1,0,0,0,82,84,1,0,0,0,83,
  	81,1,0,0,0,84,85,5,1,0,0,85,11,1,0,0,0,86,87,5,4,0,0,87,91,3,18,9,0,88,
  	90,3,34,17,0,89,88,1,0,0,0,90,93,1,0,0,0,91,89,1,0,0,0,91,92,1,0,0,0,
  	92,94,1,0,0,0,93,91,1,0,0,0,94,95,5,1,0,0,95,13,1,0,0,0,96,97,5,5,0,0,
  	97,101,3,18,9,0,98,100,3,34,17,0,99,98,1,0,0,0,100,103,1,0,0,0,101,99,
  	1,0,0,0,101,102,1,0,0,0,102,104,1,0,0,0,103,101,1,0,0,0,104,105,5,1,0,
  	0,105,15,1,0,0,0,106,107,5,7,0,0,107,111,5,11,0,0,108,110,3,34,17,0,109,
  	108,1,0,0,0,110,113,1,0,0,0,111,109,1,0,0,0,111,112,1,0,0,0,112,114,1,
  	0,0,0,113,111,1,0,0,0,114,115,5,1,0,0,115,17,1,0,0,0,116,117,7,0,0,0,
  	117,19,1,0,0,0,118,123,3,22,11,0,119,123,3,24,12,0,120,123,3,30,15,0,
  	121,123,3,32,16,0,122,118,1,0,0,0,122,119,1,0,0,0,122,120,1,0,0,0,122,
  	121,1,0,0,0,123,21,1,0,0,0,124,125,5,7,0,0,125,129,5,11,0,0,126,128,3,
  	34,17,0,127,126,1,0,0,0,128,131,1,0,0,0,129,127,1,0,0,0,129,130,1,0,0,
  	0,130,132,1,0,0,0,131,129,1,0,0,0,132,133,5,1,0,0,133,23,1,0,0,0,134,
  	135,5,9,0,0,135,136,5,1,0,0,136,25,1,0,0,0,137,141,3,28,14,0,138,141,
  	3,30,15,0,139,141,3,32,16,0,140,137,1,0,0,0,140,138,1,0,0,0,140,139,1,
  	0,0,0,141,27,1,0,0,0,142,144,5,6,0,0,143,142,1,0,0,0,144,145,1,0,0,0,
  	145,143,1,0,0,0,145,146,1,0,0,0,146,150,1,0,0,0,147,149,3,34,17,0,148,
  	147,1,0,0,0,149,152,1,0,0,0,150,148,1,0,0,0,150,151,1,0,0,0,151,153,1,
  	0,0,0,152,150,1,0,0,0,153,154,5,1,0,0,154,29,1,0,0,0,155,157,3,34,17,
  	0,156,155,1,0,0,0,157,158,1,0,0,0,158,156,1,0,0,0,158,159,1,0,0,0,159,
  	160,1,0,0,0,160,161,5,1,0,0,161,31,1,0,0,0,162,163,5,1,0,0,163,33,1,0,
  	0,0,164,165,8,1,0,0,165,35,1,0,0,0,15,39,47,55,63,74,81,91,101,111,122,
  	129,140,145,150,158
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  ovstopologyParserStaticData = staticData.release();
}

}

OvsTopologyParser::OvsTopologyParser(TokenStream *input) : OvsTopologyParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

OvsTopologyParser::OvsTopologyParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  OvsTopologyParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *ovstopologyParserStaticData->atn, ovstopologyParserStaticData->decisionToDFA, ovstopologyParserStaticData->sharedContextCache, options);
}

OvsTopologyParser::~OvsTopologyParser() {
  delete _interpreter;
}

const atn::ATN& OvsTopologyParser::getATN() const {
  return *ovstopologyParserStaticData->atn;
}

std::string OvsTopologyParser::getGrammarFileName() const {
  return "OvsTopology.g4";
}

const std::vector<std::string>& OvsTopologyParser::getRuleNames() const {
  return ovstopologyParserStaticData->ruleNames;
}

const dfa::Vocabulary& OvsTopologyParser::getVocabulary() const {
  return ovstopologyParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView OvsTopologyParser::getSerializedATN() const {
  return ovstopologyParserStaticData->serializedATN;
}


//----------------- ShowDocumentContext ------------------------------------------------------------------

OvsTopologyParser::ShowDocumentContext::ShowDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::ShowDocumentContext::EOF() {
  return getToken(OvsTopologyParser::EOF, 0);
}

std::vector<OvsTopologyParser::ShowItemContext *> OvsTopologyParser::ShowDocumentContext::showItem() {
  return getRuleContexts<OvsTopologyParser::ShowItemContext>();
}

OvsTopologyParser::ShowItemContext* OvsTopologyParser::ShowDocumentContext::showItem(size_t i) {
  return getRuleContext<OvsTopologyParser::ShowItemContext>(i);
}


size_t OvsTopologyParser::ShowDocumentContext::getRuleIndex() const {
  return OvsTopologyParser::RuleShowDocument;
}


std::any OvsTopologyParser::ShowDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitShowDocument(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ShowDocumentContext* OvsTopologyParser::showDocument() {
  ShowDocumentContext *_localctx = _tracker.createInstance<ShowDocumentContext>(_ctx, getState());
  enterRule(_localctx, 0, OvsTopologyParser::RuleShowDocument);
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
    setState(39);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4094) != 0)) {
      setState(36);
      showItem();
      setState(41);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(42);
    match(OvsTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ListDocumentContext ------------------------------------------------------------------

OvsTopologyParser::ListDocumentContext::ListDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::ListDocumentContext::EOF() {
  return getToken(OvsTopologyParser::EOF, 0);
}

std::vector<OvsTopologyParser::ListItemContext *> OvsTopologyParser::ListDocumentContext::listItem() {
  return getRuleContexts<OvsTopologyParser::ListItemContext>();
}

OvsTopologyParser::ListItemContext* OvsTopologyParser::ListDocumentContext::listItem(size_t i) {
  return getRuleContext<OvsTopologyParser::ListItemContext>(i);
}


size_t OvsTopologyParser::ListDocumentContext::getRuleIndex() const {
  return OvsTopologyParser::RuleListDocument;
}


std::any OvsTopologyParser::ListDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitListDocument(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ListDocumentContext* OvsTopologyParser::listDocument() {
  ListDocumentContext *_localctx = _tracker.createInstance<ListDocumentContext>(_ctx, getState());
  enterRule(_localctx, 2, OvsTopologyParser::RuleListDocument);
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
    setState(47);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4094) != 0)) {
      setState(44);
      listItem();
      setState(49);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(50);
    match(OvsTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- FlowDocumentContext ------------------------------------------------------------------

OvsTopologyParser::FlowDocumentContext::FlowDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::FlowDocumentContext::EOF() {
  return getToken(OvsTopologyParser::EOF, 0);
}

std::vector<OvsTopologyParser::FlowItemContext *> OvsTopologyParser::FlowDocumentContext::flowItem() {
  return getRuleContexts<OvsTopologyParser::FlowItemContext>();
}

OvsTopologyParser::FlowItemContext* OvsTopologyParser::FlowDocumentContext::flowItem(size_t i) {
  return getRuleContext<OvsTopologyParser::FlowItemContext>(i);
}


size_t OvsTopologyParser::FlowDocumentContext::getRuleIndex() const {
  return OvsTopologyParser::RuleFlowDocument;
}


std::any OvsTopologyParser::FlowDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitFlowDocument(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::FlowDocumentContext* OvsTopologyParser::flowDocument() {
  FlowDocumentContext *_localctx = _tracker.createInstance<FlowDocumentContext>(_ctx, getState());
  enterRule(_localctx, 4, OvsTopologyParser::RuleFlowDocument);
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
      ((1ULL << _la) & 4094) != 0)) {
      setState(52);
      flowItem();
      setState(57);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(58);
    match(OvsTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ShowVlanDocumentContext ------------------------------------------------------------------

OvsTopologyParser::ShowVlanDocumentContext::ShowVlanDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::ShowVlanDocumentContext::EOF() {
  return getToken(OvsTopologyParser::EOF, 0);
}

std::vector<OvsTopologyParser::ShowItemContext *> OvsTopologyParser::ShowVlanDocumentContext::showItem() {
  return getRuleContexts<OvsTopologyParser::ShowItemContext>();
}

OvsTopologyParser::ShowItemContext* OvsTopologyParser::ShowVlanDocumentContext::showItem(size_t i) {
  return getRuleContext<OvsTopologyParser::ShowItemContext>(i);
}


size_t OvsTopologyParser::ShowVlanDocumentContext::getRuleIndex() const {
  return OvsTopologyParser::RuleShowVlanDocument;
}


std::any OvsTopologyParser::ShowVlanDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitShowVlanDocument(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ShowVlanDocumentContext* OvsTopologyParser::showVlanDocument() {
  ShowVlanDocumentContext *_localctx = _tracker.createInstance<ShowVlanDocumentContext>(_ctx, getState());
  enterRule(_localctx, 6, OvsTopologyParser::RuleShowVlanDocument);
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
      ((1ULL << _la) & 4094) != 0)) {
      setState(60);
      showItem();
      setState(65);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(66);
    match(OvsTopologyParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ShowItemContext ------------------------------------------------------------------

OvsTopologyParser::ShowItemContext::ShowItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

OvsTopologyParser::BridgeLineContext* OvsTopologyParser::ShowItemContext::bridgeLine() {
  return getRuleContext<OvsTopologyParser::BridgeLineContext>(0);
}

OvsTopologyParser::PortLineContext* OvsTopologyParser::ShowItemContext::portLine() {
  return getRuleContext<OvsTopologyParser::PortLineContext>(0);
}

OvsTopologyParser::IfaceLineContext* OvsTopologyParser::ShowItemContext::ifaceLine() {
  return getRuleContext<OvsTopologyParser::IfaceLineContext>(0);
}

OvsTopologyParser::AttrLineContext* OvsTopologyParser::ShowItemContext::attrLine() {
  return getRuleContext<OvsTopologyParser::AttrLineContext>(0);
}

OvsTopologyParser::GenericLineContext* OvsTopologyParser::ShowItemContext::genericLine() {
  return getRuleContext<OvsTopologyParser::GenericLineContext>(0);
}

OvsTopologyParser::BlankContext* OvsTopologyParser::ShowItemContext::blank() {
  return getRuleContext<OvsTopologyParser::BlankContext>(0);
}


size_t OvsTopologyParser::ShowItemContext::getRuleIndex() const {
  return OvsTopologyParser::RuleShowItem;
}


std::any OvsTopologyParser::ShowItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitShowItem(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ShowItemContext* OvsTopologyParser::showItem() {
  ShowItemContext *_localctx = _tracker.createInstance<ShowItemContext>(_ctx, getState());
  enterRule(_localctx, 8, OvsTopologyParser::RuleShowItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(74);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 4, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(68);
      bridgeLine();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(69);
      portLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(70);
      ifaceLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(71);
      attrLine();
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(72);
      genericLine();
      break;
    }

    case 6: {
      enterOuterAlt(_localctx, 6);
      setState(73);
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

//----------------- BridgeLineContext ------------------------------------------------------------------

OvsTopologyParser::BridgeLineContext::BridgeLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::BridgeLineContext::BRIDGE() {
  return getToken(OvsTopologyParser::BRIDGE, 0);
}

OvsTopologyParser::NameTokenContext* OvsTopologyParser::BridgeLineContext::nameToken() {
  return getRuleContext<OvsTopologyParser::NameTokenContext>(0);
}

tree::TerminalNode* OvsTopologyParser::BridgeLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::BridgeLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::BridgeLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::BridgeLineContext::getRuleIndex() const {
  return OvsTopologyParser::RuleBridgeLine;
}


std::any OvsTopologyParser::BridgeLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitBridgeLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::BridgeLineContext* OvsTopologyParser::bridgeLine() {
  BridgeLineContext *_localctx = _tracker.createInstance<BridgeLineContext>(_ctx, getState());
  enterRule(_localctx, 10, OvsTopologyParser::RuleBridgeLine);
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
    setState(76);
    match(OvsTopologyParser::BRIDGE);
    setState(77);
    nameToken();
    setState(81);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(78);
      elem();
      setState(83);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(84);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- PortLineContext ------------------------------------------------------------------

OvsTopologyParser::PortLineContext::PortLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::PortLineContext::PORT() {
  return getToken(OvsTopologyParser::PORT, 0);
}

OvsTopologyParser::NameTokenContext* OvsTopologyParser::PortLineContext::nameToken() {
  return getRuleContext<OvsTopologyParser::NameTokenContext>(0);
}

tree::TerminalNode* OvsTopologyParser::PortLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::PortLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::PortLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::PortLineContext::getRuleIndex() const {
  return OvsTopologyParser::RulePortLine;
}


std::any OvsTopologyParser::PortLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitPortLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::PortLineContext* OvsTopologyParser::portLine() {
  PortLineContext *_localctx = _tracker.createInstance<PortLineContext>(_ctx, getState());
  enterRule(_localctx, 12, OvsTopologyParser::RulePortLine);
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
    setState(86);
    match(OvsTopologyParser::PORT);
    setState(87);
    nameToken();
    setState(91);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(88);
      elem();
      setState(93);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(94);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfaceLineContext ------------------------------------------------------------------

OvsTopologyParser::IfaceLineContext::IfaceLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::IfaceLineContext::INTERFACE() {
  return getToken(OvsTopologyParser::INTERFACE, 0);
}

OvsTopologyParser::NameTokenContext* OvsTopologyParser::IfaceLineContext::nameToken() {
  return getRuleContext<OvsTopologyParser::NameTokenContext>(0);
}

tree::TerminalNode* OvsTopologyParser::IfaceLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::IfaceLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::IfaceLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::IfaceLineContext::getRuleIndex() const {
  return OvsTopologyParser::RuleIfaceLine;
}


std::any OvsTopologyParser::IfaceLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitIfaceLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::IfaceLineContext* OvsTopologyParser::ifaceLine() {
  IfaceLineContext *_localctx = _tracker.createInstance<IfaceLineContext>(_ctx, getState());
  enterRule(_localctx, 14, OvsTopologyParser::RuleIfaceLine);
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
    setState(96);
    match(OvsTopologyParser::INTERFACE);
    setState(97);
    nameToken();
    setState(101);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(98);
      elem();
      setState(103);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(104);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- AttrLineContext ------------------------------------------------------------------

OvsTopologyParser::AttrLineContext::AttrLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::AttrLineContext::ATTRWORD() {
  return getToken(OvsTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* OvsTopologyParser::AttrLineContext::COLON() {
  return getToken(OvsTopologyParser::COLON, 0);
}

tree::TerminalNode* OvsTopologyParser::AttrLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::AttrLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::AttrLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::AttrLineContext::getRuleIndex() const {
  return OvsTopologyParser::RuleAttrLine;
}


std::any OvsTopologyParser::AttrLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitAttrLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::AttrLineContext* OvsTopologyParser::attrLine() {
  AttrLineContext *_localctx = _tracker.createInstance<AttrLineContext>(_ctx, getState());
  enterRule(_localctx, 16, OvsTopologyParser::RuleAttrLine);
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
    setState(106);
    match(OvsTopologyParser::ATTRWORD);
    setState(107);
    match(OvsTopologyParser::COLON);
    setState(111);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(108);
      elem();
      setState(113);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(114);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- NameTokenContext ------------------------------------------------------------------

OvsTopologyParser::NameTokenContext::NameTokenContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::NameTokenContext::QUOTED() {
  return getToken(OvsTopologyParser::QUOTED, 0);
}

tree::TerminalNode* OvsTopologyParser::NameTokenContext::ATTRWORD() {
  return getToken(OvsTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* OvsTopologyParser::NameTokenContext::WORD() {
  return getToken(OvsTopologyParser::WORD, 0);
}


size_t OvsTopologyParser::NameTokenContext::getRuleIndex() const {
  return OvsTopologyParser::RuleNameToken;
}


std::any OvsTopologyParser::NameTokenContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitNameToken(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::NameTokenContext* OvsTopologyParser::nameToken() {
  NameTokenContext *_localctx = _tracker.createInstance<NameTokenContext>(_ctx, getState());
  enterRule(_localctx, 18, OvsTopologyParser::RuleNameToken);
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
    setState(116);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 1408) != 0))) {
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

//----------------- ListItemContext ------------------------------------------------------------------

OvsTopologyParser::ListItemContext::ListItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

OvsTopologyParser::ListRecordContext* OvsTopologyParser::ListItemContext::listRecord() {
  return getRuleContext<OvsTopologyParser::ListRecordContext>(0);
}

OvsTopologyParser::RecordSepContext* OvsTopologyParser::ListItemContext::recordSep() {
  return getRuleContext<OvsTopologyParser::RecordSepContext>(0);
}

OvsTopologyParser::GenericLineContext* OvsTopologyParser::ListItemContext::genericLine() {
  return getRuleContext<OvsTopologyParser::GenericLineContext>(0);
}

OvsTopologyParser::BlankContext* OvsTopologyParser::ListItemContext::blank() {
  return getRuleContext<OvsTopologyParser::BlankContext>(0);
}


size_t OvsTopologyParser::ListItemContext::getRuleIndex() const {
  return OvsTopologyParser::RuleListItem;
}


std::any OvsTopologyParser::ListItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitListItem(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ListItemContext* OvsTopologyParser::listItem() {
  ListItemContext *_localctx = _tracker.createInstance<ListItemContext>(_ctx, getState());
  enterRule(_localctx, 20, OvsTopologyParser::RuleListItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(122);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 9, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(118);
      listRecord();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(119);
      recordSep();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(120);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(121);
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

//----------------- ListRecordContext ------------------------------------------------------------------

OvsTopologyParser::ListRecordContext::ListRecordContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::ListRecordContext::ATTRWORD() {
  return getToken(OvsTopologyParser::ATTRWORD, 0);
}

tree::TerminalNode* OvsTopologyParser::ListRecordContext::COLON() {
  return getToken(OvsTopologyParser::COLON, 0);
}

tree::TerminalNode* OvsTopologyParser::ListRecordContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::ListRecordContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::ListRecordContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::ListRecordContext::getRuleIndex() const {
  return OvsTopologyParser::RuleListRecord;
}


std::any OvsTopologyParser::ListRecordContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitListRecord(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ListRecordContext* OvsTopologyParser::listRecord() {
  ListRecordContext *_localctx = _tracker.createInstance<ListRecordContext>(_ctx, getState());
  enterRule(_localctx, 22, OvsTopologyParser::RuleListRecord);
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
    setState(124);
    match(OvsTopologyParser::ATTRWORD);
    setState(125);
    match(OvsTopologyParser::COLON);
    setState(129);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(126);
      elem();
      setState(131);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(132);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RecordSepContext ------------------------------------------------------------------

OvsTopologyParser::RecordSepContext::RecordSepContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::RecordSepContext::DASHES() {
  return getToken(OvsTopologyParser::DASHES, 0);
}

tree::TerminalNode* OvsTopologyParser::RecordSepContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}


size_t OvsTopologyParser::RecordSepContext::getRuleIndex() const {
  return OvsTopologyParser::RuleRecordSep;
}


std::any OvsTopologyParser::RecordSepContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitRecordSep(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::RecordSepContext* OvsTopologyParser::recordSep() {
  RecordSepContext *_localctx = _tracker.createInstance<RecordSepContext>(_ctx, getState());
  enterRule(_localctx, 24, OvsTopologyParser::RuleRecordSep);

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
    match(OvsTopologyParser::DASHES);
    setState(135);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- FlowItemContext ------------------------------------------------------------------

OvsTopologyParser::FlowItemContext::FlowItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

OvsTopologyParser::FlowLineContext* OvsTopologyParser::FlowItemContext::flowLine() {
  return getRuleContext<OvsTopologyParser::FlowLineContext>(0);
}

OvsTopologyParser::GenericLineContext* OvsTopologyParser::FlowItemContext::genericLine() {
  return getRuleContext<OvsTopologyParser::GenericLineContext>(0);
}

OvsTopologyParser::BlankContext* OvsTopologyParser::FlowItemContext::blank() {
  return getRuleContext<OvsTopologyParser::BlankContext>(0);
}


size_t OvsTopologyParser::FlowItemContext::getRuleIndex() const {
  return OvsTopologyParser::RuleFlowItem;
}


std::any OvsTopologyParser::FlowItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitFlowItem(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::FlowItemContext* OvsTopologyParser::flowItem() {
  FlowItemContext *_localctx = _tracker.createInstance<FlowItemContext>(_ctx, getState());
  enterRule(_localctx, 26, OvsTopologyParser::RuleFlowItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(140);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 11, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(137);
      flowLine();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(138);
      genericLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(139);
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

//----------------- FlowLineContext ------------------------------------------------------------------

OvsTopologyParser::FlowLineContext::FlowLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::FlowLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<tree::TerminalNode *> OvsTopologyParser::FlowLineContext::FLOWTOKEN() {
  return getTokens(OvsTopologyParser::FLOWTOKEN);
}

tree::TerminalNode* OvsTopologyParser::FlowLineContext::FLOWTOKEN(size_t i) {
  return getToken(OvsTopologyParser::FLOWTOKEN, i);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::FlowLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::FlowLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::FlowLineContext::getRuleIndex() const {
  return OvsTopologyParser::RuleFlowLine;
}


std::any OvsTopologyParser::FlowLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitFlowLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::FlowLineContext* OvsTopologyParser::flowLine() {
  FlowLineContext *_localctx = _tracker.createInstance<FlowLineContext>(_ctx, getState());
  enterRule(_localctx, 28, OvsTopologyParser::RuleFlowLine);
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
    setState(143); 
    _errHandler->sync(this);
    alt = 1;
    do {
      switch (alt) {
        case 1: {
              setState(142);
              match(OvsTopologyParser::FLOWTOKEN);
              break;
            }

      default:
        throw NoViableAltException(this);
      }
      setState(145); 
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 12, _ctx);
    } while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER);
    setState(150);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0)) {
      setState(147);
      elem();
      setState(152);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(153);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- GenericLineContext ------------------------------------------------------------------

OvsTopologyParser::GenericLineContext::GenericLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::GenericLineContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}

std::vector<OvsTopologyParser::ElemContext *> OvsTopologyParser::GenericLineContext::elem() {
  return getRuleContexts<OvsTopologyParser::ElemContext>();
}

OvsTopologyParser::ElemContext* OvsTopologyParser::GenericLineContext::elem(size_t i) {
  return getRuleContext<OvsTopologyParser::ElemContext>(i);
}


size_t OvsTopologyParser::GenericLineContext::getRuleIndex() const {
  return OvsTopologyParser::RuleGenericLine;
}


std::any OvsTopologyParser::GenericLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitGenericLine(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::GenericLineContext* OvsTopologyParser::genericLine() {
  GenericLineContext *_localctx = _tracker.createInstance<GenericLineContext>(_ctx, getState());
  enterRule(_localctx, 30, OvsTopologyParser::RuleGenericLine);
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
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(155);
      elem();
      setState(158); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4092) != 0));
    setState(160);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlankContext ------------------------------------------------------------------

OvsTopologyParser::BlankContext::BlankContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::BlankContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}


size_t OvsTopologyParser::BlankContext::getRuleIndex() const {
  return OvsTopologyParser::RuleBlank;
}


std::any OvsTopologyParser::BlankContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitBlank(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::BlankContext* OvsTopologyParser::blank() {
  BlankContext *_localctx = _tracker.createInstance<BlankContext>(_ctx, getState());
  enterRule(_localctx, 32, OvsTopologyParser::RuleBlank);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(162);
    match(OvsTopologyParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ElemContext ------------------------------------------------------------------

OvsTopologyParser::ElemContext::ElemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* OvsTopologyParser::ElemContext::NEWLINE() {
  return getToken(OvsTopologyParser::NEWLINE, 0);
}


size_t OvsTopologyParser::ElemContext::getRuleIndex() const {
  return OvsTopologyParser::RuleElem;
}


std::any OvsTopologyParser::ElemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<OvsTopologyVisitor*>(visitor))
    return parserVisitor->visitElem(this);
  else
    return visitor->visitChildren(this);
}

OvsTopologyParser::ElemContext* OvsTopologyParser::elem() {
  ElemContext *_localctx = _tracker.createInstance<ElemContext>(_ctx, getState());
  enterRule(_localctx, 34, OvsTopologyParser::RuleElem);
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
    setState(164);
    _la = _input->LA(1);
    if (_la == 0 || _la == Token::EOF || (_la == OvsTopologyParser::NEWLINE)) {
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

void OvsTopologyParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  ovstopologyParserInitialize();
#else
  ::antlr4::internal::call_once(ovstopologyParserOnceFlag, ovstopologyParserInitialize);
#endif
}
