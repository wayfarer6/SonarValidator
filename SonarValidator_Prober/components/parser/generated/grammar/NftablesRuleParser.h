
// Generated from grammar/NftablesRule.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  NftablesRuleParser : public antlr4::Parser {
public:
  enum {
    ATTRKEY = 1, MATCHKEY = 2, ACTION = 3, NEWLINE = 4, WS = 5, TABLE = 6, 
    CHAIN = 7, OPEN_BRACE = 8, CLOSE_BRACE = 9, SEMICOLON = 10, SLASH = 11, 
    COLON = 12, ADDR = 13, NUMBER = 14, DASHED_IDENT = 15, QUOTED = 16, 
    IDENT = 17, RULEWORD = 18
  };

  enum {
    RuleRulesetDocument = 0, RuleChainDocument = 1, RuleRulesetItem = 2, 
    RuleChainItem = 3, RuleTableBlock = 4, RuleBodyBlock = 5, RuleFamilyBlock = 6, 
    RuleTableName = 7, RuleChainBlock = 8, RuleChainBody = 9, RuleChainName = 10, 
    RuleChainAttr = 11, RuleNameToken = 12, RuleRuleLine = 13, RuleRulePiece = 14, 
    RuleGenericLine = 15, RuleBlank = 16, RuleElem = 17
  };

  explicit NftablesRuleParser(antlr4::TokenStream *input);

  NftablesRuleParser(antlr4::TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options);

  ~NftablesRuleParser() override;

  std::string getGrammarFileName() const override;

  const antlr4::atn::ATN& getATN() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;


  class RulesetDocumentContext;
  class ChainDocumentContext;
  class RulesetItemContext;
  class ChainItemContext;
  class TableBlockContext;
  class BodyBlockContext;
  class FamilyBlockContext;
  class TableNameContext;
  class ChainBlockContext;
  class ChainBodyContext;
  class ChainNameContext;
  class ChainAttrContext;
  class NameTokenContext;
  class RuleLineContext;
  class RulePieceContext;
  class GenericLineContext;
  class BlankContext;
  class ElemContext; 

  class  RulesetDocumentContext : public antlr4::ParserRuleContext {
  public:
    RulesetDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<RulesetItemContext *> rulesetItem();
    RulesetItemContext* rulesetItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RulesetDocumentContext* rulesetDocument();

  class  ChainDocumentContext : public antlr4::ParserRuleContext {
  public:
    ChainDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ChainItemContext *> chainItem();
    ChainItemContext* chainItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainDocumentContext* chainDocument();

  class  RulesetItemContext : public antlr4::ParserRuleContext {
  public:
    RulesetItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    TableBlockContext *tableBlock();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RulesetItemContext* rulesetItem();

  class  ChainItemContext : public antlr4::ParserRuleContext {
  public:
    ChainItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    ChainAttrContext *chainAttr();
    RuleLineContext *ruleLine();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainItemContext* chainItem();

  class  TableBlockContext : public antlr4::ParserRuleContext {
  public:
    TableBlockContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *TABLE();
    FamilyBlockContext *familyBlock();
    TableNameContext *tableName();
    antlr4::tree::TerminalNode *OPEN_BRACE();
    std::vector<antlr4::tree::TerminalNode *> NEWLINE();
    antlr4::tree::TerminalNode* NEWLINE(size_t i);
    antlr4::tree::TerminalNode *CLOSE_BRACE();
    std::vector<BodyBlockContext *> bodyBlock();
    BodyBlockContext* bodyBlock(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  TableBlockContext* tableBlock();

  class  BodyBlockContext : public antlr4::ParserRuleContext {
  public:
    BodyBlockContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    ChainBlockContext *chainBlock();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BodyBlockContext* bodyBlock();

  class  FamilyBlockContext : public antlr4::ParserRuleContext {
  public:
    FamilyBlockContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    NameTokenContext *nameToken();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  FamilyBlockContext* familyBlock();

  class  TableNameContext : public antlr4::ParserRuleContext {
  public:
    TableNameContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    NameTokenContext *nameToken();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  TableNameContext* tableName();

  class  ChainBlockContext : public antlr4::ParserRuleContext {
  public:
    ChainBlockContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *CHAIN();
    ChainNameContext *chainName();
    antlr4::tree::TerminalNode *OPEN_BRACE();
    std::vector<antlr4::tree::TerminalNode *> NEWLINE();
    antlr4::tree::TerminalNode* NEWLINE(size_t i);
    antlr4::tree::TerminalNode *CLOSE_BRACE();
    std::vector<ChainBodyContext *> chainBody();
    ChainBodyContext* chainBody(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainBlockContext* chainBlock();

  class  ChainBodyContext : public antlr4::ParserRuleContext {
  public:
    ChainBodyContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    ChainAttrContext *chainAttr();
    RuleLineContext *ruleLine();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainBodyContext* chainBody();

  class  ChainNameContext : public antlr4::ParserRuleContext {
  public:
    ChainNameContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    NameTokenContext *nameToken();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainNameContext* chainName();

  class  ChainAttrContext : public antlr4::ParserRuleContext {
  public:
    ChainAttrContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ATTRKEY();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ChainAttrContext* chainAttr();

  class  NameTokenContext : public antlr4::ParserRuleContext {
  public:
    NameTokenContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *DASHED_IDENT();
    antlr4::tree::TerminalNode *IDENT();
    antlr4::tree::TerminalNode *MATCHKEY();
    antlr4::tree::TerminalNode *ACTION();
    antlr4::tree::TerminalNode *ATTRKEY();
    antlr4::tree::TerminalNode *TABLE();
    antlr4::tree::TerminalNode *CHAIN();
    antlr4::tree::TerminalNode *NUMBER();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  NameTokenContext* nameToken();

  class  RuleLineContext : public antlr4::ParserRuleContext {
  public:
    RuleLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<RulePieceContext *> rulePiece();
    RulePieceContext* rulePiece(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RuleLineContext* ruleLine();

  class  RulePieceContext : public antlr4::ParserRuleContext {
  public:
    RulePieceContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *MATCHKEY();
    antlr4::tree::TerminalNode *ACTION();
    antlr4::tree::TerminalNode *QUOTED();
    antlr4::tree::TerminalNode *ADDR();
    antlr4::tree::TerminalNode *NUMBER();
    antlr4::tree::TerminalNode *DASHED_IDENT();
    antlr4::tree::TerminalNode *IDENT();
    antlr4::tree::TerminalNode *RULEWORD();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RulePieceContext* rulePiece();

  class  GenericLineContext : public antlr4::ParserRuleContext {
  public:
    GenericLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  GenericLineContext* genericLine();

  class  BlankContext : public antlr4::ParserRuleContext {
  public:
    BlankContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BlankContext* blank();

  class  ElemContext : public antlr4::ParserRuleContext {
  public:
    ElemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();
    antlr4::tree::TerminalNode *OPEN_BRACE();
    antlr4::tree::TerminalNode *CLOSE_BRACE();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ElemContext* elem();


  // By default the static state used to implement the parser is lazily initialized during the first
  // call to the constructor. You can call this function if you wish to initialize the static state
  // ahead of time.
  static void initialize();

private:
};

