
// Generated from grammar/IpAddr.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "IpAddrVisitor.h"


/**
 * This class provides an empty implementation of IpAddrVisitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  IpAddrBaseVisitor : public IpAddrVisitor {
public:

  virtual std::any visitDocument(IpAddrParser::DocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefDocument(IpAddrParser::BriefDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteDocument(IpAddrParser::RouteDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitArpDocument(IpAddrParser::ArpDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitArpEntry(IpAddrParser::ArpEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitArpAddress(IpAddrParser::ArpAddressContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitItem(IpAddrParser::ItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceHeader(IpAddrParser::IfaceHeaderContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceAttr(IpAddrParser::IfaceAttrContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAttrLead(IpAddrParser::AttrLeadContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefEntry(IpAddrParser::BriefEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitLinkState(IpAddrParser::LinkStateContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBriefAddr(IpAddrParser::BriefAddrContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteEntry(IpAddrParser::RouteEntryContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRouteHead(IpAddrParser::RouteHeadContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfname(IpAddrParser::IfnameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitGenericLine(IpAddrParser::GenericLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlank(IpAddrParser::BlankContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitElem(IpAddrParser::ElemContext *ctx) override {
    return visitChildren(ctx);
  }


};

