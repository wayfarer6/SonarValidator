
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "SwitchTopologyVisitor.h"


/**
 * This class provides an empty implementation of SwitchTopologyVisitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  SwitchTopologyBaseVisitor : public SwitchTopologyVisitor {
public:

  virtual std::any visitRunningDocument(SwitchTopologyParser::RunningDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanDocument(SwitchTopologyParser::VlanDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefDocument(SwitchTopologyParser::BriefDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortDocument(SwitchTopologyParser::PortDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitConfigLine(SwitchTopologyParser::ConfigLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanItem(SwitchTopologyParser::VlanItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanHeader(SwitchTopologyParser::VlanHeaderContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanEntry(SwitchTopologyParser::VlanEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanSeparator(SwitchTopologyParser::VlanSeparatorContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanId(SwitchTopologyParser::VlanIdContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanName(SwitchTopologyParser::VlanNameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitVlanStatus(SwitchTopologyParser::VlanStatusContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortList(SwitchTopologyParser::PortListContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortToken(SwitchTopologyParser::PortTokenContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitNameToken(SwitchTopologyParser::NameTokenContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefItem(SwitchTopologyParser::BriefItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefHeader(SwitchTopologyParser::BriefHeaderContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefEntry(SwitchTopologyParser::BriefEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAddrOrUnassigned(SwitchTopologyParser::AddrOrUnassignedContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefField(SwitchTopologyParser::BriefFieldContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortItem(SwitchTopologyParser::PortItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortEntry(SwitchTopologyParser::PortEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfname(SwitchTopologyParser::IfnameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitKeyWord(SwitchTopologyParser::KeyWordContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitGenericLine(SwitchTopologyParser::GenericLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlank(SwitchTopologyParser::BlankContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitElem(SwitchTopologyParser::ElemContext *ctx) override {
    return visitChildren(ctx);
  }


};

