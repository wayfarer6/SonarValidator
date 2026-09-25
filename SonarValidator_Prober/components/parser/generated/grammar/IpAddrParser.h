
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/IpAddr.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"




class  IpAddrParser : public antlr4::Parser {
public:
  enum {
    NEWLINE = 1, WS = 2, INDEX = 3, MAC = 4, ADDR = 5, DEFAULT = 6, LINK = 7, 
    INET = 8, INET6 = 9, LIFETIME = 10, UP = 11, DOWN = 12, UNKNOWN = 13, 
    ROUTETYPE = 14, IFNAME = 15, COLON = 16, SLASH = 17, WORD = 18
  };

  enum {
    RuleDocument = 0, RuleBriefDocument = 1, RuleRouteDocument = 2, RuleArpDocument = 3, 
    RuleArpEntry = 4, RuleArpAddress = 5, RuleItem = 6, RuleIfaceHeader = 7, 
    RuleIfaceAttr = 8, RuleAttrLead = 9, RuleBriefEntry = 10, RuleLinkState = 11, 
    RuleBriefAddr = 12, RuleRouteEntry = 13, RuleRouteHead = 14, RuleIfname = 15, 
    RuleGenericLine = 16, RuleBlank = 17, RuleElem = 18
  };

  explicit IpAddrParser(antlr4::TokenStream *input);

  IpAddrParser(antlr4::TokenStream *input, const antlr4::atn::ParserATNSimulatorOptions &options);

  ~IpAddrParser() override;

  std::string getGrammarFileName() const override;

  const antlr4::atn::ATN& getATN() const override;

  const std::vector<std::string>& getRuleNames() const override;

  const antlr4::dfa::Vocabulary& getVocabulary() const override;

  antlr4::atn::SerializedATNView getSerializedATN() const override;


  class DocumentContext;
  class BriefDocumentContext;
  class RouteDocumentContext;
  class ArpDocumentContext;
  class ArpEntryContext;
  class ArpAddressContext;
  class ItemContext;
  class IfaceHeaderContext;
  class IfaceAttrContext;
  class AttrLeadContext;
  class BriefEntryContext;
  class LinkStateContext;
  class BriefAddrContext;
  class RouteEntryContext;
  class RouteHeadContext;
  class IfnameContext;
  class GenericLineContext;
  class BlankContext;
  class ElemContext; 

  class  DocumentContext : public antlr4::ParserRuleContext {
  public:
    DocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ItemContext *> item();
    ItemContext* item(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  DocumentContext* document();

  class  BriefDocumentContext : public antlr4::ParserRuleContext {
  public:
    BriefDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ItemContext *> item();
    ItemContext* item(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefDocumentContext* briefDocument();

  class  RouteDocumentContext : public antlr4::ParserRuleContext {
  public:
    RouteDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ItemContext *> item();
    ItemContext* item(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteDocumentContext* routeDocument();

  class  ArpDocumentContext : public antlr4::ParserRuleContext {
  public:
    ArpDocumentContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *EOF();
    std::vector<ArpEntryContext *> arpEntry();
    ArpEntryContext* arpEntry(size_t i);
    std::vector<GenericLineContext *> genericLine();
    GenericLineContext* genericLine(size_t i);
    std::vector<BlankContext *> blank();
    BlankContext* blank(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ArpDocumentContext* arpDocument();

  class  ArpEntryContext : public antlr4::ParserRuleContext {
  public:
    ArpEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    ArpAddressContext *arpAddress();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ArpEntryContext* arpEntry();

  class  ArpAddressContext : public antlr4::ParserRuleContext {
  public:
    ArpAddressContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ADDR();
    antlr4::tree::TerminalNode *IFNAME();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ArpAddressContext* arpAddress();

  class  ItemContext : public antlr4::ParserRuleContext {
  public:
    ItemContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    IfaceHeaderContext *ifaceHeader();
    IfaceAttrContext *ifaceAttr();
    BriefEntryContext *briefEntry();
    RouteEntryContext *routeEntry();
    GenericLineContext *genericLine();
    BlankContext *blank();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  ItemContext* item();

  class  IfaceHeaderContext : public antlr4::ParserRuleContext {
  public:
    IfaceHeaderContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *INDEX();
    std::vector<antlr4::tree::TerminalNode *> COLON();
    antlr4::tree::TerminalNode* COLON(size_t i);
    IfnameContext *ifname();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceHeaderContext* ifaceHeader();

  class  IfaceAttrContext : public antlr4::ParserRuleContext {
  public:
    IfaceAttrContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    AttrLeadContext *attrLead();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  IfaceAttrContext* ifaceAttr();

  class  AttrLeadContext : public antlr4::ParserRuleContext {
  public:
    AttrLeadContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *LINK();
    antlr4::tree::TerminalNode *INET();
    antlr4::tree::TerminalNode *INET6();
    antlr4::tree::TerminalNode *LIFETIME();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  AttrLeadContext* attrLead();

  class  BriefEntryContext : public antlr4::ParserRuleContext {
  public:
    BriefEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *IFNAME();
    LinkStateContext *linkState();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<BriefAddrContext *> briefAddr();
    BriefAddrContext* briefAddr(size_t i);
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefEntryContext* briefEntry();

  class  LinkStateContext : public antlr4::ParserRuleContext {
  public:
    LinkStateContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *UP();
    antlr4::tree::TerminalNode *DOWN();
    antlr4::tree::TerminalNode *UNKNOWN();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  LinkStateContext* linkState();

  class  BriefAddrContext : public antlr4::ParserRuleContext {
  public:
    BriefAddrContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *ADDR();
    antlr4::tree::TerminalNode *MAC();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  BriefAddrContext* briefAddr();

  class  RouteEntryContext : public antlr4::ParserRuleContext {
  public:
    RouteEntryContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    RouteHeadContext *routeHead();
    antlr4::tree::TerminalNode *NEWLINE();
    std::vector<ElemContext *> elem();
    ElemContext* elem(size_t i);


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteEntryContext* routeEntry();

  class  RouteHeadContext : public antlr4::ParserRuleContext {
  public:
    RouteHeadContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *DEFAULT();
    antlr4::tree::TerminalNode *ROUTETYPE();
    antlr4::tree::TerminalNode *ADDR();


    virtual std::any accept(antlr4::tree::ParseTreeVisitor *visitor) override;
   
  };

  RouteHeadContext* routeHead();

  class  IfnameContext : public antlr4::ParserRuleContext {
  public:
    IfnameContext(antlr4::ParserRuleContext *parent, size_t invokingState);
    virtual size_t getRuleIndex() const override;
    antlr4::tree::TerminalNode *IFNAME();


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

