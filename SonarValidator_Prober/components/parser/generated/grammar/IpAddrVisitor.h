
// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/IpAddr.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "IpAddrParser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by IpAddrParser.
 */
class  IpAddrVisitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by IpAddrParser.
   */
    virtual std::any visitDocument(IpAddrParser::DocumentContext *context) = 0;

    virtual std::any visitBriefDocument(IpAddrParser::BriefDocumentContext *context) = 0;

    virtual std::any visitRouteDocument(IpAddrParser::RouteDocumentContext *context) = 0;

    virtual std::any visitArpDocument(IpAddrParser::ArpDocumentContext *context) = 0;

    virtual std::any visitArpEntry(IpAddrParser::ArpEntryContext *context) = 0;

    virtual std::any visitArpAddress(IpAddrParser::ArpAddressContext *context) = 0;

    virtual std::any visitItem(IpAddrParser::ItemContext *context) = 0;

    virtual std::any visitIfaceHeader(IpAddrParser::IfaceHeaderContext *context) = 0;

    virtual std::any visitIfaceAttr(IpAddrParser::IfaceAttrContext *context) = 0;

    virtual std::any visitAttrLead(IpAddrParser::AttrLeadContext *context) = 0;

    virtual std::any visitBriefEntry(IpAddrParser::BriefEntryContext *context) = 0;

    virtual std::any visitLinkState(IpAddrParser::LinkStateContext *context) = 0;

    virtual std::any visitBriefAddr(IpAddrParser::BriefAddrContext *context) = 0;

    virtual std::any visitRouteEntry(IpAddrParser::RouteEntryContext *context) = 0;

    virtual std::any visitRouteHead(IpAddrParser::RouteHeadContext *context) = 0;

    virtual std::any visitIfname(IpAddrParser::IfnameContext *context) = 0;

    virtual std::any visitGenericLine(IpAddrParser::GenericLineContext *context) = 0;

    virtual std::any visitBlank(IpAddrParser::BlankContext *context) = 0;

    virtual std::any visitElem(IpAddrParser::ElemContext *context) = 0;


};

