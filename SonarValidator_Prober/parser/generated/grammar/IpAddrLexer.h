
// Generated from grammar/IpAddr.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  IpAddrLexer : public antlr4::Lexer {
public:
  enum {
    NEWLINE = 1, WS = 2, INDEX = 3, MAC = 4, ADDR = 5, DEFAULT = 6, LINK = 7, 
    INET = 8, INET6 = 9, LIFETIME = 10, UP = 11, DOWN = 12, UNKNOWN = 13, 
    IFNAME = 14, COLON = 15, SLASH = 16, WORD = 17
  };

  explicit IpAddrLexer(antlr4::CharStream *input);

  ~IpAddrLexer() override;


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

