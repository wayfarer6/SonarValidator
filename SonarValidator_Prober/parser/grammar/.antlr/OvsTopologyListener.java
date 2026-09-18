// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/parser/grammar/OvsTopology.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link OvsTopologyParser}.
 */
public interface OvsTopologyListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#showDocument}.
	 * @param ctx the parse tree
	 */
	void enterShowDocument(OvsTopologyParser.ShowDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#showDocument}.
	 * @param ctx the parse tree
	 */
	void exitShowDocument(OvsTopologyParser.ShowDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#listDocument}.
	 * @param ctx the parse tree
	 */
	void enterListDocument(OvsTopologyParser.ListDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#listDocument}.
	 * @param ctx the parse tree
	 */
	void exitListDocument(OvsTopologyParser.ListDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#flowDocument}.
	 * @param ctx the parse tree
	 */
	void enterFlowDocument(OvsTopologyParser.FlowDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#flowDocument}.
	 * @param ctx the parse tree
	 */
	void exitFlowDocument(OvsTopologyParser.FlowDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#showVlanDocument}.
	 * @param ctx the parse tree
	 */
	void enterShowVlanDocument(OvsTopologyParser.ShowVlanDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#showVlanDocument}.
	 * @param ctx the parse tree
	 */
	void exitShowVlanDocument(OvsTopologyParser.ShowVlanDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#showItem}.
	 * @param ctx the parse tree
	 */
	void enterShowItem(OvsTopologyParser.ShowItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#showItem}.
	 * @param ctx the parse tree
	 */
	void exitShowItem(OvsTopologyParser.ShowItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#bridgeLine}.
	 * @param ctx the parse tree
	 */
	void enterBridgeLine(OvsTopologyParser.BridgeLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#bridgeLine}.
	 * @param ctx the parse tree
	 */
	void exitBridgeLine(OvsTopologyParser.BridgeLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#portLine}.
	 * @param ctx the parse tree
	 */
	void enterPortLine(OvsTopologyParser.PortLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#portLine}.
	 * @param ctx the parse tree
	 */
	void exitPortLine(OvsTopologyParser.PortLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#ifaceLine}.
	 * @param ctx the parse tree
	 */
	void enterIfaceLine(OvsTopologyParser.IfaceLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#ifaceLine}.
	 * @param ctx the parse tree
	 */
	void exitIfaceLine(OvsTopologyParser.IfaceLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#attrLine}.
	 * @param ctx the parse tree
	 */
	void enterAttrLine(OvsTopologyParser.AttrLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#attrLine}.
	 * @param ctx the parse tree
	 */
	void exitAttrLine(OvsTopologyParser.AttrLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#nameToken}.
	 * @param ctx the parse tree
	 */
	void enterNameToken(OvsTopologyParser.NameTokenContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#nameToken}.
	 * @param ctx the parse tree
	 */
	void exitNameToken(OvsTopologyParser.NameTokenContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#listItem}.
	 * @param ctx the parse tree
	 */
	void enterListItem(OvsTopologyParser.ListItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#listItem}.
	 * @param ctx the parse tree
	 */
	void exitListItem(OvsTopologyParser.ListItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#listRecord}.
	 * @param ctx the parse tree
	 */
	void enterListRecord(OvsTopologyParser.ListRecordContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#listRecord}.
	 * @param ctx the parse tree
	 */
	void exitListRecord(OvsTopologyParser.ListRecordContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#recordSep}.
	 * @param ctx the parse tree
	 */
	void enterRecordSep(OvsTopologyParser.RecordSepContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#recordSep}.
	 * @param ctx the parse tree
	 */
	void exitRecordSep(OvsTopologyParser.RecordSepContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#flowItem}.
	 * @param ctx the parse tree
	 */
	void enterFlowItem(OvsTopologyParser.FlowItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#flowItem}.
	 * @param ctx the parse tree
	 */
	void exitFlowItem(OvsTopologyParser.FlowItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#flowLine}.
	 * @param ctx the parse tree
	 */
	void enterFlowLine(OvsTopologyParser.FlowLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#flowLine}.
	 * @param ctx the parse tree
	 */
	void exitFlowLine(OvsTopologyParser.FlowLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#genericLine}.
	 * @param ctx the parse tree
	 */
	void enterGenericLine(OvsTopologyParser.GenericLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#genericLine}.
	 * @param ctx the parse tree
	 */
	void exitGenericLine(OvsTopologyParser.GenericLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#blank}.
	 * @param ctx the parse tree
	 */
	void enterBlank(OvsTopologyParser.BlankContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#blank}.
	 * @param ctx the parse tree
	 */
	void exitBlank(OvsTopologyParser.BlankContext ctx);
	/**
	 * Enter a parse tree produced by {@link OvsTopologyParser#elem}.
	 * @param ctx the parse tree
	 */
	void enterElem(OvsTopologyParser.ElemContext ctx);
	/**
	 * Exit a parse tree produced by {@link OvsTopologyParser#elem}.
	 * @param ctx the parse tree
	 */
	void exitElem(OvsTopologyParser.ElemContext ctx);
}