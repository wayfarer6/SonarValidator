
// Generated from grammar/OvsTopology.g4 by ANTLR 4.13.2


#include "OvsTopologyLexer.h"


using namespace antlr4;



using namespace antlr4;

namespace {

struct OvsTopologyLexerStaticData final {
  OvsTopologyLexerStaticData(std::vector<std::string> ruleNames,
                          std::vector<std::string> channelNames,
                          std::vector<std::string> modeNames,
                          std::vector<std::string> literalNames,
                          std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), channelNames(std::move(channelNames)),
        modeNames(std::move(modeNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  OvsTopologyLexerStaticData(const OvsTopologyLexerStaticData&) = delete;
  OvsTopologyLexerStaticData(OvsTopologyLexerStaticData&&) = delete;
  OvsTopologyLexerStaticData& operator=(const OvsTopologyLexerStaticData&) = delete;
  OvsTopologyLexerStaticData& operator=(OvsTopologyLexerStaticData&&) = delete;

  std::vector<antlr4::dfa::DFA> decisionToDFA;
  antlr4::atn::PredictionContextCache sharedContextCache;
  const std::vector<std::string> ruleNames;
  const std::vector<std::string> channelNames;
  const std::vector<std::string> modeNames;
  const std::vector<std::string> literalNames;
  const std::vector<std::string> symbolicNames;
  const antlr4::dfa::Vocabulary vocabulary;
  antlr4::atn::SerializedATNView serializedATN;
  std::unique_ptr<antlr4::atn::ATN> atn;
};

::antlr4::internal::OnceFlag ovstopologylexerLexerOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<OvsTopologyLexerStaticData> ovstopologylexerLexerStaticData = nullptr;

void ovstopologylexerLexerInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (ovstopologylexerLexerStaticData != nullptr) {
    return;
  }
#else
  assert(ovstopologylexerLexerStaticData == nullptr);
#endif
  auto staticData = std::make_unique<OvsTopologyLexerStaticData>(
    std::vector<std::string>{
      "NEWLINE", "WS", "BRIDGE", "PORT", "INTERFACE", "FLOWTOKEN", "ATTRWORD", 
      "QUOTED", "DASHES", "WORD", "COLON"
    },
    std::vector<std::string>{
      "DEFAULT_TOKEN_CHANNEL", "HIDDEN"
    },
    std::vector<std::string>{
      "DEFAULT_MODE"
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
  	4,0,11,96,6,-1,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,
  	6,2,7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,1,0,3,0,25,8,0,1,0,1,0,1,1,4,1,30,
  	8,1,11,1,12,1,31,1,1,1,1,1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,3,1,3,1,3,1,3,
  	1,3,1,4,1,4,1,4,1,4,1,4,1,4,1,4,1,4,1,4,1,4,1,5,4,5,59,8,5,11,5,12,5,
  	60,1,5,1,5,4,5,65,8,5,11,5,12,5,66,1,6,1,6,5,6,71,8,6,10,6,12,6,74,9,
  	6,1,7,1,7,5,7,78,8,7,10,7,12,7,81,9,7,1,7,1,7,1,8,4,8,86,8,8,11,8,12,
  	8,87,1,9,4,9,91,8,9,11,9,12,9,92,1,10,1,10,0,0,11,1,1,3,2,5,3,7,4,9,5,
  	11,6,13,7,15,8,17,9,19,10,21,11,1,0,6,2,0,9,9,32,32,3,0,65,90,95,95,97,
  	122,5,0,9,10,13,13,32,32,44,44,94,94,5,0,45,46,48,57,65,90,95,95,97,122,
  	3,0,10,10,13,13,34,34,4,0,9,10,13,13,32,32,58,58,103,0,1,1,0,0,0,0,3,
  	1,0,0,0,0,5,1,0,0,0,0,7,1,0,0,0,0,9,1,0,0,0,0,11,1,0,0,0,0,13,1,0,0,0,
  	0,15,1,0,0,0,0,17,1,0,0,0,0,19,1,0,0,0,0,21,1,0,0,0,1,24,1,0,0,0,3,29,
  	1,0,0,0,5,35,1,0,0,0,7,42,1,0,0,0,9,47,1,0,0,0,11,58,1,0,0,0,13,68,1,
  	0,0,0,15,75,1,0,0,0,17,85,1,0,0,0,19,90,1,0,0,0,21,94,1,0,0,0,23,25,5,
  	13,0,0,24,23,1,0,0,0,24,25,1,0,0,0,25,26,1,0,0,0,26,27,5,10,0,0,27,2,
  	1,0,0,0,28,30,7,0,0,0,29,28,1,0,0,0,30,31,1,0,0,0,31,29,1,0,0,0,31,32,
  	1,0,0,0,32,33,1,0,0,0,33,34,6,1,0,0,34,4,1,0,0,0,35,36,5,66,0,0,36,37,
  	5,114,0,0,37,38,5,105,0,0,38,39,5,100,0,0,39,40,5,103,0,0,40,41,5,101,
  	0,0,41,6,1,0,0,0,42,43,5,80,0,0,43,44,5,111,0,0,44,45,5,114,0,0,45,46,
  	5,116,0,0,46,8,1,0,0,0,47,48,5,73,0,0,48,49,5,110,0,0,49,50,5,116,0,0,
  	50,51,5,101,0,0,51,52,5,114,0,0,52,53,5,102,0,0,53,54,5,97,0,0,54,55,
  	5,99,0,0,55,56,5,101,0,0,56,10,1,0,0,0,57,59,7,1,0,0,58,57,1,0,0,0,59,
  	60,1,0,0,0,60,58,1,0,0,0,60,61,1,0,0,0,61,62,1,0,0,0,62,64,5,61,0,0,63,
  	65,7,2,0,0,64,63,1,0,0,0,65,66,1,0,0,0,66,64,1,0,0,0,66,67,1,0,0,0,67,
  	12,1,0,0,0,68,72,7,1,0,0,69,71,7,3,0,0,70,69,1,0,0,0,71,74,1,0,0,0,72,
  	70,1,0,0,0,72,73,1,0,0,0,73,14,1,0,0,0,74,72,1,0,0,0,75,79,5,34,0,0,76,
  	78,8,4,0,0,77,76,1,0,0,0,78,81,1,0,0,0,79,77,1,0,0,0,79,80,1,0,0,0,80,
  	82,1,0,0,0,81,79,1,0,0,0,82,83,5,34,0,0,83,16,1,0,0,0,84,86,5,45,0,0,
  	85,84,1,0,0,0,86,87,1,0,0,0,87,85,1,0,0,0,87,88,1,0,0,0,88,18,1,0,0,0,
  	89,91,8,5,0,0,90,89,1,0,0,0,91,92,1,0,0,0,92,90,1,0,0,0,92,93,1,0,0,0,
  	93,20,1,0,0,0,94,95,5,58,0,0,95,22,1,0,0,0,9,0,24,31,60,66,72,79,87,92,
  	1,6,0,0
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  ovstopologylexerLexerStaticData = std::move(staticData);
}

}

OvsTopologyLexer::OvsTopologyLexer(CharStream *input) : Lexer(input) {
  OvsTopologyLexer::initialize();
  _interpreter = new atn::LexerATNSimulator(this, *ovstopologylexerLexerStaticData->atn, ovstopologylexerLexerStaticData->decisionToDFA, ovstopologylexerLexerStaticData->sharedContextCache);
}

OvsTopologyLexer::~OvsTopologyLexer() {
  delete _interpreter;
}

std::string OvsTopologyLexer::getGrammarFileName() const {
  return "OvsTopology.g4";
}

const std::vector<std::string>& OvsTopologyLexer::getRuleNames() const {
  return ovstopologylexerLexerStaticData->ruleNames;
}

const std::vector<std::string>& OvsTopologyLexer::getChannelNames() const {
  return ovstopologylexerLexerStaticData->channelNames;
}

const std::vector<std::string>& OvsTopologyLexer::getModeNames() const {
  return ovstopologylexerLexerStaticData->modeNames;
}

const dfa::Vocabulary& OvsTopologyLexer::getVocabulary() const {
  return ovstopologylexerLexerStaticData->vocabulary;
}

antlr4::atn::SerializedATNView OvsTopologyLexer::getSerializedATN() const {
  return ovstopologylexerLexerStaticData->serializedATN;
}

const atn::ATN& OvsTopologyLexer::getATN() const {
  return *ovstopologylexerLexerStaticData->atn;
}




void OvsTopologyLexer::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  ovstopologylexerLexerInitialize();
#else
  ::antlr4::internal::call_once(ovstopologylexerLexerOnceFlag, ovstopologylexerLexerInitialize);
#endif
}
