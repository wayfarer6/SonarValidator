
// Generated from grammar/SwitchTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  SwitchTopologyParser : public antlr4::Parser {
public:
  enum {
    NEWLINE = 1, WS = 2, VLAN = 3, INTERFACE = 4, ADDR = 5, NUMBER = 6, 
    PORTNAME = 7, UNASSIGNED = 8, DASHES = 9, STATUSWORD = 10, IFNAME = 11, 
    ATTRWORD = 12, IDENT = 13, COMMA = 14, COLON = 15, SLASH = 16
  };

  enum {
    RuleRunningDocument = 0, RuleVlanDocument = 1, RuleBriefDocument = 2, 
    RulePortDocument = 3, RuleConfigLine = 4, RuleVlanItem = 5, RuleVlanHeader = 6, 
    RuleVlanEntry = 7, RuleVlanSeparator = 8, RuleVlanId = 9, RuleVlanName = 10, 
    RuleVlanStatus = 11, RulePortList = 12, RulePortToken = 13, RuleNameToken = 14, 
    RuleBriefItem = 15, RuleBriefHeader = 16, RuleBriefEntry = 17, RuleAddrOrUnassigned = 18, 
    RuleBriefField = 19, RulePortItem = 20, RulePortEntry = 21, RuleIfname = 22, 
    RuleGenericLine = 23, RuleBlank = 24, RuleElem = 25
  };

  explicit SwitchTopologyParser(antlr4::TokenStream *input);

  SwitchTopologyParser(antlr4::TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options);

  ~SwitchTopologyParser() override;

  std::string getGrammarFileName() const override;

  const antlr4::atn::ATN& getATN() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;


  class RunningDocumentContext;
  class VlanDocumentContext;
  class BriefDocumentContext;
  class PortDocumentContext;
  class ConfigLineContext;
  class VlanItemContext;
  class VlanHeaderContext;
  class VlanEntryContext;
  class VlanSeparatorContext;
  class VlanIdContext;
  class VlanNameContext;
  class VlanStatusContext;
  class PortListContext;
  class PortTokenContext;
  class NameTokenContext;
  class BriefItemContext;
  class BriefHeaderContext;
  class BriefEntryContext;
  class AddrOrUnassignedContext;
  class BriefFieldContext;
  class PortItemContext;
  class PortEntryContext;
  class IfnameContext;
  class GenericLineContext;
  class BlankContext;
  class ElemContext; 

  class  RunningDocumentContext : public antlr4::ParserRuleContext {
  public:
    RunningDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ConfigLineContext *> configLine();
    ConfigLineContext* configLine(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RunningDocumentContext* runningDocument();

  class  VlanDocumentContext : public antlr4::ParserRuleContext {
  public:
    VlanDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<VlanItemContext *> vlanItem();
    VlanItemContext* vlanItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanDocumentContext* vlanDocument();

  class  BriefDocumentContext : public antlr4::ParserRuleContext {
  public:
    BriefDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<BriefItemContext *> briefItem();
    BriefItemContext* briefItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefDocumentContext* briefDocument();

  class  PortDocumentContext : public antlr4::ParserRuleContext {
  public:
    PortDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<PortItemContext *> portItem();
    PortItemContext* portItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortDocumentContext* portDocument();

  class  ConfigLineContext : public antlr4::ParserRuleContext {
  public:
    ConfigLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ConfigLineContext* configLine();

  class  VlanItemContext : public antlr4::ParserRuleContext {
  public:
    VlanItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    VlanHeaderContext *vlanHeader();
    VlanEntryContext *vlanEntry();
    VlanSeparatorContext *vlanSeparator();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanItemContext* vlanItem();

  class  VlanHeaderContext : public antlr4::ParserRuleContext {
  public:
    VlanHeaderContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *VLAN();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanHeaderContext* vlanHeader();

  class  VlanEntryContext : public antlr4::ParserRuleContext {
  public:
    VlanEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    VlanIdContext *vlanId();
    VlanNameContext *vlanName();
    VlanStatusContext *vlanStatus();
    antlr4::tree::TerminalNode *NEWLINE();
    PortListContext *portList();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanEntryContext* vlanEntry();

  class  VlanSeparatorContext : public antlr4::ParserRuleContext {
  public:
    VlanSeparatorContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *DASHES();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanSeparatorContext* vlanSeparator();

  class  VlanIdContext : public antlr4::ParserRuleContext {
  public:
    VlanIdContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *NUMBER();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanIdContext* vlanId();

  class  VlanNameContext : public antlr4::ParserRuleContext {
  public:
    VlanNameContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    NameTokenContext *nameToken();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanNameContext* vlanName();

  class  VlanStatusContext : public antlr4::ParserRuleContext {
  public:
    VlanStatusContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *STATUSWORD();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  VlanStatusContext* vlanStatus();

  class  PortListContext : public antlr4::ParserRuleContext {
  public:
    PortListContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    std::vector<PortTokenContext *> portToken();
    PortTokenContext* portToken(size_t i);
    std::vector<antlr4::tree::TerminalNode *> COMMA();
    antlr4::tree::TerminalNode* COMMA(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortListContext* portList();

  class  PortTokenContext : public antlr4::ParserRuleContext {
  public:
    PortTokenContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *PORTNAME();
    antlr4::tree::TerminalNode *IFNAME();
    antlr4::tree::TerminalNode *IDENT();
    antlr4::tree::TerminalNode *NUMBER();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortTokenContext* portToken();

  class  NameTokenContext : public antlr4::ParserRuleContext {
  public:
    NameTokenContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *IDENT();
    antlr4::tree::TerminalNode *IFNAME();
    antlr4::tree::TerminalNode *PORTNAME();
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *NUMBER();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  NameTokenContext* nameToken();

  class  BriefItemContext : public antlr4::ParserRuleContext {
  public:
    BriefItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    BriefHeaderContext *briefHeader();
    BriefEntryContext *briefEntry();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefItemContext* briefItem();

  class  BriefHeaderContext : public antlr4::ParserRuleContext {
  public:
    BriefHeaderContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *INTERFACE();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefHeaderContext* briefHeader();

  class  BriefEntryContext : public antlr4::ParserRuleContext {
  public:
    BriefEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    IfnameContext *ifname();
    antlr4::tree::TerminalNode *NEWLINE();
    AddrOrUnassignedContext *addrOrUnassigned();
    std::vector<BriefFieldContext *> briefField();
    BriefFieldContext* briefField(size_t i);
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefEntryContext* briefEntry();

  class  AddrOrUnassignedContext : public antlr4::ParserRuleContext {
  public:
    AddrOrUnassignedContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ADDR();
    antlr4::tree::TerminalNode *UNASSIGNED();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  AddrOrUnassignedContext* addrOrUnassigned();

  class  BriefFieldContext : public antlr4::ParserRuleContext {
  public:
    BriefFieldContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *STATUSWORD();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefFieldContext* briefField();

  class  PortItemContext : public antlr4::ParserRuleContext {
  public:
    PortItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    PortEntryContext *portEntry();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortItemContext* portItem();

  class  PortEntryContext : public antlr4::ParserRuleContext {
  public:
    PortEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *INTERFACE();
    IfnameContext *ifname();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *COLON();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  PortEntryContext* portEntry();

  class  IfnameContext : public antlr4::ParserRuleContext {
  public:
    IfnameContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *IFNAME();
    antlr4::tree::TerminalNode *PORTNAME();
    antlr4::tree::TerminalNode *IDENT();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfnameContext* ifname();

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

