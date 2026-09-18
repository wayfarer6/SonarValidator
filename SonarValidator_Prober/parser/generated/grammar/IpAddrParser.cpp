
// Generated from grammar/IpAddr.g4 by ANTLR 4.13.2


#include "IpAddrVisitor.h"

#include "IpAddrParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct IpAddrParserStaticData final {
  IpAddrParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  IpAddrParserStaticData(const IpAddrParserStaticData&) = delete;
  IpAddrParserStaticData(IpAddrParserStaticData&&) = delete;
  IpAddrParserStaticData& operator=(const IpAddrParserStaticData&) = delete;
  IpAddrParserStaticData& operator=(IpAddrParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag ipaddrParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<IpAddrParserStaticData> ipaddrParserStaticData = nullptr;

void ipaddrParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (ipaddrParserStaticData != nullptr) {
    return;
  }
#else
  assert(ipaddrParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<IpAddrParserStaticData>(
    std::vector<std::string>{
      "document", "briefDocument", "routeDocument", "item", "ifaceHeader", 
      "ifaceAttr", "attrLead", "briefEntry", "linkState", "briefAddr", "routeEntry", 
      "routeHead", "ifname", "genericLine", "blank", "elem"
    },
    std::vector<std::string>{
      "", "", "", "", "", "", "'default'", "", "'inet'", "'inet6'", "", 
      "'UP'", "'DOWN'", "'UNKNOWN'", "", "':'", "'/'"
    },
    std::vector<std::string>{
      "", "NEWLINE", "WS", "INDEX", "MAC", "ADDR", "DEFAULT", "LINK", "INET", 
      "INET6", "LIFETIME", "UP", "DOWN", "UNKNOWN", "IFNAME", "COLON", "SLASH", 
      "WORD"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,17,132,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,1,0,5,0,34,8,0,10,0,12,0,37,9,0,1,0,1,0,1,1,5,1,42,8,1,10,
  	1,12,1,45,9,1,1,1,1,1,1,2,5,2,50,8,2,10,2,12,2,53,9,2,1,2,1,2,1,3,1,3,
  	1,3,1,3,1,3,1,3,3,3,63,8,3,1,4,1,4,1,4,1,4,1,4,5,4,70,8,4,10,4,12,4,73,
  	9,4,1,4,1,4,1,5,1,5,5,5,79,8,5,10,5,12,5,82,9,5,1,5,1,5,1,6,1,6,1,7,1,
  	7,1,7,5,7,91,8,7,10,7,12,7,94,9,7,1,7,5,7,97,8,7,10,7,12,7,100,9,7,1,
  	7,1,7,1,8,1,8,1,9,1,9,1,10,1,10,5,10,110,8,10,10,10,12,10,113,9,10,1,
  	10,1,10,1,11,1,11,1,12,1,12,1,13,4,13,122,8,13,11,13,12,13,123,1,13,1,
  	13,1,14,1,14,1,15,1,15,1,15,0,0,16,0,2,4,6,8,10,12,14,16,18,20,22,24,
  	26,28,30,0,5,1,0,7,10,1,0,11,13,1,0,4,5,2,0,5,6,14,14,1,0,1,1,129,0,35,
  	1,0,0,0,2,43,1,0,0,0,4,51,1,0,0,0,6,62,1,0,0,0,8,64,1,0,0,0,10,76,1,0,
  	0,0,12,85,1,0,0,0,14,87,1,0,0,0,16,103,1,0,0,0,18,105,1,0,0,0,20,107,
  	1,0,0,0,22,116,1,0,0,0,24,118,1,0,0,0,26,121,1,0,0,0,28,127,1,0,0,0,30,
  	129,1,0,0,0,32,34,3,6,3,0,33,32,1,0,0,0,34,37,1,0,0,0,35,33,1,0,0,0,35,
  	36,1,0,0,0,36,38,1,0,0,0,37,35,1,0,0,0,38,39,5,0,0,1,39,1,1,0,0,0,40,
  	42,3,6,3,0,41,40,1,0,0,0,42,45,1,0,0,0,43,41,1,0,0,0,43,44,1,0,0,0,44,
  	46,1,0,0,0,45,43,1,0,0,0,46,47,5,0,0,1,47,3,1,0,0,0,48,50,3,6,3,0,49,
  	48,1,0,0,0,50,53,1,0,0,0,51,49,1,0,0,0,51,52,1,0,0,0,52,54,1,0,0,0,53,
  	51,1,0,0,0,54,55,5,0,0,1,55,5,1,0,0,0,56,63,3,8,4,0,57,63,3,10,5,0,58,
  	63,3,14,7,0,59,63,3,20,10,0,60,63,3,26,13,0,61,63,3,28,14,0,62,56,1,0,
  	0,0,62,57,1,0,0,0,62,58,1,0,0,0,62,59,1,0,0,0,62,60,1,0,0,0,62,61,1,0,
  	0,0,63,7,1,0,0,0,64,65,5,3,0,0,65,66,5,15,0,0,66,67,3,24,12,0,67,71,5,
  	15,0,0,68,70,3,30,15,0,69,68,1,0,0,0,70,73,1,0,0,0,71,69,1,0,0,0,71,72,
  	1,0,0,0,72,74,1,0,0,0,73,71,1,0,0,0,74,75,5,1,0,0,75,9,1,0,0,0,76,80,
  	3,12,6,0,77,79,3,30,15,0,78,77,1,0,0,0,79,82,1,0,0,0,80,78,1,0,0,0,80,
  	81,1,0,0,0,81,83,1,0,0,0,82,80,1,0,0,0,83,84,5,1,0,0,84,11,1,0,0,0,85,
  	86,7,0,0,0,86,13,1,0,0,0,87,88,5,14,0,0,88,92,3,16,8,0,89,91,3,18,9,0,
  	90,89,1,0,0,0,91,94,1,0,0,0,92,90,1,0,0,0,92,93,1,0,0,0,93,98,1,0,0,0,
  	94,92,1,0,0,0,95,97,3,30,15,0,96,95,1,0,0,0,97,100,1,0,0,0,98,96,1,0,
  	0,0,98,99,1,0,0,0,99,101,1,0,0,0,100,98,1,0,0,0,101,102,5,1,0,0,102,15,
  	1,0,0,0,103,104,7,1,0,0,104,17,1,0,0,0,105,106,7,2,0,0,106,19,1,0,0,0,
  	107,111,3,22,11,0,108,110,3,30,15,0,109,108,1,0,0,0,110,113,1,0,0,0,111,
  	109,1,0,0,0,111,112,1,0,0,0,112,114,1,0,0,0,113,111,1,0,0,0,114,115,5,
  	1,0,0,115,21,1,0,0,0,116,117,7,3,0,0,117,23,1,0,0,0,118,119,5,14,0,0,
  	119,25,1,0,0,0,120,122,3,30,15,0,121,120,1,0,0,0,122,123,1,0,0,0,123,
  	121,1,0,0,0,123,124,1,0,0,0,124,125,1,0,0,0,125,126,5,1,0,0,126,27,1,
  	0,0,0,127,128,5,1,0,0,128,29,1,0,0,0,129,130,8,4,0,0,130,31,1,0,0,0,10,
  	35,43,51,62,71,80,92,98,111,123
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  ipaddrParserStaticData = std::move(staticData);
}

}

IpAddrParser::IpAddrParser(TokenStream *input) : IpAddrParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

IpAddrParser::IpAddrParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  IpAddrParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *ipaddrParserStaticData->atn, ipaddrParserStaticData->decisionToDFA, ipaddrParserStaticData->sharedContextCache, options);
}

IpAddrParser::~IpAddrParser() {
  delete _interpreter;
}

const atn::ATN& IpAddrParser::getATN() const {
  return *ipaddrParserStaticData->atn;
}

std::string IpAddrParser::getGrammarFileName() const {
  return "IpAddr.g4";
}

const std::vector<std::string>& IpAddrParser::getRuleNames() const {
  return ipaddrParserStaticData->ruleNames;
}

const dfa::Vocabulary& IpAddrParser::getVocabulary() const {
  return ipaddrParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView IpAddrParser::getSerializedATN() const {
  return ipaddrParserStaticData->serializedATN;
}


//----------------- DocumentContext ------------------------------------------------------------------

IpAddrParser::DocumentContext::DocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::DocumentContext::EOF() {
  return getToken(IpAddrParser::EOF, 0);
}

std::vector<IpAddrParser::ItemContext *> IpAddrParser::DocumentContext::item() {
  return getRuleContexts<IpAddrParser::ItemContext>();
}

IpAddrParser::ItemContext* IpAddrParser::DocumentContext::item(size_t i) {
  return getRuleContext<IpAddrParser::ItemContext>(i);
}


size_t IpAddrParser::DocumentContext::getRuleIndex() const {
  return IpAddrParser::RuleDocument;
}


std::any IpAddrParser::DocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitDocument(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::DocumentContext* IpAddrParser::document() {
  DocumentContext *_localctx = _tracker.createInstance<DocumentContext>(_ctx, getState());
  enterRule(_localctx, 0, IpAddrParser::RuleDocument);
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
    setState(35);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262142) != 0)) {
      setState(32);
      item();
      setState(37);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(38);
    match(IpAddrParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BriefDocumentContext ------------------------------------------------------------------

IpAddrParser::BriefDocumentContext::BriefDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::BriefDocumentContext::EOF() {
  return getToken(IpAddrParser::EOF, 0);
}

std::vector<IpAddrParser::ItemContext *> IpAddrParser::BriefDocumentContext::item() {
  return getRuleContexts<IpAddrParser::ItemContext>();
}

IpAddrParser::ItemContext* IpAddrParser::BriefDocumentContext::item(size_t i) {
  return getRuleContext<IpAddrParser::ItemContext>(i);
}


size_t IpAddrParser::BriefDocumentContext::getRuleIndex() const {
  return IpAddrParser::RuleBriefDocument;
}


std::any IpAddrParser::BriefDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitBriefDocument(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::BriefDocumentContext* IpAddrParser::briefDocument() {
  BriefDocumentContext *_localctx = _tracker.createInstance<BriefDocumentContext>(_ctx, getState());
  enterRule(_localctx, 2, IpAddrParser::RuleBriefDocument);
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
      ((1ULL << _la) & 262142) != 0)) {
      setState(40);
      item();
      setState(45);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(46);
    match(IpAddrParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RouteDocumentContext ------------------------------------------------------------------

IpAddrParser::RouteDocumentContext::RouteDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::RouteDocumentContext::EOF() {
  return getToken(IpAddrParser::EOF, 0);
}

std::vector<IpAddrParser::ItemContext *> IpAddrParser::RouteDocumentContext::item() {
  return getRuleContexts<IpAddrParser::ItemContext>();
}

IpAddrParser::ItemContext* IpAddrParser::RouteDocumentContext::item(size_t i) {
  return getRuleContext<IpAddrParser::ItemContext>(i);
}


size_t IpAddrParser::RouteDocumentContext::getRuleIndex() const {
  return IpAddrParser::RuleRouteDocument;
}


std::any IpAddrParser::RouteDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitRouteDocument(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::RouteDocumentContext* IpAddrParser::routeDocument() {
  RouteDocumentContext *_localctx = _tracker.createInstance<RouteDocumentContext>(_ctx, getState());
  enterRule(_localctx, 4, IpAddrParser::RuleRouteDocument);
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
      ((1ULL << _la) & 262142) != 0)) {
      setState(48);
      item();
      setState(53);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(54);
    match(IpAddrParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ItemContext ------------------------------------------------------------------

IpAddrParser::ItemContext::ItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

IpAddrParser::IfaceHeaderContext* IpAddrParser::ItemContext::ifaceHeader() {
  return getRuleContext<IpAddrParser::IfaceHeaderContext>(0);
}

IpAddrParser::IfaceAttrContext* IpAddrParser::ItemContext::ifaceAttr() {
  return getRuleContext<IpAddrParser::IfaceAttrContext>(0);
}

IpAddrParser::BriefEntryContext* IpAddrParser::ItemContext::briefEntry() {
  return getRuleContext<IpAddrParser::BriefEntryContext>(0);
}

IpAddrParser::RouteEntryContext* IpAddrParser::ItemContext::routeEntry() {
  return getRuleContext<IpAddrParser::RouteEntryContext>(0);
}

IpAddrParser::GenericLineContext* IpAddrParser::ItemContext::genericLine() {
  return getRuleContext<IpAddrParser::GenericLineContext>(0);
}

IpAddrParser::BlankContext* IpAddrParser::ItemContext::blank() {
  return getRuleContext<IpAddrParser::BlankContext>(0);
}


size_t IpAddrParser::ItemContext::getRuleIndex() const {
  return IpAddrParser::RuleItem;
}


std::any IpAddrParser::ItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitItem(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::ItemContext* IpAddrParser::item() {
  ItemContext *_localctx = _tracker.createInstance<ItemContext>(_ctx, getState());
  enterRule(_localctx, 6, IpAddrParser::RuleItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(62);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 3, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(56);
      ifaceHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(57);
      ifaceAttr();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(58);
      briefEntry();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(59);
      routeEntry();
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(60);
      genericLine();
      break;
    }

    case 6: {
      enterOuterAlt(_localctx, 6);
      setState(61);
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

IpAddrParser::IfaceHeaderContext::IfaceHeaderContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::IfaceHeaderContext::INDEX() {
  return getToken(IpAddrParser::INDEX, 0);
}

std::vector<tree::TerminalNode *> IpAddrParser::IfaceHeaderContext::COLON() {
  return getTokens(IpAddrParser::COLON);
}

tree::TerminalNode* IpAddrParser::IfaceHeaderContext::COLON(size_t i) {
  return getToken(IpAddrParser::COLON, i);
}

IpAddrParser::IfnameContext* IpAddrParser::IfaceHeaderContext::ifname() {
  return getRuleContext<IpAddrParser::IfnameContext>(0);
}

tree::TerminalNode* IpAddrParser::IfaceHeaderContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::IfaceHeaderContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::IfaceHeaderContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::IfaceHeaderContext::getRuleIndex() const {
  return IpAddrParser::RuleIfaceHeader;
}


std::any IpAddrParser::IfaceHeaderContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitIfaceHeader(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::IfaceHeaderContext* IpAddrParser::ifaceHeader() {
  IfaceHeaderContext *_localctx = _tracker.createInstance<IfaceHeaderContext>(_ctx, getState());
  enterRule(_localctx, 8, IpAddrParser::RuleIfaceHeader);
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
    setState(64);
    match(IpAddrParser::INDEX);
    setState(65);
    match(IpAddrParser::COLON);
    setState(66);
    ifname();
    setState(67);
    match(IpAddrParser::COLON);
    setState(71);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(68);
      elem();
      setState(73);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(74);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- IfaceAttrContext ------------------------------------------------------------------

IpAddrParser::IfaceAttrContext::IfaceAttrContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

IpAddrParser::AttrLeadContext* IpAddrParser::IfaceAttrContext::attrLead() {
  return getRuleContext<IpAddrParser::AttrLeadContext>(0);
}

tree::TerminalNode* IpAddrParser::IfaceAttrContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::IfaceAttrContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::IfaceAttrContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::IfaceAttrContext::getRuleIndex() const {
  return IpAddrParser::RuleIfaceAttr;
}


std::any IpAddrParser::IfaceAttrContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitIfaceAttr(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::IfaceAttrContext* IpAddrParser::ifaceAttr() {
  IfaceAttrContext *_localctx = _tracker.createInstance<IfaceAttrContext>(_ctx, getState());
  enterRule(_localctx, 10, IpAddrParser::RuleIfaceAttr);
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
    attrLead();
    setState(80);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(77);
      elem();
      setState(82);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(83);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- AttrLeadContext ------------------------------------------------------------------

IpAddrParser::AttrLeadContext::AttrLeadContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::AttrLeadContext::LINK() {
  return getToken(IpAddrParser::LINK, 0);
}

tree::TerminalNode* IpAddrParser::AttrLeadContext::INET() {
  return getToken(IpAddrParser::INET, 0);
}

tree::TerminalNode* IpAddrParser::AttrLeadContext::INET6() {
  return getToken(IpAddrParser::INET6, 0);
}

tree::TerminalNode* IpAddrParser::AttrLeadContext::LIFETIME() {
  return getToken(IpAddrParser::LIFETIME, 0);
}


size_t IpAddrParser::AttrLeadContext::getRuleIndex() const {
  return IpAddrParser::RuleAttrLead;
}


std::any IpAddrParser::AttrLeadContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitAttrLead(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::AttrLeadContext* IpAddrParser::attrLead() {
  AttrLeadContext *_localctx = _tracker.createInstance<AttrLeadContext>(_ctx, getState());
  enterRule(_localctx, 12, IpAddrParser::RuleAttrLead);
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
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 1920) != 0))) {
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

//----------------- BriefEntryContext ------------------------------------------------------------------

IpAddrParser::BriefEntryContext::BriefEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::BriefEntryContext::IFNAME() {
  return getToken(IpAddrParser::IFNAME, 0);
}

IpAddrParser::LinkStateContext* IpAddrParser::BriefEntryContext::linkState() {
  return getRuleContext<IpAddrParser::LinkStateContext>(0);
}

tree::TerminalNode* IpAddrParser::BriefEntryContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::BriefAddrContext *> IpAddrParser::BriefEntryContext::briefAddr() {
  return getRuleContexts<IpAddrParser::BriefAddrContext>();
}

IpAddrParser::BriefAddrContext* IpAddrParser::BriefEntryContext::briefAddr(size_t i) {
  return getRuleContext<IpAddrParser::BriefAddrContext>(i);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::BriefEntryContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::BriefEntryContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::BriefEntryContext::getRuleIndex() const {
  return IpAddrParser::RuleBriefEntry;
}


std::any IpAddrParser::BriefEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitBriefEntry(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::BriefEntryContext* IpAddrParser::briefEntry() {
  BriefEntryContext *_localctx = _tracker.createInstance<BriefEntryContext>(_ctx, getState());
  enterRule(_localctx, 14, IpAddrParser::RuleBriefEntry);
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
    setState(87);
    match(IpAddrParser::IFNAME);
    setState(88);
    linkState();
    setState(92);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 6, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(89);
        briefAddr(); 
      }
      setState(94);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 6, _ctx);
    }
    setState(98);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(95);
      elem();
      setState(100);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(101);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- LinkStateContext ------------------------------------------------------------------

IpAddrParser::LinkStateContext::LinkStateContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::LinkStateContext::UP() {
  return getToken(IpAddrParser::UP, 0);
}

tree::TerminalNode* IpAddrParser::LinkStateContext::DOWN() {
  return getToken(IpAddrParser::DOWN, 0);
}

tree::TerminalNode* IpAddrParser::LinkStateContext::UNKNOWN() {
  return getToken(IpAddrParser::UNKNOWN, 0);
}


size_t IpAddrParser::LinkStateContext::getRuleIndex() const {
  return IpAddrParser::RuleLinkState;
}


std::any IpAddrParser::LinkStateContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitLinkState(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::LinkStateContext* IpAddrParser::linkState() {
  LinkStateContext *_localctx = _tracker.createInstance<LinkStateContext>(_ctx, getState());
  enterRule(_localctx, 16, IpAddrParser::RuleLinkState);
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
    setState(103);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 14336) != 0))) {
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

//----------------- BriefAddrContext ------------------------------------------------------------------

IpAddrParser::BriefAddrContext::BriefAddrContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::BriefAddrContext::ADDR() {
  return getToken(IpAddrParser::ADDR, 0);
}

tree::TerminalNode* IpAddrParser::BriefAddrContext::MAC() {
  return getToken(IpAddrParser::MAC, 0);
}


size_t IpAddrParser::BriefAddrContext::getRuleIndex() const {
  return IpAddrParser::RuleBriefAddr;
}


std::any IpAddrParser::BriefAddrContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitBriefAddr(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::BriefAddrContext* IpAddrParser::briefAddr() {
  BriefAddrContext *_localctx = _tracker.createInstance<BriefAddrContext>(_ctx, getState());
  enterRule(_localctx, 18, IpAddrParser::RuleBriefAddr);
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
    setState(105);
    _la = _input->LA(1);
    if (!(_la == IpAddrParser::MAC

    || _la == IpAddrParser::ADDR)) {
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

//----------------- RouteEntryContext ------------------------------------------------------------------

IpAddrParser::RouteEntryContext::RouteEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

IpAddrParser::RouteHeadContext* IpAddrParser::RouteEntryContext::routeHead() {
  return getRuleContext<IpAddrParser::RouteHeadContext>(0);
}

tree::TerminalNode* IpAddrParser::RouteEntryContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::RouteEntryContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::RouteEntryContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::RouteEntryContext::getRuleIndex() const {
  return IpAddrParser::RuleRouteEntry;
}


std::any IpAddrParser::RouteEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitRouteEntry(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::RouteEntryContext* IpAddrParser::routeEntry() {
  RouteEntryContext *_localctx = _tracker.createInstance<RouteEntryContext>(_ctx, getState());
  enterRule(_localctx, 20, IpAddrParser::RuleRouteEntry);
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
    routeHead();
    setState(111);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(108);
      elem();
      setState(113);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(114);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RouteHeadContext ------------------------------------------------------------------

IpAddrParser::RouteHeadContext::RouteHeadContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::RouteHeadContext::DEFAULT() {
  return getToken(IpAddrParser::DEFAULT, 0);
}

tree::TerminalNode* IpAddrParser::RouteHeadContext::ADDR() {
  return getToken(IpAddrParser::ADDR, 0);
}

tree::TerminalNode* IpAddrParser::RouteHeadContext::IFNAME() {
  return getToken(IpAddrParser::IFNAME, 0);
}


size_t IpAddrParser::RouteHeadContext::getRuleIndex() const {
  return IpAddrParser::RuleRouteHead;
}


std::any IpAddrParser::RouteHeadContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitRouteHead(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::RouteHeadContext* IpAddrParser::routeHead() {
  RouteHeadContext *_localctx = _tracker.createInstance<RouteHeadContext>(_ctx, getState());
  enterRule(_localctx, 22, IpAddrParser::RuleRouteHead);
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
      ((1ULL << _la) & 16480) != 0))) {
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

//----------------- IfnameContext ------------------------------------------------------------------

IpAddrParser::IfnameContext::IfnameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::IfnameContext::IFNAME() {
  return getToken(IpAddrParser::IFNAME, 0);
}


size_t IpAddrParser::IfnameContext::getRuleIndex() const {
  return IpAddrParser::RuleIfname;
}


std::any IpAddrParser::IfnameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitIfname(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::IfnameContext* IpAddrParser::ifname() {
  IfnameContext *_localctx = _tracker.createInstance<IfnameContext>(_ctx, getState());
  enterRule(_localctx, 24, IpAddrParser::RuleIfname);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(118);
    match(IpAddrParser::IFNAME);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- GenericLineContext ------------------------------------------------------------------

IpAddrParser::GenericLineContext::GenericLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::GenericLineContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::GenericLineContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::GenericLineContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::GenericLineContext::getRuleIndex() const {
  return IpAddrParser::RuleGenericLine;
}


std::any IpAddrParser::GenericLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitGenericLine(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::GenericLineContext* IpAddrParser::genericLine() {
  GenericLineContext *_localctx = _tracker.createInstance<GenericLineContext>(_ctx, getState());
  enterRule(_localctx, 26, IpAddrParser::RuleGenericLine);
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
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(120);
      elem();
      setState(123); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0));
    setState(125);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlankContext ------------------------------------------------------------------

IpAddrParser::BlankContext::BlankContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::BlankContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}


size_t IpAddrParser::BlankContext::getRuleIndex() const {
  return IpAddrParser::RuleBlank;
}


std::any IpAddrParser::BlankContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitBlank(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::BlankContext* IpAddrParser::blank() {
  BlankContext *_localctx = _tracker.createInstance<BlankContext>(_ctx, getState());
  enterRule(_localctx, 28, IpAddrParser::RuleBlank);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(127);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ElemContext ------------------------------------------------------------------

IpAddrParser::ElemContext::ElemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::ElemContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}


size_t IpAddrParser::ElemContext::getRuleIndex() const {
  return IpAddrParser::RuleElem;
}


std::any IpAddrParser::ElemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitElem(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::ElemContext* IpAddrParser::elem() {
  ElemContext *_localctx = _tracker.createInstance<ElemContext>(_ctx, getState());
  enterRule(_localctx, 30, IpAddrParser::RuleElem);
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
    setState(129);
    _la = _input->LA(1);
    if (_la == 0 || _la == Token::EOF || (_la == IpAddrParser::NEWLINE)) {
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

void IpAddrParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  ipaddrParserInitialize();
#else
  ::antlr4::internal::call_once(ipaddrParserOnceFlag, ipaddrParserInitialize);
#endif
}
