
// Generated from /home/osboxes/SonarValidator/SonarValidator_Prober/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "SwitchTopologyParser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by SwitchTopologyParser.
 */
class  SwitchTopologyVisitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by SwitchTopologyParser.
   */
    virtual std::any visitRunningDocument(SwitchTopologyParser::RunningDocumentContext *context) = 0;

    virtual std::any visitVlanDocument(SwitchTopologyParser::VlanDocumentContext *context) = 0;

    virtual std::any visitBriefDocument(SwitchTopologyParser::BriefDocumentContext *context) = 0;

    virtual std::any visitPortDocument(SwitchTopologyParser::PortDocumentContext *context) = 0;

    virtual std::any visitConfigLine(SwitchTopologyParser::ConfigLineContext *context) = 0;

    virtual std::any visitVlanItem(SwitchTopologyParser::VlanItemContext *context) = 0;

    virtual std::any visitVlanHeader(SwitchTopologyParser::VlanHeaderContext *context) = 0;

    virtual std::any visitVlanEntry(SwitchTopologyParser::VlanEntryContext *context) = 0;

    virtual std::any visitVlanSeparator(SwitchTopologyParser::VlanSeparatorContext *context) = 0;

    virtual std::any visitVlanId(SwitchTopologyParser::VlanIdContext *context) = 0;

    virtual std::any visitVlanName(SwitchTopologyParser::VlanNameContext *context) = 0;

    virtual std::any visitVlanStatus(SwitchTopologyParser::VlanStatusContext *context) = 0;

    virtual std::any visitPortList(SwitchTopologyParser::PortListContext *context) = 0;

    virtual std::any visitPortToken(SwitchTopologyParser::PortTokenContext *context) = 0;

    virtual std::any visitNameToken(SwitchTopologyParser::NameTokenContext *context) = 0;

    virtual std::any visitBriefItem(SwitchTopologyParser::BriefItemContext *context) = 0;

    virtual std::any visitBriefHeader(SwitchTopologyParser::BriefHeaderContext *context) = 0;

    virtual std::any visitBriefEntry(SwitchTopologyParser::BriefEntryContext *context) = 0;

    virtual std::any visitAddrOrUnassigned(SwitchTopologyParser::AddrOrUnassignedContext *context) = 0;

    virtual std::any visitBriefField(SwitchTopologyParser::BriefFieldContext *context) = 0;

    virtual std::any visitPortItem(SwitchTopologyParser::PortItemContext *context) = 0;

    virtual std::any visitPortEntry(SwitchTopologyParser::PortEntryContext *context) = 0;

    virtual std::any visitIfname(SwitchTopologyParser::IfnameContext *context) = 0;

    virtual std::any visitKeyWord(SwitchTopologyParser::KeyWordContext *context) = 0;

    virtual std::any visitGenericLine(SwitchTopologyParser::GenericLineContext *context) = 0;

    virtual std::any visitBlank(SwitchTopologyParser::BlankContext *context) = 0;

    virtual std::any visitElem(SwitchTopologyParser::ElemContext *context) = 0;


};

