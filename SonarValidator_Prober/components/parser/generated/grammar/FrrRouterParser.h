
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/FrrRouter.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  FrrRouterParser : public antlr4::Parser {
public:
  enum {
    NEWLINE = 1, WS = 2, ROUTECODE = 3, DEFAULT = 4, ADDR = 5, CODES = 6, 
    GATEWAY = 7, IS = 8, VARIABLY = 9, SUBMITTED = 10, INTERFACE = 11, UNASSIGNED = 12, 
    METHOD = 13, STATUSWORD = 14, IFNAME = 15, PORTNAME = 16, IDENT = 17, 
    ATTRWORD = 18, COLON = 19, SLASH = 20, WORD = 21
  };

  enum {
    RuleRouteDocument = 0, RuleIfaceDocument = 1, RuleDetailDocument = 2, 
    RuleRouteItem = 3, RuleRouteLine = 4, RuleRouteCode = 5, RuleDestination = 6, 
    RuleSubnetSummary = 7, RuleIfaceItem = 8, RuleBriefHeader = 9, RuleBriefEntry = 10, 
    RuleAddrOrUnassigned = 11, RuleBriefField = 12, RuleDetailItem = 13, 
    RuleIfaceHeader = 14, RuleDetailAttr = 15, RuleIfname = 16, RuleGenericLine = 17, 
    RuleBlank = 18, RuleElem = 19
  };

  explicit FrrRouterParser(antlr4::TokenStream *input);

  FrrRouterParser(antlr4::TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options);

  ~FrrRouterParser() override;

  std::string getGrammarFileName() const override;

  const antlr4::atn::ATN& getATN() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;


  class RouteDocumentContext;
  class IfaceDocumentContext;
  class DetailDocumentContext;
  class RouteItemContext;
  class RouteLineContext;
  class RouteCodeContext;
  class DestinationContext;
  class SubnetSummaryContext;
  class IfaceItemContext;
  class BriefHeaderContext;
  class BriefEntryContext;
  class AddrOrUnassignedContext;
  class BriefFieldContext;
  class DetailItemContext;
  class IfaceHeaderContext;
  class DetailAttrContext;
  class IfnameContext;
  class GenericLineContext;
  class BlankContext;
  class ElemContext; 

  class  RouteDocumentContext : public antlr4::ParserRuleContext {
  public:
    RouteDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<RouteItemContext *> routeItem();
    RouteItemContext* routeItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteDocumentContext* routeDocument();

  class  IfaceDocumentContext : public antlr4::ParserRuleContext {
  public:
    IfaceDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<IfaceItemContext *> ifaceItem();
    IfaceItemContext* ifaceItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceDocumentContext* ifaceDocument();

  class  DetailDocumentContext : public antlr4::ParserRuleContext {
  public:
    DetailDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<DetailItemContext *> detailItem();
    DetailItemContext* detailItem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  DetailDocumentContext* detailDocument();

  class  RouteItemContext : public antlr4::ParserRuleContext {
  public:
    RouteItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    RouteLineContext *routeLine();
    SubnetSummaryContext *subnetSummary();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteItemContext* routeItem();

  class  RouteLineContext : public antlr4::ParserRuleContext {
  public:
    RouteLineContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    DestinationContext *destination();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<RouteCodeContext *> routeCode();
    RouteCodeContext* routeCode(size_t i);
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteLineContext* routeLine();

  class  RouteCodeContext : public antlr4::ParserRuleContext {
  public:
    RouteCodeContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ROUTECODE();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteCodeContext* routeCode();

  class  DestinationContext : public antlr4::ParserRuleContext {
  public:
    DestinationContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *DEFAULT();
    antlr4::tree::TerminalNode *ADDR();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  DestinationContext* destination();

  class  SubnetSummaryContext : public antlr4::ParserRuleContext {
  public:
    SubnetSummaryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ADDR();
    antlr4::tree::TerminalNode *IS();
    antlr4::tree::TerminalNode *VARIABLY();
    antlr4::tree::TerminalNode *SUBMITTED();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  SubnetSummaryContext* subnetSummary();

  class  IfaceItemContext : public antlr4::ParserRuleContext {
  public:
    IfaceItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    BriefHeaderContext *briefHeader();
    BriefEntryContext *briefEntry();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceItemContext* ifaceItem();

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
    antlr4::tree::TerminalNode *METHOD();
    antlr4::tree::TerminalNode *STATUSWORD();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefFieldContext* briefField();

  class  DetailItemContext : public antlr4::ParserRuleContext {
  public:
    DetailItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    IfaceHeaderContext *ifaceHeader();
    DetailAttrContext *detailAttr();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  DetailItemContext* detailItem();

  class  IfaceHeaderContext : public antlr4::ParserRuleContext {
  public:
    IfaceHeaderContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *INTERFACE();
    IfnameContext *ifname();
    antlr4::tree::TerminalNode *IS();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceHeaderContext* ifaceHeader();

  class  DetailAttrContext : public antlr4::ParserRuleContext {
  public:
    DetailAttrContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ATTRWORD();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  DetailAttrContext* detailAttr();

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

