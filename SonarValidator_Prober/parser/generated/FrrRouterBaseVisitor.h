
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/FrrRouter.g4 by ANTLR 4.13.1

#pragma once


#include "antlr4-runtime.h"
#include "FrrRouterVisitor.h"


/**
 * This class provides an empty implementation of FrrRouterVisitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  FrrRouterBaseVisitor : public FrrRouterVisitor {
public:

  virtual std::any visitRouteDocument(FrrRouterParser::RouteDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceDocument(FrrRouterParser::IfaceDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitDetailDocument(FrrRouterParser::DetailDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteItem(FrrRouterParser::RouteItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteLine(FrrRouterParser::RouteLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteCode(FrrRouterParser::RouteCodeContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitDestination(FrrRouterParser::DestinationContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitSubnetSummary(FrrRouterParser::SubnetSummaryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceItem(FrrRouterParser::IfaceItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefHeader(FrrRouterParser::BriefHeaderContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefEntry(FrrRouterParser::BriefEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAddrOrUnassigned(FrrRouterParser::AddrOrUnassignedContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefField(FrrRouterParser::BriefFieldContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitDetailItem(FrrRouterParser::DetailItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceHeader(FrrRouterParser::IfaceHeaderContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitDetailAttr(FrrRouterParser::DetailAttrContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfname(FrrRouterParser::IfnameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitGenericLine(FrrRouterParser::GenericLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlank(FrrRouterParser::BlankContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitElem(FrrRouterParser::ElemContext *ctx) override {
    return visitChildren(ctx);
  }


};

