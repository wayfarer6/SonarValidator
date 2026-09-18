
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/NftablesRule.g4 by ANTLR 4.13.1

#pragma once


#include "antlr4-runtime.h"
#include "NftablesRuleVisitor.h"


/**
 * This class provides an empty implementation of NftablesRuleVisitor, which can be
 * extended to create a visitor which only needs to handle a subset of the available methods.
 */
class  NftablesRuleBaseVisitor : public NftablesRuleVisitor {
public:

  virtual std::any visitRulesetDocument(NftablesRuleParser::RulesetDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainDocument(NftablesRuleParser::ChainDocumentContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRulesetItem(NftablesRuleParser::RulesetItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainItem(NftablesRuleParser::ChainItemContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitTableBlock(NftablesRuleParser::TableBlockContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBodyBlock(NftablesRuleParser::BodyBlockContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitFamilyBlock(NftablesRuleParser::FamilyBlockContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitTableName(NftablesRuleParser::TableNameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainBlock(NftablesRuleParser::ChainBlockContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainBody(NftablesRuleParser::ChainBodyContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainName(NftablesRuleParser::ChainNameContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitChainAttr(NftablesRuleParser::ChainAttrContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitNameToken(NftablesRuleParser::NameTokenContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRuleLine(NftablesRuleParser::RuleLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitRulePiece(NftablesRuleParser::RulePieceContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitGenericLine(NftablesRuleParser::GenericLineContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitBlank(NftablesRuleParser::BlankContext *ctx) override {
    return visitChildren(ctx);
  }

  virtual std::any visitElem(NftablesRuleParser::ElemContext *ctx) override {
    return visitChildren(ctx);
  }


};

