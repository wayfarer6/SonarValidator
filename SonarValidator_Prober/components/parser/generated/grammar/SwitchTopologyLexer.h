
// Generated from grammar/SwitchTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  SwitchTopologyLexer : public antlr4::Lexer {
public:
  enum {
    NEWLINE = 1, WS = 2, VLAN = 3, INTERFACE = 4, ADDR = 5, NUMBER = 6, 
    PORTNAME = 7, UNASSIGNED = 8, DASHES = 9, STATUSWORD = 10, IFNAME = 11, 
    ATTRWORD = 12, IDENT = 13, COMMA = 14, COLON = 15, SLASH = 16, LPAREN = 17, 
    RPAREN = 18
  };

  explicit SwitchTopologyLexer(antlr4::CharStream *input);

  ~SwitchTopologyLexer() override;


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

