
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/OvsTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  OvsTopologyParser : public antlr4::Parser {
public:
  enum {
    NEWLINE = 1, WS = 2, BRIDGE = 3, PORT = 4, INTERFACE = 5, FLOWTOKEN = 6, 
    ATTRWORD = 7, QUOTED = 8, DASHES = 9, WORD = 10, COLON = 11
  };

  enum {
    RuleShowDocument = 0, RuleListDocument = 1, RuleFlowDocument = 2, RuleShowVlanDocument = 3, 
    RuleShowItem = 4, RuleBridgeLine = 5, RulePortLine = 6, RuleIfaceLine = 7, 
    RuleAttrLine = 8, RuleNameToken = 9, RuleListItem = 10, RuleListRecord = 11, 
    RuleRecordSep = 12, RuleFlowItem = 13, RuleFlowLine = 14, RuleGenericLine = 15, 
    RuleBlank = 16, RuleElem = 17
  };

  explicit OvsTopologyParser(antlr4::TokenStream *input);

  OvsTopologyParser(antlr4::TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options);

  ~OvsTopologyParser() override;

  std::string getGrammarFileName() const override;

  const antlr4::atn::ATN& getATN() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;


  class ShowDocumentContext;
  class ListDocumentContext;
  class FlowDocumentContext;
  class ShowVlanDocumentContext;
  class ShowItemContext;
  class BridgeLineContext;
  class PortLineContext;
  class IfaceLineContext;
  class AttrLineContext;
  class NameTokenContext;
  class ListItemContext;
  class ListRecordContext;
  class RecordSepContext;
  class FlowItemContext;
  class FlowLineContext;
  class GenericLineContext;
  class BlankContext;
  class ElemContext; 

  class  ShowDocumentContext : public antlr4::ParserRuleContext {
  public:
    ShowDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ShowItemContext *> showItem();
    ShowItemContext* showItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ShowDocumentContext* showDocument();

  class  ListDocumentContext : public antlr4::ParserRuleContext {
  public:
    ListDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ListItemContext *> listItem();
    ListItemContext* listItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ListDocumentContext* listDocument();

  class  FlowDocumentContext : public antlr4::ParserRuleContext {
  public:
    FlowDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<FlowItemContext *> flowItem();
    FlowItemContext* flowItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  FlowDocumentContext* flowDocument();

  class  ShowVlanDocumentContext : public antlr4::ParserRuleContext {
  public:
    ShowVlanDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ShowItemContext *> showItem();
    ShowItemContext* showItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ShowVlanDocumentContext* showVlanDocument();

  class  ShowItemContext : public antlr4::ParserRuleContext {
  public:
    ShowItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    BridgeLineContext *bridgeLine();
    PortLineContext *portLine();
    IfaceLineContext *ifaceLine();
    AttrLineContext *attrLine();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ShowItemContext* showItem();

  class  BridgeLineContext : public antlr4::ParserRuleContext {
  public:
    BridgeLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *BRIDGE();
    NameTokenContext *nameToken();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BridgeLineContext* bridgeLine();

  class  PortLineContext : public antlr4::ParserRuleContext {
  public:
    PortLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *PORT();
    NameTokenContext *nameToken();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortLineContext* portLine();

  class  IfaceLineContext : public antlr4::ParserRuleContext {
  public:
    IfaceLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *INTERFACE();
    NameTokenContext *nameToken();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceLineContext* ifaceLine();

  class  AttrLineContext : public antlr4::ParserRuleContext {
  public:
    AttrLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *COLON();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  AttrLineContext* attrLine();

  class  NameTokenContext : public antlr4::ParserRuleContext {
  public:
    NameTokenContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *QUOTED();
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *WORD();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  NameTokenContext* nameToken();

  class  ListItemContext : public antlr4::ParserRuleContext {
  public:
    ListItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    ListRecordContext *listRecord();
    RecordSepContext *recordSep();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ListItemContext* listItem();

  class  ListRecordContext : public antlr4::ParserRuleContext {
  public:
    ListRecordContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *COLON();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ListRecordContext* listRecord();

  class  RecordSepContext : public antlr4::ParserRuleContext {
  public:
    RecordSepContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *DASHES();
    antlr4::tree::TerminalNode *NEWLINE();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RecordSepContext* recordSep();

  class  FlowItemContext : public antlr4::ParserRuleContext {
  public:
    FlowItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    FlowLineContext *flowLine();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  FlowItemContext* flowItem();

  class  FlowLineContext : public antlr4::ParserRuleContext {
  public:
    FlowLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<antlr4::tree::TerminalNode *> FLOWTOKEN();
    antlr4::tree::TerminalNode* FLOWTOKEN(size_t i);
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  FlowLineContext* flowLine();

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


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ElemContext* elem();


  // By default the static state used to implement the parser is lazily initialized during the first
  // call to the constructor. You can call this function if you wish to initialize the static state
  // ahead of time.
  static void initialize();

private:
};

