
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
      "document", "briefDocument", "routeDocument", "arpDocument", "arpEntry", 
      "arpAddress", "item", "ifaceHeader", "ifaceAttr", "attrLead", "briefEntry", 
      "linkState", "briefAddr", "routeEntry", "routeHead", "ifname", "genericLine", 
      "blank", "elem"
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
  	4,1,17,159,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,1,0,5,0,40,8,0,10,0,12,0,43,
  	9,0,1,0,1,0,1,1,5,1,48,8,1,10,1,12,1,51,9,1,1,1,1,1,1,2,5,2,56,8,2,10,
  	2,12,2,59,9,2,1,2,1,2,1,3,1,3,1,3,5,3,66,8,3,10,3,12,3,69,9,3,1,3,1,3,
  	1,4,1,4,5,4,75,8,4,10,4,12,4,78,9,4,1,4,1,4,1,5,1,5,1,6,1,6,1,6,1,6,1,
  	6,1,6,3,6,90,8,6,1,7,1,7,1,7,1,7,1,7,5,7,97,8,7,10,7,12,7,100,9,7,1,7,
  	1,7,1,8,1,8,5,8,106,8,8,10,8,12,8,109,9,8,1,8,1,8,1,9,1,9,1,10,1,10,1,
  	10,5,10,118,8,10,10,10,12,10,121,9,10,1,10,5,10,124,8,10,10,10,12,10,
  	127,9,10,1,10,1,10,1,11,1,11,1,12,1,12,1,13,1,13,5,13,137,8,13,10,13,
  	12,13,140,9,13,1,13,1,13,1,14,1,14,1,15,1,15,1,16,4,16,149,8,16,11,16,
  	12,16,150,1,16,1,16,1,17,1,17,1,18,1,18,1,18,0,0,19,0,2,4,6,8,10,12,14,
  	16,18,20,22,24,26,28,30,32,34,36,0,6,2,0,5,5,14,14,1,0,7,10,1,0,11,13,
  	1,0,4,5,2,0,5,6,14,14,1,0,1,1,157,0,41,1,0,0,0,2,49,1,0,0,0,4,57,1,0,
  	0,0,6,67,1,0,0,0,8,72,1,0,0,0,10,81,1,0,0,0,12,89,1,0,0,0,14,91,1,0,0,
  	0,16,103,1,0,0,0,18,112,1,0,0,0,20,114,1,0,0,0,22,130,1,0,0,0,24,132,
  	1,0,0,0,26,134,1,0,0,0,28,143,1,0,0,0,30,145,1,0,0,0,32,148,1,0,0,0,34,
  	154,1,0,0,0,36,156,1,0,0,0,38,40,3,12,6,0,39,38,1,0,0,0,40,43,1,0,0,0,
  	41,39,1,0,0,0,41,42,1,0,0,0,42,44,1,0,0,0,43,41,1,0,0,0,44,45,5,0,0,1,
  	45,1,1,0,0,0,46,48,3,12,6,0,47,46,1,0,0,0,48,51,1,0,0,0,49,47,1,0,0,0,
  	49,50,1,0,0,0,50,52,1,0,0,0,51,49,1,0,0,0,52,53,5,0,0,1,53,3,1,0,0,0,
  	54,56,3,12,6,0,55,54,1,0,0,0,56,59,1,0,0,0,57,55,1,0,0,0,57,58,1,0,0,
  	0,58,60,1,0,0,0,59,57,1,0,0,0,60,61,5,0,0,1,61,5,1,0,0,0,62,66,3,8,4,
  	0,63,66,3,32,16,0,64,66,3,34,17,0,65,62,1,0,0,0,65,63,1,0,0,0,65,64,1,
  	0,0,0,66,69,1,0,0,0,67,65,1,0,0,0,67,68,1,0,0,0,68,70,1,0,0,0,69,67,1,
  	0,0,0,70,71,5,0,0,1,71,7,1,0,0,0,72,76,3,10,5,0,73,75,3,36,18,0,74,73,
  	1,0,0,0,75,78,1,0,0,0,76,74,1,0,0,0,76,77,1,0,0,0,77,79,1,0,0,0,78,76,
  	1,0,0,0,79,80,5,1,0,0,80,9,1,0,0,0,81,82,7,0,0,0,82,11,1,0,0,0,83,90,
  	3,14,7,0,84,90,3,16,8,0,85,90,3,20,10,0,86,90,3,26,13,0,87,90,3,32,16,
  	0,88,90,3,34,17,0,89,83,1,0,0,0,89,84,1,0,0,0,89,85,1,0,0,0,89,86,1,0,
  	0,0,89,87,1,0,0,0,89,88,1,0,0,0,90,13,1,0,0,0,91,92,5,3,0,0,92,93,5,15,
  	0,0,93,94,3,30,15,0,94,98,5,15,0,0,95,97,3,36,18,0,96,95,1,0,0,0,97,100,
  	1,0,0,0,98,96,1,0,0,0,98,99,1,0,0,0,99,101,1,0,0,0,100,98,1,0,0,0,101,
  	102,5,1,0,0,102,15,1,0,0,0,103,107,3,18,9,0,104,106,3,36,18,0,105,104,
  	1,0,0,0,106,109,1,0,0,0,107,105,1,0,0,0,107,108,1,0,0,0,108,110,1,0,0,
  	0,109,107,1,0,0,0,110,111,5,1,0,0,111,17,1,0,0,0,112,113,7,1,0,0,113,
  	19,1,0,0,0,114,115,5,14,0,0,115,119,3,22,11,0,116,118,3,24,12,0,117,116,
  	1,0,0,0,118,121,1,0,0,0,119,117,1,0,0,0,119,120,1,0,0,0,120,125,1,0,0,
  	0,121,119,1,0,0,0,122,124,3,36,18,0,123,122,1,0,0,0,124,127,1,0,0,0,125,
  	123,1,0,0,0,125,126,1,0,0,0,126,128,1,0,0,0,127,125,1,0,0,0,128,129,5,
  	1,0,0,129,21,1,0,0,0,130,131,7,2,0,0,131,23,1,0,0,0,132,133,7,3,0,0,133,
  	25,1,0,0,0,134,138,3,28,14,0,135,137,3,36,18,0,136,135,1,0,0,0,137,140,
  	1,0,0,0,138,136,1,0,0,0,138,139,1,0,0,0,139,141,1,0,0,0,140,138,1,0,0,
  	0,141,142,5,1,0,0,142,27,1,0,0,0,143,144,7,4,0,0,144,29,1,0,0,0,145,146,
  	5,14,0,0,146,31,1,0,0,0,147,149,3,36,18,0,148,147,1,0,0,0,149,150,1,0,
  	0,0,150,148,1,0,0,0,150,151,1,0,0,0,151,152,1,0,0,0,152,153,5,1,0,0,153,
  	33,1,0,0,0,154,155,5,1,0,0,155,35,1,0,0,0,156,157,8,5,0,0,157,37,1,0,
  	0,0,13,41,49,57,65,67,76,89,98,107,119,125,138,150
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
    setState(41);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262142) != 0)) {
      setState(38);
      item();
      setState(43);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(44);
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
    setState(49);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262142) != 0)) {
      setState(46);
      item();
      setState(51);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(52);
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
    setState(57);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262142) != 0)) {
      setState(54);
      item();
      setState(59);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(60);
    match(IpAddrParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ArpDocumentContext ------------------------------------------------------------------

IpAddrParser::ArpDocumentContext::ArpDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::ArpDocumentContext::EOF() {
  return getToken(IpAddrParser::EOF, 0);
}

std::vector<IpAddrParser::ArpEntryContext *> IpAddrParser::ArpDocumentContext::arpEntry() {
  return getRuleContexts<IpAddrParser::ArpEntryContext>();
}

IpAddrParser::ArpEntryContext* IpAddrParser::ArpDocumentContext::arpEntry(size_t i) {
  return getRuleContext<IpAddrParser::ArpEntryContext>(i);
}

std::vector<IpAddrParser::GenericLineContext *> IpAddrParser::ArpDocumentContext::genericLine() {
  return getRuleContexts<IpAddrParser::GenericLineContext>();
}

IpAddrParser::GenericLineContext* IpAddrParser::ArpDocumentContext::genericLine(size_t i) {
  return getRuleContext<IpAddrParser::GenericLineContext>(i);
}

std::vector<IpAddrParser::BlankContext *> IpAddrParser::ArpDocumentContext::blank() {
  return getRuleContexts<IpAddrParser::BlankContext>();
}

IpAddrParser::BlankContext* IpAddrParser::ArpDocumentContext::blank(size_t i) {
  return getRuleContext<IpAddrParser::BlankContext>(i);
}


size_t IpAddrParser::ArpDocumentContext::getRuleIndex() const {
  return IpAddrParser::RuleArpDocument;
}


std::any IpAddrParser::ArpDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitArpDocument(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::ArpDocumentContext* IpAddrParser::arpDocument() {
  ArpDocumentContext *_localctx = _tracker.createInstance<ArpDocumentContext>(_ctx, getState());
  enterRule(_localctx, 6, IpAddrParser::RuleArpDocument);
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
    setState(67);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262142) != 0)) {
      setState(65);
      _errHandler->sync(this);
      switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 3, _ctx)) {
      case 1: {
        setState(62);
        arpEntry();
        break;
      }

      case 2: {
        setState(63);
        genericLine();
        break;
      }

      case 3: {
        setState(64);
        blank();
        break;
      }

      default:
        break;
      }
      setState(69);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(70);
    match(IpAddrParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ArpEntryContext ------------------------------------------------------------------

IpAddrParser::ArpEntryContext::ArpEntryContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

IpAddrParser::ArpAddressContext* IpAddrParser::ArpEntryContext::arpAddress() {
  return getRuleContext<IpAddrParser::ArpAddressContext>(0);
}

tree::TerminalNode* IpAddrParser::ArpEntryContext::NEWLINE() {
  return getToken(IpAddrParser::NEWLINE, 0);
}

std::vector<IpAddrParser::ElemContext *> IpAddrParser::ArpEntryContext::elem() {
  return getRuleContexts<IpAddrParser::ElemContext>();
}

IpAddrParser::ElemContext* IpAddrParser::ArpEntryContext::elem(size_t i) {
  return getRuleContext<IpAddrParser::ElemContext>(i);
}


size_t IpAddrParser::ArpEntryContext::getRuleIndex() const {
  return IpAddrParser::RuleArpEntry;
}


std::any IpAddrParser::ArpEntryContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitArpEntry(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::ArpEntryContext* IpAddrParser::arpEntry() {
  ArpEntryContext *_localctx = _tracker.createInstance<ArpEntryContext>(_ctx, getState());
  enterRule(_localctx, 8, IpAddrParser::RuleArpEntry);
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
    setState(72);
    arpAddress();
    setState(76);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(73);
      elem();
      setState(78);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(79);
    match(IpAddrParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ArpAddressContext ------------------------------------------------------------------

IpAddrParser::ArpAddressContext::ArpAddressContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* IpAddrParser::ArpAddressContext::ADDR() {
  return getToken(IpAddrParser::ADDR, 0);
}

tree::TerminalNode* IpAddrParser::ArpAddressContext::IFNAME() {
  return getToken(IpAddrParser::IFNAME, 0);
}


size_t IpAddrParser::ArpAddressContext::getRuleIndex() const {
  return IpAddrParser::RuleArpAddress;
}


std::any IpAddrParser::ArpAddressContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<IpAddrVisitor*>(visitor))
    return parserVisitor->visitArpAddress(this);
  else
    return visitor->visitChildren(this);
}

IpAddrParser::ArpAddressContext* IpAddrParser::arpAddress() {
  ArpAddressContext *_localctx = _tracker.createInstance<ArpAddressContext>(_ctx, getState());
  enterRule(_localctx, 10, IpAddrParser::RuleArpAddress);
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
    _la = _input->LA(1);
    if (!(_la == IpAddrParser::ADDR

    || _la == IpAddrParser::IFNAME)) {
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
  enterRule(_localctx, 12, IpAddrParser::RuleItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(89);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 6, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(83);
      ifaceHeader();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(84);
      ifaceAttr();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(85);
      briefEntry();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(86);
      routeEntry();
      break;
    }

    case 5: {
      enterOuterAlt(_localctx, 5);
      setState(87);
      genericLine();
      break;
    }

    case 6: {
      enterOuterAlt(_localctx, 6);
      setState(88);
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
  enterRule(_localctx, 14, IpAddrParser::RuleIfaceHeader);
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
    setState(91);
    match(IpAddrParser::INDEX);
    setState(92);
    match(IpAddrParser::COLON);
    setState(93);
    ifname();
    setState(94);
    match(IpAddrParser::COLON);
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
  enterRule(_localctx, 16, IpAddrParser::RuleIfaceAttr);
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
    attrLead();
    setState(107);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(104);
      elem();
      setState(109);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(110);
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
  enterRule(_localctx, 18, IpAddrParser::RuleAttrLead);
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
    setState(112);
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
  enterRule(_localctx, 20, IpAddrParser::RuleBriefEntry);
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
    setState(114);
    match(IpAddrParser::IFNAME);
    setState(115);
    linkState();
    setState(119);
    _errHandler->sync(this);
    alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 9, _ctx);
    while (alt != 2 && alt != atn::ATN::INVALID_ALT_NUMBER) {
      if (alt == 1) {
        setState(116);
        briefAddr(); 
      }
      setState(121);
      _errHandler->sync(this);
      alt = getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 9, _ctx);
    }
    setState(125);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(122);
      elem();
      setState(127);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(128);
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
  enterRule(_localctx, 22, IpAddrParser::RuleLinkState);
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
    setState(130);
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
  enterRule(_localctx, 24, IpAddrParser::RuleBriefAddr);
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
    setState(132);
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
  enterRule(_localctx, 26, IpAddrParser::RuleRouteEntry);
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
    setState(134);
    routeHead();
    setState(138);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0)) {
      setState(135);
      elem();
      setState(140);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(141);
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
  enterRule(_localctx, 28, IpAddrParser::RuleRouteHead);
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
  enterRule(_localctx, 30, IpAddrParser::RuleIfname);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(145);
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
  enterRule(_localctx, 32, IpAddrParser::RuleGenericLine);
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
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(147);
      elem();
      setState(150); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 262140) != 0));
    setState(152);
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
  enterRule(_localctx, 34, IpAddrParser::RuleBlank);

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
  enterRule(_localctx, 36, IpAddrParser::RuleElem);
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
