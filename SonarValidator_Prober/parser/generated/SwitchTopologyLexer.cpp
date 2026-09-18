
// Generated from /home/osboxes/SonarValidator/SonarValidator_Prober/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.2


#include "SwitchTopologyLexer.h"


using namespace antlr4;



using namespace antlr4;

namespace {

struct SwitchTopologyLexerStaticData final {
  SwitchTopologyLexerStaticData(std::vector<std::string> ruleNames,
                          std::vector<std::string> channelNames,
                          std::vector<std::string> modeNames,
                          std::vector<std::string> literalNames,
                          std::vector<std::string> symbolicNames)
      : ruleNames(std::move(ruleNames)), channelNames(std::move(channelNames)),
        modeNames(std::move(modeNames)), literalNames(std::move(literalNames)),
        symbolicNames(std::move(symbolicNames)),
        vocabulary(this->literalNames, this->symbolicNames) {}

  SwitchTopologyLexerStaticData(const SwitchTopologyLexerStaticData&) = delete;
  SwitchTopologyLexerStaticData(SwitchTopologyLexerStaticData&&) = delete;
  SwitchTopologyLexerStaticData& operator=(const SwitchTopologyLexerStaticData&) = delete;
  SwitchTopologyLexerStaticData& operator=(SwitchTopologyLexerStaticData&&) = delete;

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

::antlr4::internal::OnceFlag switchtopologylexerLexerOnceFlag;
#if ANTLR4_USE_THREAD_LOCAL_CACHE
static thread_local
#endif
std::unique_ptr<SwitchTopologyLexerStaticData> switchtopologylexerLexerStaticData = nullptr;

void switchtopologylexerLexerInitialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  if (switchtopologylexerLexerStaticData != nullptr) {
    return;
  }
#else
  assert(switchtopologylexerLexerStaticData == nullptr);
#endif
  auto staticData = std::make_unique<SwitchTopologyLexerStaticData>(
    std::vector<std::string>{
      "NEWLINE", "WS", "VLAN", "INTERFACE", "ADDR", "NUMBER", "PORTNAME", 
      "UNASSIGNED", "DASHES", "STATUSWORD", "IFNAME", "ATTRWORD", "IDENT", 
      "COMMA", "COLON", "SLASH", "LPAREN", "RPAREN", "IPV4", "OCTET", "IPV6"
    },
    std::vector<std::string>{
      "DEFAULT_TOKEN_CHANNEL", "HIDDEN"
    },
    std::vector<std::string>{
      "DEFAULT_MODE"
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
  	4,0,18,291,6,-1,2,0,7,0,2,1,7,1,2,2,7,2,2,3,7,3,2,4,7,4,2,5,7,5,2,6,7,
  	6,2,7,7,7,2,8,7,8,2,9,7,9,2,10,7,10,2,11,7,11,2,12,7,12,2,13,7,13,2,14,
  	7,14,2,15,7,15,2,16,7,16,2,17,7,17,2,18,7,18,2,19,7,19,2,20,7,20,1,0,
  	3,0,45,8,0,1,0,1,0,1,1,4,1,50,8,1,11,1,12,1,51,1,1,1,1,1,2,1,2,1,2,1,
  	2,1,2,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,1,3,
  	1,3,1,3,3,3,79,8,3,1,4,1,4,1,4,4,4,84,8,4,11,4,12,4,85,3,4,88,8,4,1,4,
  	1,4,1,4,4,4,93,8,4,11,4,12,4,94,3,4,97,8,4,3,4,99,8,4,1,5,4,5,102,8,5,
  	11,5,12,5,103,1,6,4,6,107,8,6,11,6,12,6,108,1,6,4,6,112,8,6,11,6,12,6,
  	113,1,6,1,6,4,6,118,8,6,11,6,12,6,119,5,6,122,8,6,10,6,12,6,125,9,6,1,
  	7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,1,7,1,8,4,8,139,8,8,11,8,12,8,140,
  	1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,
  	9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,
  	1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,
  	9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,1,9,3,9,211,
  	8,9,1,10,1,10,5,10,215,8,10,10,10,12,10,218,9,10,1,11,1,11,5,11,222,8,
  	11,10,11,12,11,225,9,11,1,12,1,12,5,12,229,8,12,10,12,12,12,232,9,12,
  	1,13,1,13,1,14,1,14,1,15,1,15,1,16,1,16,1,17,1,17,1,18,1,18,1,18,1,18,
  	1,18,1,18,1,18,1,18,1,19,4,19,253,8,19,11,19,12,19,254,1,20,5,20,258,
  	8,20,10,20,12,20,261,9,20,1,20,1,20,1,20,5,20,266,8,20,10,20,12,20,269,
  	9,20,1,20,5,20,272,8,20,10,20,12,20,275,9,20,1,20,1,20,4,20,279,8,20,
  	11,20,12,20,280,1,20,1,20,5,20,285,8,20,10,20,12,20,288,9,20,3,20,290,
  	8,20,0,0,21,1,1,3,2,5,3,7,4,9,5,11,6,13,7,15,8,17,9,19,10,21,11,23,12,
  	25,13,27,14,29,15,31,16,33,17,35,18,37,0,39,0,41,0,1,0,8,2,0,9,9,32,32,
  	1,0,48,57,2,0,65,90,97,122,4,0,45,57,65,90,95,95,97,122,3,0,65,90,95,
  	95,97,122,5,0,45,46,48,57,65,90,95,95,97,122,3,0,48,57,65,70,97,102,3,
  	0,48,58,65,70,97,102,322,0,1,1,0,0,0,0,3,1,0,0,0,0,5,1,0,0,0,0,7,1,0,
  	0,0,0,9,1,0,0,0,0,11,1,0,0,0,0,13,1,0,0,0,0,15,1,0,0,0,0,17,1,0,0,0,0,
  	19,1,0,0,0,0,21,1,0,0,0,0,23,1,0,0,0,0,25,1,0,0,0,0,27,1,0,0,0,0,29,1,
  	0,0,0,0,31,1,0,0,0,0,33,1,0,0,0,0,35,1,0,0,0,1,44,1,0,0,0,3,49,1,0,0,
  	0,5,55,1,0,0,0,7,78,1,0,0,0,9,98,1,0,0,0,11,101,1,0,0,0,13,106,1,0,0,
  	0,15,126,1,0,0,0,17,138,1,0,0,0,19,210,1,0,0,0,21,212,1,0,0,0,23,219,
  	1,0,0,0,25,226,1,0,0,0,27,233,1,0,0,0,29,235,1,0,0,0,31,237,1,0,0,0,33,
  	239,1,0,0,0,35,241,1,0,0,0,37,243,1,0,0,0,39,252,1,0,0,0,41,289,1,0,0,
  	0,43,45,5,13,0,0,44,43,1,0,0,0,44,45,1,0,0,0,45,46,1,0,0,0,46,47,5,10,
  	0,0,47,2,1,0,0,0,48,50,7,0,0,0,49,48,1,0,0,0,50,51,1,0,0,0,51,49,1,0,
  	0,0,51,52,1,0,0,0,52,53,1,0,0,0,53,54,6,1,0,0,54,4,1,0,0,0,55,56,5,86,
  	0,0,56,57,5,76,0,0,57,58,5,65,0,0,58,59,5,78,0,0,59,6,1,0,0,0,60,61,5,
  	105,0,0,61,62,5,110,0,0,62,63,5,116,0,0,63,64,5,101,0,0,64,65,5,114,0,
  	0,65,66,5,102,0,0,66,67,5,97,0,0,67,68,5,99,0,0,68,79,5,101,0,0,69,70,
  	5,73,0,0,70,71,5,110,0,0,71,72,5,116,0,0,72,73,5,101,0,0,73,74,5,114,
  	0,0,74,75,5,102,0,0,75,76,5,97,0,0,76,77,5,99,0,0,77,79,5,101,0,0,78,
  	60,1,0,0,0,78,69,1,0,0,0,79,8,1,0,0,0,80,87,3,37,18,0,81,83,3,31,15,0,
  	82,84,7,1,0,0,83,82,1,0,0,0,84,85,1,0,0,0,85,83,1,0,0,0,85,86,1,0,0,0,
  	86,88,1,0,0,0,87,81,1,0,0,0,87,88,1,0,0,0,88,99,1,0,0,0,89,96,3,41,20,
  	0,90,92,3,31,15,0,91,93,7,1,0,0,92,91,1,0,0,0,93,94,1,0,0,0,94,92,1,0,
  	0,0,94,95,1,0,0,0,95,97,1,0,0,0,96,90,1,0,0,0,96,97,1,0,0,0,97,99,1,0,
  	0,0,98,80,1,0,0,0,98,89,1,0,0,0,99,10,1,0,0,0,100,102,7,1,0,0,101,100,
  	1,0,0,0,102,103,1,0,0,0,103,101,1,0,0,0,103,104,1,0,0,0,104,12,1,0,0,
  	0,105,107,7,2,0,0,106,105,1,0,0,0,107,108,1,0,0,0,108,106,1,0,0,0,108,
  	109,1,0,0,0,109,111,1,0,0,0,110,112,7,1,0,0,111,110,1,0,0,0,112,113,1,
  	0,0,0,113,111,1,0,0,0,113,114,1,0,0,0,114,123,1,0,0,0,115,117,5,47,0,
  	0,116,118,7,1,0,0,117,116,1,0,0,0,118,119,1,0,0,0,119,117,1,0,0,0,119,
  	120,1,0,0,0,120,122,1,0,0,0,121,115,1,0,0,0,122,125,1,0,0,0,123,121,1,
  	0,0,0,123,124,1,0,0,0,124,14,1,0,0,0,125,123,1,0,0,0,126,127,5,117,0,
  	0,127,128,5,110,0,0,128,129,5,97,0,0,129,130,5,115,0,0,130,131,5,115,
  	0,0,131,132,5,105,0,0,132,133,5,103,0,0,133,134,5,110,0,0,134,135,5,101,
  	0,0,135,136,5,100,0,0,136,16,1,0,0,0,137,139,5,45,0,0,138,137,1,0,0,0,
  	139,140,1,0,0,0,140,138,1,0,0,0,140,141,1,0,0,0,141,18,1,0,0,0,142,143,
  	5,117,0,0,143,211,5,112,0,0,144,145,5,100,0,0,145,146,5,111,0,0,146,147,
  	5,119,0,0,147,211,5,110,0,0,148,149,5,97,0,0,149,150,5,100,0,0,150,151,
  	5,109,0,0,151,152,5,105,0,0,152,153,5,110,0,0,153,154,5,105,0,0,154,155,
  	5,115,0,0,155,156,5,116,0,0,156,157,5,114,0,0,157,158,5,97,0,0,158,159,
  	5,116,0,0,159,160,5,105,0,0,160,161,5,118,0,0,161,162,5,101,0,0,162,163,
  	5,108,0,0,163,211,5,121,0,0,164,165,5,100,0,0,165,166,5,101,0,0,166,167,
  	5,108,0,0,167,168,5,101,0,0,168,169,5,116,0,0,169,170,5,101,0,0,170,211,
  	5,100,0,0,171,172,5,97,0,0,172,173,5,99,0,0,173,174,5,116,0,0,174,175,
  	5,105,0,0,175,176,5,118,0,0,176,211,5,101,0,0,177,178,5,97,0,0,178,179,
  	5,99,0,0,179,211,5,116,0,0,180,181,5,115,0,0,181,182,5,117,0,0,182,183,
  	5,115,0,0,183,184,5,112,0,0,184,185,5,101,0,0,185,186,5,110,0,0,186,187,
  	5,100,0,0,187,188,5,101,0,0,188,211,5,100,0,0,189,190,5,89,0,0,190,191,
  	5,69,0,0,191,211,5,83,0,0,192,193,5,78,0,0,193,211,5,79,0,0,194,195,5,
  	78,0,0,195,196,5,86,0,0,196,197,5,82,0,0,197,198,5,65,0,0,198,211,5,77,
  	0,0,199,200,5,117,0,0,200,201,5,110,0,0,201,202,5,115,0,0,202,203,5,101,
  	0,0,203,211,5,116,0,0,204,205,5,109,0,0,205,206,5,97,0,0,206,207,5,110,
  	0,0,207,208,5,117,0,0,208,209,5,97,0,0,209,211,5,108,0,0,210,142,1,0,
  	0,0,210,144,1,0,0,0,210,148,1,0,0,0,210,164,1,0,0,0,210,171,1,0,0,0,210,
  	177,1,0,0,0,210,180,1,0,0,0,210,189,1,0,0,0,210,192,1,0,0,0,210,194,1,
  	0,0,0,210,199,1,0,0,0,210,204,1,0,0,0,211,20,1,0,0,0,212,216,7,2,0,0,
  	213,215,7,3,0,0,214,213,1,0,0,0,215,218,1,0,0,0,216,214,1,0,0,0,216,217,
  	1,0,0,0,217,22,1,0,0,0,218,216,1,0,0,0,219,223,7,4,0,0,220,222,7,5,0,
  	0,221,220,1,0,0,0,222,225,1,0,0,0,223,221,1,0,0,0,223,224,1,0,0,0,224,
  	24,1,0,0,0,225,223,1,0,0,0,226,230,7,4,0,0,227,229,7,5,0,0,228,227,1,
  	0,0,0,229,232,1,0,0,0,230,228,1,0,0,0,230,231,1,0,0,0,231,26,1,0,0,0,
  	232,230,1,0,0,0,233,234,5,44,0,0,234,28,1,0,0,0,235,236,5,58,0,0,236,
  	30,1,0,0,0,237,238,5,47,0,0,238,32,1,0,0,0,239,240,5,40,0,0,240,34,1,
  	0,0,0,241,242,5,41,0,0,242,36,1,0,0,0,243,244,3,39,19,0,244,245,5,46,
  	0,0,245,246,3,39,19,0,246,247,5,46,0,0,247,248,3,39,19,0,248,249,5,46,
  	0,0,249,250,3,39,19,0,250,38,1,0,0,0,251,253,7,1,0,0,252,251,1,0,0,0,
  	253,254,1,0,0,0,254,252,1,0,0,0,254,255,1,0,0,0,255,40,1,0,0,0,256,258,
  	7,6,0,0,257,256,1,0,0,0,258,261,1,0,0,0,259,257,1,0,0,0,259,260,1,0,0,
  	0,260,262,1,0,0,0,261,259,1,0,0,0,262,263,3,29,14,0,263,267,3,29,14,0,
  	264,266,7,7,0,0,265,264,1,0,0,0,266,269,1,0,0,0,267,265,1,0,0,0,267,268,
  	1,0,0,0,268,290,1,0,0,0,269,267,1,0,0,0,270,272,7,6,0,0,271,270,1,0,0,
  	0,272,275,1,0,0,0,273,271,1,0,0,0,273,274,1,0,0,0,274,276,1,0,0,0,275,
  	273,1,0,0,0,276,278,3,29,14,0,277,279,7,6,0,0,278,277,1,0,0,0,279,280,
  	1,0,0,0,280,278,1,0,0,0,280,281,1,0,0,0,281,282,1,0,0,0,282,286,3,29,
  	14,0,283,285,7,7,0,0,284,283,1,0,0,0,285,288,1,0,0,0,286,284,1,0,0,0,
  	286,287,1,0,0,0,287,290,1,0,0,0,288,286,1,0,0,0,289,259,1,0,0,0,289,273,
  	1,0,0,0,290,42,1,0,0,0,26,0,44,51,78,85,87,94,96,98,103,108,113,119,123,
  	140,210,216,223,230,254,259,267,273,280,286,289,1,6,0,0
  };
  staticData->serializedATN = antlr4::atn::SerializedATNView(serializedATNSegment, sizeof(serializedATNSegment) / sizeof(serializedATNSegment[0]));

  antlr4::atn::ATNDeserializer deserializer;
  staticData->atn = deserializer.deserialize(staticData->serializedATN);

  const size_t count = staticData->atn->getNumberOfDecisions();
  staticData->decisionToDFA.reserve(count);
  for (size_t i = 0; i < count; i++) { 
    staticData->decisionToDFA.emplace_back(staticData->atn->getDecisionState(i), i);
  }
  switchtopologylexerLexerStaticData = std::move(staticData);
}

}

SwitchTopologyLexer::SwitchTopologyLexer(CharStream *input) : Lexer(input) {
  SwitchTopologyLexer::initialize();
  _interpreter = new atn::LexerATNSimulator(this, *switchtopologylexerLexerStaticData->atn, switchtopologylexerLexerStaticData->decisionToDFA, switchtopologylexerLexerStaticData->sharedContextCache);
}

SwitchTopologyLexer::~SwitchTopologyLexer() {
  delete _interpreter;
}

std::string SwitchTopologyLexer::getGrammarFileName() const {
  return "SwitchTopology.g4";
}

const std::vector<std::string>& SwitchTopologyLexer::getRuleNames() const {
  return switchtopologylexerLexerStaticData->ruleNames;
}

const std::vector<std::string>& SwitchTopologyLexer::getChannelNames() const {
  return switchtopologylexerLexerStaticData->channelNames;
}

const std::vector<std::string>& SwitchTopologyLexer::getModeNames() const {
  return switchtopologylexerLexerStaticData->modeNames;
}

const dfa::Vocabulary& SwitchTopologyLexer::getVocabulary() const {
  return switchtopologylexerLexerStaticData->vocabulary;
}

antlr4::atn::SerializedATNView SwitchTopologyLexer::getSerializedATN() const {
  return switchtopologylexerLexerStaticData->serializedATN;
}

const atn::ATN& SwitchTopologyLexer::getATN() const {
  return *switchtopologylexerLexerStaticData->atn;
}




void SwitchTopologyLexer::initialize() {
#if ANTLR4_USE_THREAD_LOCAL_CACHE
  switchtopologylexerLexerInitialize();
#else
  ::antlr4::internal::call_once(switchtopologylexerLexerOnceFlag, switchtopologylexerLexerInitialize);
#endif
}
