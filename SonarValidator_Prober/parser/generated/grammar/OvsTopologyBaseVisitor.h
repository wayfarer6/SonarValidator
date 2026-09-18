
// Generated from grammar/OvsTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "OvsTopologyVisitor.h"


/**
 * This class provides an empty implementation of OvsTopologyVisitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  OvsTopologyBaseVisitor : public OvsTopologyVisitor {
public:

  virtual std::any visitShowDocument(OvsTopologyParser::ShowDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitListDocument(OvsTopologyParser::ListDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFlowDocument(OvsTopologyParser::FlowDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitShowVlanDocument(OvsTopologyParser::ShowVlanDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitShowItem(OvsTopologyParser::ShowItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBridgeLine(OvsTopologyParser::BridgeLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitPortLine(OvsTopologyParser::PortLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitIfaceLine(OvsTopologyParser::IfaceLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitAttrLine(OvsTopologyParser::AttrLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitNameToken(OvsTopologyParser::NameTokenContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitListItem(OvsTopologyParser::ListItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitListRecord(OvsTopologyParser::ListRecordContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRecordSep(OvsTopologyParser::RecordSepContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFlowItem(OvsTopologyParser::FlowItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFlowLine(OvsTopologyParser::FlowLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitGenericLine(OvsTopologyParser::GenericLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlank(OvsTopologyParser::BlankContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitElem(OvsTopologyParser::ElemContext *ctx) override {
    return visitChildren(ctx);
  }


};

