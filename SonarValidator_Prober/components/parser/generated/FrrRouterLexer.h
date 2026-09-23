
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/FrrRouter.g4 by ANTLR 4.13.1

#pragma once


#include "antlr4-runtime.h"




class  FrrRouterLexer : public antlr4::Lexer {
public:
  enum {
    NEWLINE = 1, WS = 2, ROUTECODE = 3, DEFAULT = 4, ADDR = 5, CODES = 6, 
    GATEWAY = 7, IS = 8, VARIABLY = 9, SUBMITTED = 10, INTERFACE = 11, UNASSIGNED = 12, 
    METHOD = 13, STATUSWORD = 14, IFNAME = 15, PORTNAME = 16, IDENT = 17, 
    ATTRWORD = 18, COLON = 19, SLASH = 20, WORD = 21
  };

  explicit FrrRouterLexer(antlr4::CharStream *input);

  ~FrrRouterLexer() override;


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

