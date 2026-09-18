
// Generated from grammar/NftablesRule.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  NftablesRuleLexer : public antlr4::Lexer {
public:
  enum {
    ATTRKEY = 1, MATCHKEY = 2, ACTION = 3, NEWLINE = 4, WS = 5, TABLE = 6, 
    CHAIN = 7, OPEN_BRACE = 8, CLOSE_BRACE = 9, SEMICOLON = 10, SLASH = 11, 
    COLON = 12, ADDR = 13, NUMBER = 14, DASHED_IDENT = 15, QUOTED = 16, 
    IDENT = 17, RULEWORD = 18
  };

  explicit NftablesRuleLexer(antlr4::CharStream *input);

  ~NftablesRuleLexer() override;


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

