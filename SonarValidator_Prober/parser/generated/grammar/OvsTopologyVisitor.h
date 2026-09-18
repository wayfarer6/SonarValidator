
// Generated from grammar/OvsTopology.g4 by ANTLR 4.13.2

#pragma once


#include "antlr4-runtime.h"
#include "OvsTopologyParser.h"



/**
 * This class defines an abstract visitor for a parse tree
 * produced by OvsTopologyParser.
 */
class  OvsTopologyVisitor : public antlr4::tree::AbstractParseTreeVisitor {
public:

  /**
   * Visit parse trees produced by OvsTopologyParser.
   */
    virtual std::any visitShowDocument(OvsTopologyParser::ShowDocumentContext *context) = 0;

    virtual std::any visitListDocument(OvsTopologyParser::ListDocumentContext *context) = 0;

    virtual std::any visitFlowDocument(OvsTopologyParser::FlowDocumentContext *context) = 0;

    virtual std::any visitShowVlanDocument(OvsTopologyParser::ShowVlanDocumentContext *context) = 0;

    virtual std::any visitShowItem(OvsTopologyParser::ShowItemContext *context) = 0;

    virtual std::any visitBridgeLine(OvsTopologyParser::BridgeLineContext *context) = 0;

    virtual std::any visitPortLine(OvsTopologyParser::PortLineContext *context) = 0;

    virtual std::any visitIfaceLine(OvsTopologyParser::IfaceLineContext *context) = 0;

    virtual std::any visitAttrLine(OvsTopologyParser::AttrLineContext *context) = 0;

    virtual std::any visitNameToken(OvsTopologyParser::NameTokenContext *context) = 0;

    virtual std::any visitListItem(OvsTopologyParser::ListItemContext *context) = 0;

    virtual std::any visitListRecord(OvsTopologyParser::ListRecordContext *context) = 0;

    virtual std::any visitRecordSep(OvsTopologyParser::RecordSepContext *context) = 0;

    virtual std::any visitFlowItem(OvsTopologyParser::FlowItemContext *context) = 0;

    virtual std::any visitFlowLine(OvsTopologyParser::FlowLineContext *context) = 0;

    virtual std::any visitGenericLine(OvsTopologyParser::GenericLineContext *context) = 0;

    virtual std::any visitBlank(OvsTopologyParser::BlankContext *context) = 0;

    virtual std::any visitElem(OvsTopologyParser::ElemContext *context) = 0;


};

