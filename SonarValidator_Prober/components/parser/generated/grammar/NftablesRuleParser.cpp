
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/NftablesRule.g4 by ANTLR 4.13.2


#include "NftablesRuleVisitor.h"

#include "NftablesRuleParser.h"


using namespace antlrcpp;

using namespace antlr4;

namespace {

struct NftablesRuleParserStaticData final {
  NftablesRuleParserStaticData(std::vector<std::string> ruleNames,
                        std::vector<std::string> literalNames,
                        std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  NftablesRuleParserStaticData(const NftablesRuleParserStaticData&) = delete;
  NftablesRuleParserStaticData(NftablesRuleParserStaticData&&) = delete;
  NftablesRuleParserStaticData& operator=(const NftablesRuleParserStaticData&) = delete;
  NftablesRuleParserStaticData& operator=(NftablesRuleParserStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag nftablesruleParserOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<NftablesRuleParserStaticData> nftablesruleParserStaticData = nullptr;

void nftablesruleParserInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (nftablesruleParserStaticData != nullptr) {
    return;
  }
#else
  assert(nftablesruleParserStaticData == nullptr);
#endif
  auto staticData = std::make_unique<NftablesRuleParserStaticData>(
    std::vector<std::string>{
      "rulesetDocument", "chainDocument", "rulesetItem", "chainItem", "tableBlock", 
      "bodyBlock", "familyBlock", "tableName", "chainBlock", "chainBody", 
      "chainName", "chainAttr", "nameToken", "ruleLine", "rulePiece", "genericLine", 
      "blank", "elem"
    },
    std::vector<std::string>{
      "", "", "", "", "", "", "'table'", "'chain'", "'{'", "'}'", "';'", 
      "'/'", "':'"
    },
    std::vector<std::string>{
      "", "ATTRKEY", "MATCHKEY", "ACTION", "NEWLINE", "WS", "TABLE", "CHAIN", 
      "OPEN_BRACE", "CLOSE_BRACE", "SEMICOLON", "SLASH", "COLON", "ADDR", 
      "NUMBER", "DASHED_IDENT", "QUOTED", "IDENT", "RULEWORD"
    }
  );
  static const int32_t serializedATNSegment[] = {
  	4,1,18,141,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,6,2,
  	7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,7,
  	14,2,15,7,15,2,16,7,16,2,17,7,17,1,0,5,0,38,8,0,10,0,12,0,41,9,0,1,0,
  	1,0,1,1,5,1,46,8,1,10,1,12,1,49,9,1,1,1,1,1,1,2,1,2,1,2,3,2,56,8,2,1,
  	3,1,3,1,3,1,3,3,3,62,8,3,1,4,1,4,1,4,1,4,1,4,1,4,5,4,70,8,4,10,4,12,4,
  	73,9,4,1,4,1,4,3,4,77,8,4,1,5,1,5,1,5,3,5,82,8,5,1,6,1,6,1,7,1,7,1,8,
  	1,8,1,8,1,8,1,8,5,8,93,8,8,10,8,12,8,96,9,8,1,8,1,8,3,8,100,8,8,1,9,1,
  	9,1,9,1,9,3,9,106,8,9,1,10,1,10,1,11,1,11,5,11,112,8,11,10,11,12,11,115,
  	9,11,1,11,1,11,1,12,1,12,1,13,4,13,122,8,13,11,13,12,13,123,1,13,1,13,
  	1,14,1,14,1,15,4,15,131,8,15,11,15,12,15,132,1,15,1,15,1,16,1,16,1,17,
  	1,17,1,17,0,0,18,0,2,4,6,8,10,12,14,16,18,20,22,24,26,28,30,32,34,0,3,
  	4,0,1,3,6,7,14,15,17,17,2,0,2,3,13,18,2,0,4,4,8,9,141,0,39,1,0,0,0,2,
  	47,1,0,0,0,4,55,1,0,0,0,6,61,1,0,0,0,8,63,1,0,0,0,10,81,1,0,0,0,12,83,
  	1,0,0,0,14,85,1,0,0,0,16,87,1,0,0,0,18,105,1,0,0,0,20,107,1,0,0,0,22,
  	109,1,0,0,0,24,118,1,0,0,0,26,121,1,0,0,0,28,127,1,0,0,0,30,130,1,0,0,
  	0,32,136,1,0,0,0,34,138,1,0,0,0,36,38,3,4,2,0,37,36,1,0,0,0,38,41,1,0,
  	0,0,39,37,1,0,0,0,39,40,1,0,0,0,40,42,1,0,0,0,41,39,1,0,0,0,42,43,5,0,
  	0,1,43,1,1,0,0,0,44,46,3,6,3,0,45,44,1,0,0,0,46,49,1,0,0,0,47,45,1,0,
  	0,0,47,48,1,0,0,0,48,50,1,0,0,0,49,47,1,0,0,0,50,51,5,0,0,1,51,3,1,0,
  	0,0,52,56,3,8,4,0,53,56,3,30,15,0,54,56,3,32,16,0,55,52,1,0,0,0,55,53,
  	1,0,0,0,55,54,1,0,0,0,56,5,1,0,0,0,57,62,3,22,11,0,58,62,3,26,13,0,59,
  	62,3,30,15,0,60,62,3,32,16,0,61,57,1,0,0,0,61,58,1,0,0,0,61,59,1,0,0,
  	0,61,60,1,0,0,0,62,7,1,0,0,0,63,64,5,6,0,0,64,65,3,12,6,0,65,66,3,14,
  	7,0,66,67,5,8,0,0,67,71,5,4,0,0,68,70,3,10,5,0,69,68,1,0,0,0,70,73,1,
  	0,0,0,71,69,1,0,0,0,71,72,1,0,0,0,72,74,1,0,0,0,73,71,1,0,0,0,74,76,5,
  	9,0,0,75,77,5,4,0,0,76,75,1,0,0,0,76,77,1,0,0,0,77,9,1,0,0,0,78,82,3,
  	16,8,0,79,82,3,30,15,0,80,82,3,32,16,0,81,78,1,0,0,0,81,79,1,0,0,0,81,
  	80,1,0,0,0,82,11,1,0,0,0,83,84,3,24,12,0,84,13,1,0,0,0,85,86,3,24,12,
  	0,86,15,1,0,0,0,87,88,5,7,0,0,88,89,3,20,10,0,89,90,5,8,0,0,90,94,5,4,
  	0,0,91,93,3,18,9,0,92,91,1,0,0,0,93,96,1,0,0,0,94,92,1,0,0,0,94,95,1,
  	0,0,0,95,97,1,0,0,0,96,94,1,0,0,0,97,99,5,9,0,0,98,100,5,4,0,0,99,98,
  	1,0,0,0,99,100,1,0,0,0,100,17,1,0,0,0,101,106,3,22,11,0,102,106,3,26,
  	13,0,103,106,3,30,15,0,104,106,3,32,16,0,105,101,1,0,0,0,105,102,1,0,
  	0,0,105,103,1,0,0,0,105,104,1,0,0,0,106,19,1,0,0,0,107,108,3,24,12,0,
  	108,21,1,0,0,0,109,113,5,1,0,0,110,112,3,34,17,0,111,110,1,0,0,0,112,
  	115,1,0,0,0,113,111,1,0,0,0,113,114,1,0,0,0,114,116,1,0,0,0,115,113,1,
  	0,0,0,116,117,5,4,0,0,117,23,1,0,0,0,118,119,7,0,0,0,119,25,1,0,0,0,120,
  	122,3,28,14,0,121,120,1,0,0,0,122,123,1,0,0,0,123,121,1,0,0,0,123,124,
  	1,0,0,0,124,125,1,0,0,0,125,126,5,4,0,0,126,27,1,0,0,0,127,128,7,1,0,
  	0,128,29,1,0,0,0,129,131,3,34,17,0,130,129,1,0,0,0,131,132,1,0,0,0,132,
  	130,1,0,0,0,132,133,1,0,0,0,133,134,1,0,0,0,134,135,5,4,0,0,135,31,1,
  	0,0,0,136,137,5,4,0,0,137,33,1,0,0,0,138,139,8,2,0,0,139,35,1,0,0,0,13,
  	39,47,55,61,71,76,81,94,99,105,113,123,132
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  nftablesruleParserStaticData = std::move(staticData);
}

}

NftablesRuleParser::NftablesRuleParser(TokenStream *input) : NftablesRuleParser(input, antlr4::atn::ParserATNSimulatorOptions()) {}

NftablesRuleParser::NftablesRuleParser(TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options) : Parser(input) {
  NftablesRuleParser::initialize();
  _interpreter = new atn::ParserATNSimulator(this, *nftablesruleParserStaticData->atn, nftablesruleParserStaticData->decisionToDFA, nftablesruleParserStaticData->sharedContextCache, options);
}

NftablesRuleParser::~NftablesRuleParser() {
  delete _interpreter;
}

const atn::ATN& NftablesRuleParser::getATN() const {
  return *nftablesruleParserStaticData->atn;
}

std::string NftablesRuleParser::getGrammarFileName() const {
  return "NftablesRule.g4";
}

const std::vector<std::string>& NftablesRuleParser::getRuleNames() const {
  return nftablesruleParserStaticData->ruleNames;
}

const dfa::Vocabulary& NftablesRuleParser::getVocabulary() const {
  return nftablesruleParserStaticData->vocabulary;
}

antlr4::atn::SerializedATNView NftablesRuleParser::getSerializedATN() const {
  return nftablesruleParserStaticData->serializedATN;
}


//----------------- RulesetDocumentContext ------------------------------------------------------------------

NftablesRuleParser::RulesetDocumentContext::RulesetDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::RulesetDocumentContext::EOF() {
  return getToken(NftablesRuleParser::EOF, 0);
}

std::vector<NftablesRuleParser::RulesetItemContext *> NftablesRuleParser::RulesetDocumentContext::rulesetItem() {
  return getRuleContexts<NftablesRuleParser::RulesetItemContext>();
}

NftablesRuleParser::RulesetItemContext* NftablesRuleParser::RulesetDocumentContext::rulesetItem(size_t i) {
  return getRuleContext<NftablesRuleParser::RulesetItemContext>(i);
}


size_t NftablesRuleParser::RulesetDocumentContext::getRuleIndex() const {
  return NftablesRuleParser::RuleRulesetDocument;
}


std::any NftablesRuleParser::RulesetDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitRulesetDocument(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::RulesetDocumentContext* NftablesRuleParser::rulesetDocument() {
  RulesetDocumentContext *_localctx = _tracker.createInstance<RulesetDocumentContext>(_ctx, getState());
  enterRule(_localctx, 0, NftablesRuleParser::RuleRulesetDocument);
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
      ((1ULL << _la) & 523518) != 0)) {
      setState(36);
      rulesetItem();
      setState(41);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(42);
    match(NftablesRuleParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ChainDocumentContext ------------------------------------------------------------------

NftablesRuleParser::ChainDocumentContext::ChainDocumentContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::ChainDocumentContext::EOF() {
  return getToken(NftablesRuleParser::EOF, 0);
}

std::vector<NftablesRuleParser::ChainItemContext *> NftablesRuleParser::ChainDocumentContext::chainItem() {
  return getRuleContexts<NftablesRuleParser::ChainItemContext>();
}

NftablesRuleParser::ChainItemContext* NftablesRuleParser::ChainDocumentContext::chainItem(size_t i) {
  return getRuleContext<NftablesRuleParser::ChainItemContext>(i);
}


size_t NftablesRuleParser::ChainDocumentContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainDocument;
}


std::any NftablesRuleParser::ChainDocumentContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainDocument(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainDocumentContext* NftablesRuleParser::chainDocument() {
  ChainDocumentContext *_localctx = _tracker.createInstance<ChainDocumentContext>(_ctx, getState());
  enterRule(_localctx, 2, NftablesRuleParser::RuleChainDocument);
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
      ((1ULL << _la) & 523518) != 0)) {
      setState(44);
      chainItem();
      setState(49);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(50);
    match(NftablesRuleParser::EOF);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RulesetItemContext ------------------------------------------------------------------

NftablesRuleParser::RulesetItemContext::RulesetItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::TableBlockContext* NftablesRuleParser::RulesetItemContext::tableBlock() {
  return getRuleContext<NftablesRuleParser::TableBlockContext>(0);
}

NftablesRuleParser::GenericLineContext* NftablesRuleParser::RulesetItemContext::genericLine() {
  return getRuleContext<NftablesRuleParser::GenericLineContext>(0);
}

NftablesRuleParser::BlankContext* NftablesRuleParser::RulesetItemContext::blank() {
  return getRuleContext<NftablesRuleParser::BlankContext>(0);
}


size_t NftablesRuleParser::RulesetItemContext::getRuleIndex() const {
  return NftablesRuleParser::RuleRulesetItem;
}


std::any NftablesRuleParser::RulesetItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitRulesetItem(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::RulesetItemContext* NftablesRuleParser::rulesetItem() {
  RulesetItemContext *_localctx = _tracker.createInstance<RulesetItemContext>(_ctx, getState());
  enterRule(_localctx, 4, NftablesRuleParser::RuleRulesetItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(55);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 2, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(52);
      tableBlock();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(53);
      genericLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(54);
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

//----------------- ChainItemContext ------------------------------------------------------------------

NftablesRuleParser::ChainItemContext::ChainItemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::ChainAttrContext* NftablesRuleParser::ChainItemContext::chainAttr() {
  return getRuleContext<NftablesRuleParser::ChainAttrContext>(0);
}

NftablesRuleParser::RuleLineContext* NftablesRuleParser::ChainItemContext::ruleLine() {
  return getRuleContext<NftablesRuleParser::RuleLineContext>(0);
}

NftablesRuleParser::GenericLineContext* NftablesRuleParser::ChainItemContext::genericLine() {
  return getRuleContext<NftablesRuleParser::GenericLineContext>(0);
}

NftablesRuleParser::BlankContext* NftablesRuleParser::ChainItemContext::blank() {
  return getRuleContext<NftablesRuleParser::BlankContext>(0);
}


size_t NftablesRuleParser::ChainItemContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainItem;
}


std::any NftablesRuleParser::ChainItemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainItem(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainItemContext* NftablesRuleParser::chainItem() {
  ChainItemContext *_localctx = _tracker.createInstance<ChainItemContext>(_ctx, getState());
  enterRule(_localctx, 6, NftablesRuleParser::RuleChainItem);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(61);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 3, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(57);
      chainAttr();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(58);
      ruleLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(59);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(60);
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

//----------------- TableBlockContext ------------------------------------------------------------------

NftablesRuleParser::TableBlockContext::TableBlockContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::TableBlockContext::TABLE() {
  return getToken(NftablesRuleParser::TABLE, 0);
}

NftablesRuleParser::FamilyBlockContext* NftablesRuleParser::TableBlockContext::familyBlock() {
  return getRuleContext<NftablesRuleParser::FamilyBlockContext>(0);
}

NftablesRuleParser::TableNameContext* NftablesRuleParser::TableBlockContext::tableName() {
  return getRuleContext<NftablesRuleParser::TableNameContext>(0);
}

tree::TerminalNode* NftablesRuleParser::TableBlockContext::OPEN_BRACE() {
  return getToken(NftablesRuleParser::OPEN_BRACE, 0);
}

std::vector<tree::TerminalNode *> NftablesRuleParser::TableBlockContext::NEWLINE() {
  return getTokens(NftablesRuleParser::NEWLINE);
}

tree::TerminalNode* NftablesRuleParser::TableBlockContext::NEWLINE(size_t i) {
  return getToken(NftablesRuleParser::NEWLINE, i);
}

tree::TerminalNode* NftablesRuleParser::TableBlockContext::CLOSE_BRACE() {
  return getToken(NftablesRuleParser::CLOSE_BRACE, 0);
}

std::vector<NftablesRuleParser::BodyBlockContext *> NftablesRuleParser::TableBlockContext::bodyBlock() {
  return getRuleContexts<NftablesRuleParser::BodyBlockContext>();
}

NftablesRuleParser::BodyBlockContext* NftablesRuleParser::TableBlockContext::bodyBlock(size_t i) {
  return getRuleContext<NftablesRuleParser::BodyBlockContext>(i);
}


size_t NftablesRuleParser::TableBlockContext::getRuleIndex() const {
  return NftablesRuleParser::RuleTableBlock;
}


std::any NftablesRuleParser::TableBlockContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitTableBlock(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::TableBlockContext* NftablesRuleParser::tableBlock() {
  TableBlockContext *_localctx = _tracker.createInstance<TableBlockContext>(_ctx, getState());
  enterRule(_localctx, 8, NftablesRuleParser::RuleTableBlock);
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
    match(NftablesRuleParser::TABLE);
    setState(64);
    familyBlock();
    setState(65);
    tableName();
    setState(66);
    match(NftablesRuleParser::OPEN_BRACE);
    setState(67);
    match(NftablesRuleParser::NEWLINE);
    setState(71);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 523518) != 0)) {
      setState(68);
      bodyBlock();
      setState(73);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(74);
    match(NftablesRuleParser::CLOSE_BRACE);
    setState(76);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 5, _ctx)) {
    case 1: {
      setState(75);
      match(NftablesRuleParser::NEWLINE);
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

//----------------- BodyBlockContext ------------------------------------------------------------------

NftablesRuleParser::BodyBlockContext::BodyBlockContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::ChainBlockContext* NftablesRuleParser::BodyBlockContext::chainBlock() {
  return getRuleContext<NftablesRuleParser::ChainBlockContext>(0);
}

NftablesRuleParser::GenericLineContext* NftablesRuleParser::BodyBlockContext::genericLine() {
  return getRuleContext<NftablesRuleParser::GenericLineContext>(0);
}

NftablesRuleParser::BlankContext* NftablesRuleParser::BodyBlockContext::blank() {
  return getRuleContext<NftablesRuleParser::BlankContext>(0);
}


size_t NftablesRuleParser::BodyBlockContext::getRuleIndex() const {
  return NftablesRuleParser::RuleBodyBlock;
}


std::any NftablesRuleParser::BodyBlockContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitBodyBlock(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::BodyBlockContext* NftablesRuleParser::bodyBlock() {
  BodyBlockContext *_localctx = _tracker.createInstance<BodyBlockContext>(_ctx, getState());
  enterRule(_localctx, 10, NftablesRuleParser::RuleBodyBlock);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(81);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 6, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(78);
      chainBlock();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(79);
      genericLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(80);
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

//----------------- FamilyBlockContext ------------------------------------------------------------------

NftablesRuleParser::FamilyBlockContext::FamilyBlockContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::NameTokenContext* NftablesRuleParser::FamilyBlockContext::nameToken() {
  return getRuleContext<NftablesRuleParser::NameTokenContext>(0);
}


size_t NftablesRuleParser::FamilyBlockContext::getRuleIndex() const {
  return NftablesRuleParser::RuleFamilyBlock;
}


std::any NftablesRuleParser::FamilyBlockContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitFamilyBlock(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::FamilyBlockContext* NftablesRuleParser::familyBlock() {
  FamilyBlockContext *_localctx = _tracker.createInstance<FamilyBlockContext>(_ctx, getState());
  enterRule(_localctx, 12, NftablesRuleParser::RuleFamilyBlock);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    enterOuterAlt(_localctx, 1);
    setState(83);
    nameToken();
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- TableNameContext ------------------------------------------------------------------

NftablesRuleParser::TableNameContext::TableNameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::NameTokenContext* NftablesRuleParser::TableNameContext::nameToken() {
  return getRuleContext<NftablesRuleParser::NameTokenContext>(0);
}


size_t NftablesRuleParser::TableNameContext::getRuleIndex() const {
  return NftablesRuleParser::RuleTableName;
}


std::any NftablesRuleParser::TableNameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitTableName(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::TableNameContext* NftablesRuleParser::tableName() {
  TableNameContext *_localctx = _tracker.createInstance<TableNameContext>(_ctx, getState());
  enterRule(_localctx, 14, NftablesRuleParser::RuleTableName);

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
    nameToken();
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ChainBlockContext ------------------------------------------------------------------

NftablesRuleParser::ChainBlockContext::ChainBlockContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::ChainBlockContext::CHAIN() {
  return getToken(NftablesRuleParser::CHAIN, 0);
}

NftablesRuleParser::ChainNameContext* NftablesRuleParser::ChainBlockContext::chainName() {
  return getRuleContext<NftablesRuleParser::ChainNameContext>(0);
}

tree::TerminalNode* NftablesRuleParser::ChainBlockContext::OPEN_BRACE() {
  return getToken(NftablesRuleParser::OPEN_BRACE, 0);
}

std::vector<tree::TerminalNode *> NftablesRuleParser::ChainBlockContext::NEWLINE() {
  return getTokens(NftablesRuleParser::NEWLINE);
}

tree::TerminalNode* NftablesRuleParser::ChainBlockContext::NEWLINE(size_t i) {
  return getToken(NftablesRuleParser::NEWLINE, i);
}

tree::TerminalNode* NftablesRuleParser::ChainBlockContext::CLOSE_BRACE() {
  return getToken(NftablesRuleParser::CLOSE_BRACE, 0);
}

std::vector<NftablesRuleParser::ChainBodyContext *> NftablesRuleParser::ChainBlockContext::chainBody() {
  return getRuleContexts<NftablesRuleParser::ChainBodyContext>();
}

NftablesRuleParser::ChainBodyContext* NftablesRuleParser::ChainBlockContext::chainBody(size_t i) {
  return getRuleContext<NftablesRuleParser::ChainBodyContext>(i);
}


size_t NftablesRuleParser::ChainBlockContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainBlock;
}


std::any NftablesRuleParser::ChainBlockContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainBlock(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainBlockContext* NftablesRuleParser::chainBlock() {
  ChainBlockContext *_localctx = _tracker.createInstance<ChainBlockContext>(_ctx, getState());
  enterRule(_localctx, 16, NftablesRuleParser::RuleChainBlock);
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
    match(NftablesRuleParser::CHAIN);
    setState(88);
    chainName();
    setState(89);
    match(NftablesRuleParser::OPEN_BRACE);
    setState(90);
    match(NftablesRuleParser::NEWLINE);
    setState(94);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 523518) != 0)) {
      setState(91);
      chainBody();
      setState(96);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(97);
    match(NftablesRuleParser::CLOSE_BRACE);
    setState(99);
    _errHandler->sync(this);

    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 8, _ctx)) {
    case 1: {
      setState(98);
      match(NftablesRuleParser::NEWLINE);
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

//----------------- ChainBodyContext ------------------------------------------------------------------

NftablesRuleParser::ChainBodyContext::ChainBodyContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::ChainAttrContext* NftablesRuleParser::ChainBodyContext::chainAttr() {
  return getRuleContext<NftablesRuleParser::ChainAttrContext>(0);
}

NftablesRuleParser::RuleLineContext* NftablesRuleParser::ChainBodyContext::ruleLine() {
  return getRuleContext<NftablesRuleParser::RuleLineContext>(0);
}

NftablesRuleParser::GenericLineContext* NftablesRuleParser::ChainBodyContext::genericLine() {
  return getRuleContext<NftablesRuleParser::GenericLineContext>(0);
}

NftablesRuleParser::BlankContext* NftablesRuleParser::ChainBodyContext::blank() {
  return getRuleContext<NftablesRuleParser::BlankContext>(0);
}


size_t NftablesRuleParser::ChainBodyContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainBody;
}


std::any NftablesRuleParser::ChainBodyContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainBody(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainBodyContext* NftablesRuleParser::chainBody() {
  ChainBodyContext *_localctx = _tracker.createInstance<ChainBodyContext>(_ctx, getState());
  enterRule(_localctx, 18, NftablesRuleParser::RuleChainBody);

#if __cplusplus > 201703L
  auto onExit = finally([=, this] {
#else
  auto onExit = finally([=] {
#endif
    exitRule();
  });
  try {
    setState(105);
    _errHandler->sync(this);
    switch (getInterpreter<atn::ParserATNSimulator>()->adaptivePredict(_input, 9, _ctx)) {
    case 1: {
      enterOuterAlt(_localctx, 1);
      setState(101);
      chainAttr();
      break;
    }

    case 2: {
      enterOuterAlt(_localctx, 2);
      setState(102);
      ruleLine();
      break;
    }

    case 3: {
      enterOuterAlt(_localctx, 3);
      setState(103);
      genericLine();
      break;
    }

    case 4: {
      enterOuterAlt(_localctx, 4);
      setState(104);
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

//----------------- ChainNameContext ------------------------------------------------------------------

NftablesRuleParser::ChainNameContext::ChainNameContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

NftablesRuleParser::NameTokenContext* NftablesRuleParser::ChainNameContext::nameToken() {
  return getRuleContext<NftablesRuleParser::NameTokenContext>(0);
}


size_t NftablesRuleParser::ChainNameContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainName;
}


std::any NftablesRuleParser::ChainNameContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainName(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainNameContext* NftablesRuleParser::chainName() {
  ChainNameContext *_localctx = _tracker.createInstance<ChainNameContext>(_ctx, getState());
  enterRule(_localctx, 20, NftablesRuleParser::RuleChainName);

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
    nameToken();
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ChainAttrContext ------------------------------------------------------------------

NftablesRuleParser::ChainAttrContext::ChainAttrContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::ChainAttrContext::ATTRKEY() {
  return getToken(NftablesRuleParser::ATTRKEY, 0);
}

tree::TerminalNode* NftablesRuleParser::ChainAttrContext::NEWLINE() {
  return getToken(NftablesRuleParser::NEWLINE, 0);
}

std::vector<NftablesRuleParser::ElemContext *> NftablesRuleParser::ChainAttrContext::elem() {
  return getRuleContexts<NftablesRuleParser::ElemContext>();
}

NftablesRuleParser::ElemContext* NftablesRuleParser::ChainAttrContext::elem(size_t i) {
  return getRuleContext<NftablesRuleParser::ElemContext>(i);
}


size_t NftablesRuleParser::ChainAttrContext::getRuleIndex() const {
  return NftablesRuleParser::RuleChainAttr;
}


std::any NftablesRuleParser::ChainAttrContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitChainAttr(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ChainAttrContext* NftablesRuleParser::chainAttr() {
  ChainAttrContext *_localctx = _tracker.createInstance<ChainAttrContext>(_ctx, getState());
  enterRule(_localctx, 22, NftablesRuleParser::RuleChainAttr);
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
    match(NftablesRuleParser::ATTRKEY);
    setState(113);
    _errHandler->sync(this);
    _la = _input->LA(1);
    while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 523502) != 0)) {
      setState(110);
      elem();
      setState(115);
      _errHandler->sync(this);
      _la = _input->LA(1);
    }
    setState(116);
    match(NftablesRuleParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- NameTokenContext ------------------------------------------------------------------

NftablesRuleParser::NameTokenContext::NameTokenContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::DASHED_IDENT() {
  return getToken(NftablesRuleParser::DASHED_IDENT, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::IDENT() {
  return getToken(NftablesRuleParser::IDENT, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::MATCHKEY() {
  return getToken(NftablesRuleParser::MATCHKEY, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::ACTION() {
  return getToken(NftablesRuleParser::ACTION, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::ATTRKEY() {
  return getToken(NftablesRuleParser::ATTRKEY, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::TABLE() {
  return getToken(NftablesRuleParser::TABLE, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::CHAIN() {
  return getToken(NftablesRuleParser::CHAIN, 0);
}

tree::TerminalNode* NftablesRuleParser::NameTokenContext::NUMBER() {
  return getToken(NftablesRuleParser::NUMBER, 0);
}


size_t NftablesRuleParser::NameTokenContext::getRuleIndex() const {
  return NftablesRuleParser::RuleNameToken;
}


std::any NftablesRuleParser::NameTokenContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitNameToken(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::NameTokenContext* NftablesRuleParser::nameToken() {
  NameTokenContext *_localctx = _tracker.createInstance<NameTokenContext>(_ctx, getState());
  enterRule(_localctx, 24, NftablesRuleParser::RuleNameToken);
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
    setState(118);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 180430) != 0))) {
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

//----------------- RuleLineContext ------------------------------------------------------------------

NftablesRuleParser::RuleLineContext::RuleLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::RuleLineContext::NEWLINE() {
  return getToken(NftablesRuleParser::NEWLINE, 0);
}

std::vector<NftablesRuleParser::RulePieceContext *> NftablesRuleParser::RuleLineContext::rulePiece() {
  return getRuleContexts<NftablesRuleParser::RulePieceContext>();
}

NftablesRuleParser::RulePieceContext* NftablesRuleParser::RuleLineContext::rulePiece(size_t i) {
  return getRuleContext<NftablesRuleParser::RulePieceContext>(i);
}


size_t NftablesRuleParser::RuleLineContext::getRuleIndex() const {
  return NftablesRuleParser::RuleRuleLine;
}


std::any NftablesRuleParser::RuleLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitRuleLine(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::RuleLineContext* NftablesRuleParser::ruleLine() {
  RuleLineContext *_localctx = _tracker.createInstance<RuleLineContext>(_ctx, getState());
  enterRule(_localctx, 26, NftablesRuleParser::RuleRuleLine);
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
      rulePiece();
      setState(123); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 516108) != 0));
    setState(125);
    match(NftablesRuleParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- RulePieceContext ------------------------------------------------------------------

NftablesRuleParser::RulePieceContext::RulePieceContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::MATCHKEY() {
  return getToken(NftablesRuleParser::MATCHKEY, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::ACTION() {
  return getToken(NftablesRuleParser::ACTION, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::QUOTED() {
  return getToken(NftablesRuleParser::QUOTED, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::ADDR() {
  return getToken(NftablesRuleParser::ADDR, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::NUMBER() {
  return getToken(NftablesRuleParser::NUMBER, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::DASHED_IDENT() {
  return getToken(NftablesRuleParser::DASHED_IDENT, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::IDENT() {
  return getToken(NftablesRuleParser::IDENT, 0);
}

tree::TerminalNode* NftablesRuleParser::RulePieceContext::RULEWORD() {
  return getToken(NftablesRuleParser::RULEWORD, 0);
}


size_t NftablesRuleParser::RulePieceContext::getRuleIndex() const {
  return NftablesRuleParser::RuleRulePiece;
}


std::any NftablesRuleParser::RulePieceContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitRulePiece(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::RulePieceContext* NftablesRuleParser::rulePiece() {
  RulePieceContext *_localctx = _tracker.createInstance<RulePieceContext>(_ctx, getState());
  enterRule(_localctx, 28, NftablesRuleParser::RuleRulePiece);
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
    setState(127);
    _la = _input->LA(1);
    if (!((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 516108) != 0))) {
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

NftablesRuleParser::GenericLineContext::GenericLineContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::GenericLineContext::NEWLINE() {
  return getToken(NftablesRuleParser::NEWLINE, 0);
}

std::vector<NftablesRuleParser::ElemContext *> NftablesRuleParser::GenericLineContext::elem() {
  return getRuleContexts<NftablesRuleParser::ElemContext>();
}

NftablesRuleParser::ElemContext* NftablesRuleParser::GenericLineContext::elem(size_t i) {
  return getRuleContext<NftablesRuleParser::ElemContext>(i);
}


size_t NftablesRuleParser::GenericLineContext::getRuleIndex() const {
  return NftablesRuleParser::RuleGenericLine;
}


std::any NftablesRuleParser::GenericLineContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitGenericLine(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::GenericLineContext* NftablesRuleParser::genericLine() {
  GenericLineContext *_localctx = _tracker.createInstance<GenericLineContext>(_ctx, getState());
  enterRule(_localctx, 30, NftablesRuleParser::RuleGenericLine);
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
    _errHandler->sync(this);
    _la = _input->LA(1);
    do {
      setState(129);
      elem();
      setState(132); 
      _errHandler->sync(this);
      _la = _input->LA(1);
    } while ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 523502) != 0));
    setState(134);
    match(NftablesRuleParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- BlankContext ------------------------------------------------------------------

NftablesRuleParser::BlankContext::BlankContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::BlankContext::NEWLINE() {
  return getToken(NftablesRuleParser::NEWLINE, 0);
}


size_t NftablesRuleParser::BlankContext::getRuleIndex() const {
  return NftablesRuleParser::RuleBlank;
}


std::any NftablesRuleParser::BlankContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitBlank(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::BlankContext* NftablesRuleParser::blank() {
  BlankContext *_localctx = _tracker.createInstance<BlankContext>(_ctx, getState());
  enterRule(_localctx, 32, NftablesRuleParser::RuleBlank);

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
    match(NftablesRuleParser::NEWLINE);
   
  }
  catch (RecognitionException &e) {
    _errHandler->reportError(this, e);
    _localctx->exception = std::current_exception();
    _errHandler->recover(this, _localctx->exception);
  }

  return _localctx;
}

//----------------- ElemContext ------------------------------------------------------------------

NftablesRuleParser::ElemContext::ElemContext(ParserRuleContext *parent, size_t invokingState)
  : ParserRuleContext(parent, invokingState) {
}

tree::TerminalNode* NftablesRuleParser::ElemContext::NEWLINE() {
  return getToken(NftablesRuleParser::NEWLINE, 0);
}

tree::TerminalNode* NftablesRuleParser::ElemContext::OPEN_BRACE() {
  return getToken(NftablesRuleParser::OPEN_BRACE, 0);
}

tree::TerminalNode* NftablesRuleParser::ElemContext::CLOSE_BRACE() {
  return getToken(NftablesRuleParser::CLOSE_BRACE, 0);
}


size_t NftablesRuleParser::ElemContext::getRuleIndex() const {
  return NftablesRuleParser::RuleElem;
}


std::any NftablesRuleParser::ElemContext::accept(tree::ParseTreeVisitor *visitor) {
  if (auto parserVisitor = dynamic_cast<NftablesRuleVisitor*>(visitor))
    return parserVisitor->visitElem(this);
  else
    return visitor->visitChildren(this);
}

NftablesRuleParser::ElemContext* NftablesRuleParser::elem() {
  ElemContext *_localctx = _tracker.createInstance<ElemContext>(_ctx, getState());
  enterRule(_localctx, 34, NftablesRuleParser::RuleElem);
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
    setState(138);
    _la = _input->LA(1);
    if (_la == 0 || _la == Token::EOF || ((((_la & ~ 0x3fULL) == 0) &&
      ((1ULL << _la) & 784) != 0))) {
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

void NftablesRuleParser::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  nftablesruleParserInitialize();
#else
  ::antlr4::internal::call_once(nftablesruleParserOnceFlag, nftablesruleParserInitialize);
#endif
}
