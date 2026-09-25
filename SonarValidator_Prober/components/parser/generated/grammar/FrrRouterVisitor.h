
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/FrrRouter.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "FrrRouterParser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by FrrRouterParser.
 */
class  FrrRouterVisitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by FrrRouterParser.
   */
    virtual std::any visitRouteDocument(FrrRouterParser::RouteDocumentContext *context) = 0;

    virtual std::any visitIfaceDocument(FrrRouterParser::IfaceDocumentContext *context) = 0;

    virtual std::any visitDetailDocument(FrrRouterParser::DetailDocumentContext *context) = 0;

    virtual std::any visitRouteItem(FrrRouterParser::RouteItemContext *context) = 0;

    virtual std::any visitRouteLine(FrrRouterParser::RouteLineContext *context) = 0;

    virtual std::any visitRouteCode(FrrRouterParser::RouteCodeContext *context) = 0;

    virtual std::any visitDestination(FrrRouterParser::DestinationContext *context) = 0;

    virtual std::any visitSubnetSummary(FrrRouterParser::SubnetSummaryContext *context) = 0;

    virtual std::any visitIfaceItem(FrrRouterParser::IfaceItemContext *context) = 0;

    virtual std::any visitBriefHeader(FrrRouterParser::BriefHeaderContext *context) = 0;

    virtual std::any visitBriefEntry(FrrRouterParser::BriefEntryContext *context) = 0;

    virtual std::any visitAddrOrUnassigned(FrrRouterParser::AddrOrUnassignedContext *context) = 0;

    virtual std::any visitBriefField(FrrRouterParser::BriefFieldContext *context) = 0;

    virtual std::any visitDetailItem(FrrRouterParser::DetailItemContext *context) = 0;

    virtual std::any visitIfaceHeader(FrrRouterParser::IfaceHeaderContext *context) = 0;

    virtual std::any visitDetailAttr(FrrRouterParser::DetailAttrContext *context) = 0;

    virtual std::any visitIfname(FrrRouterParser::IfnameContext *context) = 0;

    virtual std::any visitGenericLine(FrrRouterParser::GenericLineContext *context) = 0;

    virtual std::any visitBlank(FrrRouterParser::BlankContext *context) = 0;

    virtual std::any visitElem(FrrRouterParser::ElemContext *context) = 0;


};

