
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/FrrRouter.g4 by ANTLR 4.13.2


#include "FrrRouterVisitor.h"

#include "FrrRouterParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct FrrRouterParserStaticData final {
  FrrRouterParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  FrrRouterParserStaticData(const FrrRouterParserStaticData&) = delete;
  FrrRouterParserStaticData(FrrRouterParserStaticData&&) = delete;
  FrrRouterParserStaticData& operator=(const FrrRouterParserStaticData&) = delete;
  FrrRouterParserStaticData& operator=(FrrRouterParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag frrrouterParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<FrrRouterParserStaticData> frrrouterParserStaticData = nullptr;

void frrrouterParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (frrrouterParserStaticData != nullptr) {
    return;
  }
#else
  assert(frrrouterParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<FrrRouterParserStaticData>(
    std::vector<std::string>{
      "routeDocument", "ifaceDocument", "detailDocument", "routeItem", "routeLine", 
      "routeCode", "destination", "subnetSummary", "ifaceItem", "briefHeader", 
      "briefEntry", "addrOrUnassigned", "briefField", "detailItem", "ifaceHeader", 
      "detailAttr", "ifname", "genericLine", "blank", "elem"
    },
    std::vector<std::string>{
      "", "", "", "", "'default'", "", "'Codes:'", "'Gateway'", "'is'", 
      "'variably'", "'subnetted'", "", "'unassigned'", "", "", "", "", "", 
      "", "':'", "'/'"
    },
    std::vector<std::string>{
      "", "NEWLINE", "WS", "ROUTECODE", "DEFAULT", "ADDR", "CODES", "GATEWAY", 
      "IS", "VARIABLY", "SUBMITTED", "INTERFACE", "UNASSIGNED", "METHOD", 
      "STATUSWORD", "IFNAME", "PORTNAME", "IDENT", "ATTRWORD", "COLON", 
      "SLASH", "WORD"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,21,177,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,2,19,7,19,1,0,5,0,42,8,0,10,
  	0,12,0,45,9,0,1,0,1,0,1,1,5,1,50,8,1,10,1,12,1,53,9,1,1,1,1,1,1,2,5,2,
  	58,8,2,10,2,12,2,61,9,2,1,2,1,2,1,3,1,3,1,3,1,3,3,3,69,8,3,1,4,4,4,72,
  	8,4,11,4,12,4,73,1,4,1,4,5,4,78,8,4,10,4,12,4,81,9,4,1,4,1,4,1,5,1,5,
  	1,6,1,6,1,7,1,7,1,7,1,7,1,7,5,7,94,8,7,10,7,12,7,97,9,7,1,7,1,7,1,8,1,
  	8,1,8,1,8,3,8,105,8,8,1,9,1,9,5,9,109,8,9,10,9,12,9,112,9,9,1,9,1,9,1,
  	10,1,10,3,10,118,8,10,1,10,5,10,121,8,10,10,10,12,10,124,9,10,1,10,5,
  	10,127,8,10,10,10,12,10,130,9,10,1,10,1,10,1,11,1,11,1,12,1,12,1,13,1,
  	13,1,13,1,13,3,13,142,8,13,1,14,1,14,1,14,1,14,5,14,148,8,14,10,14,12,
  	14,151,9,14,1,14,1,14,1,15,1,15,5,15,157,8,15,10,15,12,15,160,9,15,1,
  	15,1,15,1,16,1,16,1,17,4,17,167,8,17,11,17,12,17,168,1,17,1,17,1,18,1,
  	18,1,19,1,19,1,19,0,0,20,0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,
  	34,36,38,0,5,1,0,4,5,2,0,5,5,12,12,1,0,13,14,1,0,15,17,1,0,1,1,178,0,
  	43,1,0,0,0,2,51,1,0,0,0,4,59,1,0,0,0,6,68,1,0,0,0,8,71,1,0,0,0,10,84,
  	1,0,0,0,12,86,1,0,0,0,14,88,1,0,0,0,16,104,1,0,0,0,18,106,1,0,0,0,20,
  	115,1,0,0,0,22,133,1,0,0,0,24,135,1,0,0,0,26,141,1,0,0,0,28,143,1,0,0,
  	0,30,154,1,0,0,0,32,163,1,0,0,0,34,166,1,0,0,0,36,172,1,0,0,0,38,174,
  	1,0,0,0,40,42,3,6,3,0,41,40,1,0,0,0,42,45,1,0,0,0,43,41,1,0,0,0,43,44,
  	1,0,0,0,44,46,1,0,0,0,45,43,1,0,0,0,46,47,5,0,0,1,47,1,1,0,0,0,48,50,
  	3,16,8,0,49,48,1,0,0,0,50,53,1,0,0,0,51,49,1,0,0,0,51,52,1,0,0,0,52,54,
  	1,0,0,0,53,51,1,0,0,0,54,55,5,0,0,1,55,3,1,0,0,0,56,58,3,26,13,0,57,56,
  	1,0,0,0,58,61,1,0,0,0,59,57,1,0,0,0,59,60,1,0,0,0,60,62,1,0,0,0,61,59,
  	1,0,0,0,62,63,5,0,0,1,63,5,1,0,0,0,64,69,3,8,4,0,65,69,3,14,7,0,66,69,
  	3,34,17,0,67,69,3,36,18,0,68,64,1,0,0,0,68,65,1,0,0,0,68,66,1,0,0,0,68,
  	67,1,0,0,0,69,7,1,0,0,0,70,72,3,10,5,0,71,70,1,0,0,0,72,73,1,0,0,0,73,
  	71,1,0,0,0,73,74,1,0,0,0,74,75,1,0,0,0,75,79,3,12,6,0,76,78,3,38,19,0,
  	77,76,1,0,0,0,78,81,1,0,0,0,79,77,1,0,0,0,79,80,1,0,0,0,80,82,1,0,0,0,
  	81,79,1,0,0,0,82,83,5,1,0,0,83,9,1,0,0,0,84,85,5,3,0,0,85,11,1,0,0,0,
  	86,87,7,0,0,0,87,13,1,0,0,0,88,89,5,5,0,0,89,90,5,8,0,0,90,91,5,9,0,0,
  	91,95,5,10,0,0,92,94,3,38,19,0,93,92,1,0,0,0,94,97,1,0,0,0,95,93,1,0,
  	0,0,95,96,1,0,0,0,96,98,1,0,0,0,97,95,1,0,0,0,98,99,5,1,0,0,99,15,1,0,
  	0,0,100,105,3,18,9,0,101,105,3,20,10,0,102,105,3,34,17,0,103,105,3,36,
  	18,0,104,100,1,0,0,0,104,101,1,0,0,0,104,102,1,0,0,0,104,103,1,0,0,0,
  	105,17,1,0,0,0,106,110,5,11,0,0,107,109,3,38,19,0,108,107,1,0,0,0,109,
  	112,1,0,0,0,110,108,1,0,0,0,110,111,1,0,0,0,111,113,1,0,0,0,112,110,1,
  	0,0,0,113,114,5,1,0,0,114,19,1,0,0,0,115,117,3,32,16,0,116,118,3,22,11,
  	0,117,116,1,0,0,0,117,118,1,0,0,0,118,122,1,0,0,0,119,121,3,24,12,0,120,
  	119,1,0,0,0,121,124,1,0,0,0,122,120,1,0,0,0,122,123,1,0,0,0,123,128,1,
  	0,0,0,124,122,1,0,0,0,125,127,3,38,19,0,126,125,1,0,0,0,127,130,1,0,0,
  	0,128,126,1,0,0,0,128,129,1,0,0,0,129,131,1,0,0,0,130,128,1,0,0,0,131,
  	132,5,1,0,0,132,21,1,0,0,0,133,134,7,1,0,0,134,23,1,0,0,0,135,136,7,2,
  	0,0,136,25,1,0,0,0,137,142,3,28,14,0,138,142,3,30,15,0,139,142,3,34,17,
  	0,140,142,3,36,18,0,141,137,1,0,0,0,141,138,1,0,0,0,141,139,1,0,0,0,141,
  	140,1,0,0,0,142,27,1,0,0,0,143,144,5,11,0,0,144,145,3,32,16,0,145,149,
  	5,8,0,0,146,148,3,38,19,0,147,146,1,0,0,0,148,151,1,0,0,0,149,147,1,0,
  	0,0,149,150,1,0,0,0,150,152,1,0,0,0,151,149,1,0,0,0,152,153,5,1,0,0,153,
  	29,1,0,0,0,154,158,5,18,0,0,155,157,3,38,19,0,156,155,1,0,0,0,157,160,
  	1,0,0,0,158,156,1,0,0,0,158,159,1,0,0,0,159,161,1,0,0,0,160,158,1,0,0,
  	0,161,162,5,1,0,0,162,31,1,0,0,0,163,164,7,3,0,0,164,33,1,0,0,0,165,167,
  	3,38,19,0,166,165,1,0,0,0,167,168,1,0,0,0,168,166,1,0,0,0,168,169,1,0,
  	0,0,169,170,1,0,0,0,170,171,5,1,0,0,171,35,1,0,0,0,172,173,5,1,0,0,173,
  	37,1,0,0,0,174,175,8,4,0,0,175,39,1,0,0,0,16,43,51,59,68,73,79,95,104,
  	110,117,122,128,141,149,158,168
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  frrrouterParserStaticData = std::move(staticData);
}

}

FrrRouterParser::FrrRouterParser(TokenStream *input) : FrrRouterParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

FrrRouterParser::FrrRouterParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  FrrRouterParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *frrrouterParserStaticData->atn, frrrouterParserStaticData->decisionToDFA, frrrouterParserStaticData->sharedContextCache, options);
}

FrrRouterParser::~FrrRouterParser() {
  delete _interpreter;
}

const atn::ATN& FrrRouterParser::getATN() const {
  return *frrrouterParserStaticData->atn;
}

std::string FrrRouterParser::getGrammarFileName() const {
  return "FrrRouter.g4";
}

const std::vector<std::string>& FrrRouterParser::getRuleNames() const {
  return frrrouterParserStaticData->ruleNames;
}

const dfa::Vocabulary& FrrRouterParser::getVocabulary() const {
  return frrrouterParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView FrrRouterParser::getSerializedATN() const {
  return frrrouterParserStaticData->serializedATN;
}


//----------------- RouteDocumentContext ------------------------------------------------------------------

FrrRouterParser::RouteDocumentContext::RouteDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::RouteDocumentContext::EOF() {
  return getToken(FrrRouterParser::EOF, 0);
}

std::vector<FrrRouterParser::RouteItemContext *> FrrRouterParser::RouteDocumentContext::routeItem() {
  return getRuleContexts<FrrRouterParser::RouteItemContext>();
}

FrrRouterParser::RouteItemContext* FrrRouterParser::RouteDocumentContext::routeItem(size_t i) {
  return getRuleContext<FrrRouterParser::RouteItemContext>(i);
}


size_t FrrRouterParser::RouteDocumentContext::getRuleIndex() const {
  return FrrRouterParser::RuleRouteDocument;
}


std::any FrrRouterParser::RouteDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitRouteDocument(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::RouteDocumentContext* FrrRouterParser::routeDocument() {
  RouteDocumentContext *_localctx = _tracker.createInstance<RouteDocumentContext>(_ctx, getState());
  enterRule(_localctx, 0, FrrRouterParser::RuleRouteDocument);
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
    setState(43);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194302) != 0)) {
      setState(40);
      routeItem();
      setState(45);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(46);
    match(FrrRouterParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfaceDocumentContext ------------------------------------------------------------------

FrrRouterParser::IfaceDocumentContext::IfaceDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::IfaceDocumentContext::EOF() {
  return getToken(FrrRouterParser::EOF, 0);
}

std::vector<FrrRouterParser::IfaceItemContext *> FrrRouterParser::IfaceDocumentContext::ifaceItem() {
  return getRuleContexts<FrrRouterParser::IfaceItemContext>();
}

FrrRouterParser::IfaceItemContext* FrrRouterParser::IfaceDocumentContext::ifaceItem(size_t i) {
  return getRuleContext<FrrRouterParser::IfaceItemContext>(i);
}


size_t FrrRouterParser::IfaceDocumentContext::getRuleIndex() const {
  return FrrRouterParser::RuleIfaceDocument;
}


std::any FrrRouterParser::IfaceDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitIfaceDocument(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::IfaceDocumentContext* FrrRouterParser::ifaceDocument() {
  IfaceDocumentContext *_localctx = _tracker.createInstance<IfaceDocumentContext>(_ctx, getState());
  enterRule(_localctx, 2, FrrRouterParser::RuleIfaceDocument);
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
    setState(51);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194302) != 0)) {
      setState(48);
      ifaceItem();
      setState(53);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(54);
    match(FrrRouterParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- DetailDocumentContext ------------------------------------------------------------------

FrrRouterParser::DetailDocumentContext::DetailDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::DetailDocumentContext::EOF() {
  return getToken(FrrRouterParser::EOF, 0);
}

std::vector<FrrRouterParser::DetailItemContext *> FrrRouterParser::DetailDocumentContext::detailItem() {
  return getRuleContexts<FrrRouterParser::DetailItemContext>();
}

FrrRouterParser::DetailItemContext* FrrRouterParser::DetailDocumentContext::detailItem(size_t i) {
  return getRuleContext<FrrRouterParser::DetailItemContext>(i);
}


size_t FrrRouterParser::DetailDocumentContext::getRuleIndex() const {
  return FrrRouterParser::RuleDetailDocument;
}


std::any FrrRouterParser::DetailDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitDetailDocument(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::DetailDocumentContext* FrrRouterParser::detailDocument() {
  DetailDocumentContext *_localctx = _tracker.createInstance<DetailDocumentContext>(_ctx, getState());
  enterRule(_localctx, 4, FrrRouterParser::RuleDetailDocument);
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
    setState(59);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194302) != 0)) {
      setState(56);
      detailItem();
      setState(61);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(62);
    match(FrrRouterParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RouteItemContext ------------------------------------------------------------------

FrrRouterParser::RouteItemContext::RouteItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrrRouterParser::RouteLineContext* FrrRouterParser::RouteItemContext::routeLine() {
  return getRuleContext<FrrRouterParser::RouteLineContext>(0);
}

FrrRouterParser::SubnetSummaryContext* FrrRouterParser::RouteItemContext::subnetSummary() {
  return getRuleContext<FrrRouterParser::SubnetSummaryContext>(0);
}

FrrRouterParser::GenericLineContext* FrrRouterParser::RouteItemContext::genericLine() {
  return getRuleContext<FrrRouterParser::GenericLineContext>(0);
}

FrrRouterParser::BlankContext* FrrRouterParser::RouteItemContext::blank() {
  return getRuleContext<FrrRouterParser::BlankContext>(0);
}


size_t FrrRouterParser::RouteItemContext::getRuleIndex() const {
  return FrrRouterParser::RuleRouteItem;
}


std::any FrrRouterParser::RouteItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitRouteItem(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::RouteItemContext* FrrRouterParser::routeItem() {
  RouteItemContext *_localctx = _tracker.createInstance<RouteItemContext>(_ctx, getState());
  enterRule(_localctx, 6, FrrRouterParser::RuleRouteItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(68);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 3, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(64);
      routeLine();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(65);
      subnetSummary();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(66);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(67);
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

//----------------- RouteLineContext ------------------------------------------------------------------

FrrRouterParser::RouteLineContext::RouteLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrrRouterParser::DestinationContext* FrrRouterParser::RouteLineContext::destination() {
  return getRuleContext<FrrRouterParser::DestinationContext>(0);
}

tree::TerminalNode* FrrRouterParser::RouteLineContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::RouteCodeContext *> FrrRouterParser::RouteLineContext::routeCode() {
  return getRuleContexts<FrrRouterParser::RouteCodeContext>();
}

FrrRouterParser::RouteCodeContext* FrrRouterParser::RouteLineContext::routeCode(size_t i) {
  return getRuleContext<FrrRouterParser::RouteCodeContext>(i);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::RouteLineContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::RouteLineContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::RouteLineContext::getRuleIndex() const {
  return FrrRouterParser::RuleRouteLine;
}


std::any FrrRouterParser::RouteLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitRouteLine(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::RouteLineContext* FrrRouterParser::routeLine() {
  RouteLineContext *_localctx = _tracker.createInstance<RouteLineContext>(_ctx, getState());
  enterRule(_localctx, 8, FrrRouterParser::RuleRouteLine);
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
    do {
      setState(70);
      routeCode();
      setState(73); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while (_la == FrrRouterParser::ROUTECODE);
    setState(75);
    destination();
    setState(79);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(76);
      elem();
      setState(81);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(82);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RouteCodeContext ------------------------------------------------------------------

FrrRouterParser::RouteCodeContext::RouteCodeContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::RouteCodeContext::ROUTECODE() {
  return getToken(FrrRouterParser::ROUTECODE, 0);
}


size_t FrrRouterParser::RouteCodeContext::getRuleIndex() const {
  return FrrRouterParser::RuleRouteCode;
}


std::any FrrRouterParser::RouteCodeContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitRouteCode(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::RouteCodeContext* FrrRouterParser::routeCode() {
  RouteCodeContext *_localctx = _tracker.createInstance<RouteCodeContext>(_ctx, getState());
  enterRule(_localctx, 10, FrrRouterParser::RuleRouteCode);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(84);
    match(FrrRouterParser::ROUTECODE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- DestinationContext ------------------------------------------------------------------

FrrRouterParser::DestinationContext::DestinationContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::DestinationContext::DEFAULT() {
  return getToken(FrrRouterParser::DEFAULT, 0);
}

tree::TerminalNode* FrrRouterParser::DestinationContext::ADDR() {
  return getToken(FrrRouterParser::ADDR, 0);
}


size_t FrrRouterParser::DestinationContext::getRuleIndex() const {
  return FrrRouterParser::RuleDestination;
}


std::any FrrRouterParser::DestinationContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitDestination(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::DestinationContext* FrrRouterParser::destination() {
  DestinationContext *_localctx = _tracker.createInstance<DestinationContext>(_ctx, getState());
  enterRule(_localctx, 12, FrrRouterParser::RuleDestination);
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
    _la = _input->LA(1);
    if (!(_la == FrrRouterParser::DEFAULT

    || _la == FrrRouterParser::ADDR)) {
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

//----------------- SubnetSummaryContext ------------------------------------------------------------------

FrrRouterParser::SubnetSummaryContext::SubnetSummaryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::SubnetSummaryContext::ADDR() {
  return getToken(FrrRouterParser::ADDR, 0);
}

tree::TerminalNode* FrrRouterParser::SubnetSummaryContext::IS() {
  return getToken(FrrRouterParser::IS, 0);
}

tree::TerminalNode* FrrRouterParser::SubnetSummaryContext::VARIABLY() {
  return getToken(FrrRouterParser::VARIABLY, 0);
}

tree::TerminalNode* FrrRouterParser::SubnetSummaryContext::SUBMITTED() {
  return getToken(FrrRouterParser::SUBMITTED, 0);
}

tree::TerminalNode* FrrRouterParser::SubnetSummaryContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::SubnetSummaryContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::SubnetSummaryContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::SubnetSummaryContext::getRuleIndex() const {
  return FrrRouterParser::RuleSubnetSummary;
}


std::any FrrRouterParser::SubnetSummaryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitSubnetSummary(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::SubnetSummaryContext* FrrRouterParser::subnetSummary() {
  SubnetSummaryContext *_localctx = _tracker.createInstance<SubnetSummaryContext>(_ctx, getState());
  enterRule(_localctx, 14, FrrRouterParser::RuleSubnetSummary);
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
    setState(88);
    match(FrrRouterParser::ADDR);
    setState(89);
    match(FrrRouterParser::IS);
    setState(90);
    match(FrrRouterParser::VARIABLY);
    setState(91);
    match(FrrRouterParser::SUBMITTED);
    setState(95);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(92);
      elem();
      setState(97);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(98);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfaceItemContext ------------------------------------------------------------------

FrrRouterParser::IfaceItemContext::IfaceItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrrRouterParser::BriefHeaderContext* FrrRouterParser::IfaceItemContext::briefHeader() {
  return getRuleContext<FrrRouterParser::BriefHeaderContext>(0);
}

FrrRouterParser::BriefEntryContext* FrrRouterParser::IfaceItemContext::briefEntry() {
  return getRuleContext<FrrRouterParser::BriefEntryContext>(0);
}

FrrRouterParser::GenericLineContext* FrrRouterParser::IfaceItemContext::genericLine() {
  return getRuleContext<FrrRouterParser::GenericLineContext>(0);
}

FrrRouterParser::BlankContext* FrrRouterParser::IfaceItemContext::blank() {
  return getRuleContext<FrrRouterParser::BlankContext>(0);
}


size_t FrrRouterParser::IfaceItemContext::getRuleIndex() const {
  return FrrRouterParser::RuleIfaceItem;
}


std::any FrrRouterParser::IfaceItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitIfaceItem(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::IfaceItemContext* FrrRouterParser::ifaceItem() {
  IfaceItemContext *_localctx = _tracker.createInstance<IfaceItemContext>(_ctx, getState());
  enterRule(_localctx, 16, FrrRouterParser::RuleIfaceItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(104);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 7, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(100);
      briefHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(101);
      briefEntry();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(102);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(103);
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

FrrRouterParser::BriefHeaderContext::BriefHeaderContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::BriefHeaderContext::INTERFACE() {
  return getToken(FrrRouterParser::INTERFACE, 0);
}

tree::TerminalNode* FrrRouterParser::BriefHeaderContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::BriefHeaderContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::BriefHeaderContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::BriefHeaderContext::getRuleIndex() const {
  return FrrRouterParser::RuleBriefHeader;
}


std::any FrrRouterParser::BriefHeaderContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitBriefHeader(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::BriefHeaderContext* FrrRouterParser::briefHeader() {
  BriefHeaderContext *_localctx = _tracker.createInstance<BriefHeaderContext>(_ctx, getState());
  enterRule(_localctx, 18, FrrRouterParser::RuleBriefHeader);
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
    match(FrrRouterParser::INTERFACE);
    setState(110);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(107);
      elem();
      setState(112);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(113);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefEntryContext ------------------------------------------------------------------

FrrRouterParser::BriefEntryContext::BriefEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrrRouterParser::IfnameContext* FrrRouterParser::BriefEntryContext::ifname() {
  return getRuleContext<FrrRouterParser::IfnameContext>(0);
}

tree::TerminalNode* FrrRouterParser::BriefEntryContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

FrrRouterParser::AddrOrUnassignedContext* FrrRouterParser::BriefEntryContext::addrOrUnassigned() {
  return getRuleContext<FrrRouterParser::AddrOrUnassignedContext>(0);
}

std::vector<FrrRouterParser::BriefFieldContext *> FrrRouterParser::BriefEntryContext::briefField() {
  return getRuleContexts<FrrRouterParser::BriefFieldContext>();
}

FrrRouterParser::BriefFieldContext* FrrRouterParser::BriefEntryContext::briefField(size_t i) {
  return getRuleContext<FrrRouterParser::BriefFieldContext>(i);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::BriefEntryContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::BriefEntryContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::BriefEntryContext::getRuleIndex() const {
  return FrrRouterParser::RuleBriefEntry;
}


std::any FrrRouterParser::BriefEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitBriefEntry(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::BriefEntryContext* FrrRouterParser::briefEntry() {
  BriefEntryContext *_localctx = _tracker.createInstance<BriefEntryContext>(_ctx, getState());
  enterRule(_localctx, 20, FrrRouterParser::RuleBriefEntry);
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
    setState(115);
    ifname();
    setState(117);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 9, _ctx)) {
    case 1: {
      setState(116);
      addrOrUnassigned();
      break;
    }

    default:
      break;
    }
    setState(122);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 10, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(119);
        briefField(); 
      }
      setState(124);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 10, _ctx);
    }
    setState(128);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(125);
      elem();
      setState(130);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(131);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- AddrOrUnassignedContext ------------------------------------------------------------------

FrrRouterParser::AddrOrUnassignedContext::AddrOrUnassignedContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::AddrOrUnassignedContext::ADDR() {
  return getToken(FrrRouterParser::ADDR, 0);
}

tree::TerminalNode* FrrRouterParser::AddrOrUnassignedContext::UNASSIGNED() {
  return getToken(FrrRouterParser::UNASSIGNED, 0);
}


size_t FrrRouterParser::AddrOrUnassignedContext::getRuleIndex() const {
  return FrrRouterParser::RuleAddrOrUnassigned;
}


std::any FrrRouterParser::AddrOrUnassignedContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitAddrOrUnassigned(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::AddrOrUnassignedContext* FrrRouterParser::addrOrUnassigned() {
  AddrOrUnassignedContext *_localctx = _tracker.createInstance<AddrOrUnassignedContext>(_ctx, getState());
  enterRule(_localctx, 22, FrrRouterParser::RuleAddrOrUnassigned);
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
    setState(133);
    _la = _input->LA(1);
    if (!(_la == FrrRouterParser::ADDR

    || _la == FrrRouterParser::UNASSIGNED)) {
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

FrrRouterParser::BriefFieldContext::BriefFieldContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::BriefFieldContext::METHOD() {
  return getToken(FrrRouterParser::METHOD, 0);
}

tree::TerminalNode* FrrRouterParser::BriefFieldContext::STATUSWORD() {
  return getToken(FrrRouterParser::STATUSWORD, 0);
}


size_t FrrRouterParser::BriefFieldContext::getRuleIndex() const {
  return FrrRouterParser::RuleBriefField;
}


std::any FrrRouterParser::BriefFieldContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitBriefField(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::BriefFieldContext* FrrRouterParser::briefField() {
  BriefFieldContext *_localctx = _tracker.createInstance<BriefFieldContext>(_ctx, getState());
  enterRule(_localctx, 24, FrrRouterParser::RuleBriefField);
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
    setState(135);
    _la = _input->LA(1);
    if (!(_la == FrrRouterParser::METHOD

    || _la == FrrRouterParser::STATUSWORD)) {
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

//----------------- DetailItemContext ------------------------------------------------------------------

FrrRouterParser::DetailItemContext::DetailItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

FrrRouterParser::IfaceHeaderContext* FrrRouterParser::DetailItemContext::ifaceHeader() {
  return getRuleContext<FrrRouterParser::IfaceHeaderContext>(0);
}

FrrRouterParser::DetailAttrContext* FrrRouterParser::DetailItemContext::detailAttr() {
  return getRuleContext<FrrRouterParser::DetailAttrContext>(0);
}

FrrRouterParser::GenericLineContext* FrrRouterParser::DetailItemContext::genericLine() {
  return getRuleContext<FrrRouterParser::GenericLineContext>(0);
}

FrrRouterParser::BlankContext* FrrRouterParser::DetailItemContext::blank() {
  return getRuleContext<FrrRouterParser::BlankContext>(0);
}


size_t FrrRouterParser::DetailItemContext::getRuleIndex() const {
  return FrrRouterParser::RuleDetailItem;
}


std::any FrrRouterParser::DetailItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitDetailItem(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::DetailItemContext* FrrRouterParser::detailItem() {
  DetailItemContext *_localctx = _tracker.createInstance<DetailItemContext>(_ctx, getState());
  enterRule(_localctx, 26, FrrRouterParser::RuleDetailItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(141);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 12, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(137);
      ifaceHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(138);
      detailAttr();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(139);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(140);
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

//----------------- IfaceHeaderContext ------------------------------------------------------------------

FrrRouterParser::IfaceHeaderContext::IfaceHeaderContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::IfaceHeaderContext::INTERFACE() {
  return getToken(FrrRouterParser::INTERFACE, 0);
}

FrrRouterParser::IfnameContext* FrrRouterParser::IfaceHeaderContext::ifname() {
  return getRuleContext<FrrRouterParser::IfnameContext>(0);
}

tree::TerminalNode* FrrRouterParser::IfaceHeaderContext::IS() {
  return getToken(FrrRouterParser::IS, 0);
}

tree::TerminalNode* FrrRouterParser::IfaceHeaderContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::IfaceHeaderContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::IfaceHeaderContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::IfaceHeaderContext::getRuleIndex() const {
  return FrrRouterParser::RuleIfaceHeader;
}


std::any FrrRouterParser::IfaceHeaderContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitIfaceHeader(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::IfaceHeaderContext* FrrRouterParser::ifaceHeader() {
  IfaceHeaderContext *_localctx = _tracker.createInstance<IfaceHeaderContext>(_ctx, getState());
  enterRule(_localctx, 28, FrrRouterParser::RuleIfaceHeader);
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
    setState(143);
    match(FrrRouterParser::INTERFACE);
    setState(144);
    ifname();
    setState(145);
    match(FrrRouterParser::IS);
    setState(149);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(146);
      elem();
      setState(151);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(152);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- DetailAttrContext ------------------------------------------------------------------

FrrRouterParser::DetailAttrContext::DetailAttrContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::DetailAttrContext::ATTRWORD() {
  return getToken(FrrRouterParser::ATTRWORD, 0);
}

tree::TerminalNode* FrrRouterParser::DetailAttrContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::DetailAttrContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::DetailAttrContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::DetailAttrContext::getRuleIndex() const {
  return FrrRouterParser::RuleDetailAttr;
}


std::any FrrRouterParser::DetailAttrContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitDetailAttr(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::DetailAttrContext* FrrRouterParser::detailAttr() {
  DetailAttrContext *_localctx = _tracker.createInstance<DetailAttrContext>(_ctx, getState());
  enterRule(_localctx, 30, FrrRouterParser::RuleDetailAttr);
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
    match(FrrRouterParser::ATTRWORD);
    setState(158);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0)) {
      setState(155);
      elem();
      setState(160);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(161);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfnameContext ------------------------------------------------------------------

FrrRouterParser::IfnameContext::IfnameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::IfnameContext::IFNAME() {
  return getToken(FrrRouterParser::IFNAME, 0);
}

tree::TerminalNode* FrrRouterParser::IfnameContext::PORTNAME() {
  return getToken(FrrRouterParser::PORTNAME, 0);
}

tree::TerminalNode* FrrRouterParser::IfnameContext::IDENT() {
  return getToken(FrrRouterParser::IDENT, 0);
}


size_t FrrRouterParser::IfnameContext::getRuleIndex() const {
  return FrrRouterParser::RuleIfname;
}


std::any FrrRouterParser::IfnameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitIfname(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::IfnameContext* FrrRouterParser::ifname() {
  IfnameContext *_localctx = _tracker.createInstance<IfnameContext>(_ctx, getState());
  enterRule(_localctx, 32, FrrRouterParser::RuleIfname);
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
    setState(163);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 229376) != 0))) {
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

FrrRouterParser::GenericLineContext::GenericLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::GenericLineContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}

std::vector<FrrRouterParser::ElemContext *> FrrRouterParser::GenericLineContext::elem() {
  return getRuleContexts<FrrRouterParser::ElemContext>();
}

FrrRouterParser::ElemContext* FrrRouterParser::GenericLineContext::elem(size_t i) {
  return getRuleContext<FrrRouterParser::ElemContext>(i);
}


size_t FrrRouterParser::GenericLineContext::getRuleIndex() const {
  return FrrRouterParser::RuleGenericLine;
}


std::any FrrRouterParser::GenericLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitGenericLine(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::GenericLineContext* FrrRouterParser::genericLine() {
  GenericLineContext *_localctx = _tracker.createInstance<GenericLineContext>(_ctx, getState());
  enterRule(_localctx, 34, FrrRouterParser::RuleGenericLine);
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
    setState(166); 
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(165);
      elem();
      setState(168); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 4194300) != 0));
    setState(170);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlankContext ------------------------------------------------------------------

FrrRouterParser::BlankContext::BlankContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::BlankContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}


size_t FrrRouterParser::BlankContext::getRuleIndex() const {
  return FrrRouterParser::RuleBlank;
}


std::any FrrRouterParser::BlankContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitBlank(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::BlankContext* FrrRouterParser::blank() {
  BlankContext *_localctx = _tracker.createInstance<BlankContext>(_ctx, getState());
  enterRule(_localctx, 36, FrrRouterParser::RuleBlank);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(172);
    match(FrrRouterParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ElemContext ------------------------------------------------------------------

FrrRouterParser::ElemContext::ElemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* FrrRouterParser::ElemContext::NEWLINE() {
  return getToken(FrrRouterParser::NEWLINE, 0);
}


size_t FrrRouterParser::ElemContext::getRuleIndex() const {
  return FrrRouterParser::RuleElem;
}


std::any FrrRouterParser::ElemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<FrrRouterVisitor*>(visitor))
    return parserVisitor->visitElem(this);
  else
    return visitor->visitChildren(this);
}

FrrRouterParser::ElemContext* FrrRouterParser::elem() {
  ElemContext *_localctx = _tracker.createInstance<ElemContext>(_ctx, getState());
  enterRule(_localctx, 38, FrrRouterParser::RuleElem);
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
    setState(174);
    _la = _input->LA(1);
    if (_la == 0 || _la == Token::EOF || (_la == FrrRouterParser::NEWLINE)) {
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

void FrrRouterParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  frrrouterParserInitialize();
#else
  ::antlr4::internal::call_once(frrrouterParserOnceFlag, frrrouterParserInitialize);
#endif
}
