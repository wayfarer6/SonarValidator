
// Generated from grammar/NftablesRule.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "NftablesRuleParser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by NftablesRuleParser.
 */
class  NftablesRuleVisitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by NftablesRuleParser.
   */
    virtual std::any visitRulesetDocument(NftablesRuleParser::RulesetDocumentContext *context) = 0;

    virtual std::any visitChainDocument(NftablesRuleParser::ChainDocumentContext *context) = 0;

    virtual std::any visitRulesetItem(NftablesRuleParser::RulesetItemContext *context) = 0;

    virtual std::any visitChainItem(NftablesRuleParser::ChainItemContext *context) = 0;

    virtual std::any visitTableBlock(NftablesRuleParser::TableBlockContext *context) = 0;

    virtual std::any visitBodyBlock(NftablesRuleParser::BodyBlockContext *context) = 0;

    virtual std::any visitFamilyBlock(NftablesRuleParser::FamilyBlockContext *context) = 0;

    virtual std::any visitTableName(NftablesRuleParser::TableNameContext *context) = 0;

    virtual std::any visitChainBlock(NftablesRuleParser::ChainBlockContext *context) = 0;

    virtual std::any visitChainBody(NftablesRuleParser::ChainBodyContext *context) = 0;

    virtual std::any visitChainName(NftablesRuleParser::ChainNameContext *context) = 0;

    virtual std::any visitChainAttr(NftablesRuleParser::ChainAttrContext *context) = 0;

    virtual std::any visitNameToken(NftablesRuleParser::NameTokenContext *context) = 0;

    virtual std::any visitRuleLine(NftablesRuleParser::RuleLineContext *context) = 0;

    virtual std::any visitRulePiece(NftablesRuleParser::RulePieceContext *context) = 0;

    virtual std::any visitGenericLine(NftablesRuleParser::GenericLineContext *context) = 0;

    virtual std::any visitBlank(NftablesRuleParser::BlankContext *context) = 0;

    virtual std::any visitElem(NftablesRuleParser::ElemContext *context) = 0;


};

