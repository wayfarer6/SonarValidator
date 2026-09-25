
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/OvsTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  OvsTopologyLexer : public antlr4::Lexer {
public:
  enum {
    NEWLINE = 1, WS = 2, BRIDGE = 3, PORT = 4, INTERFACE = 5, FLOWTOKEN = 6, 
    ATTRWORD = 7, QUOTED = 8, DASHES = 9, WORD = 10, COLON = 11
  };

  explicit OvsTopologyLexer(antlr4::CharStream *input);

  ~OvsTopologyLexer() override;


  std::string getGrammarFileName() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const std::vector<std::string>& getChannelNames() const override;

  const std::vector<std::string>& getModeNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;

  const antlr4::atn::ATN& getATN() const override;

  // By default the static state used to implement the lexer is lazily initialized during the first
  // call to the constructor. You can call this function if you wish to initialize the static state
  // ahead of time.
  static void initialize();

private:

  // Individual action functions triggered by action() above.

  // Individual semantic predicate functions triggered by sempred() above.

};

