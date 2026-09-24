// Generated from /home/osboxes/IdeaProjects/SonarValidator/SonarValidator_Prober/components/parser/grammar/SwitchTopology.g4 by ANTLR 4.13.1
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link SwitchTopologyParser}.
 */
public interface SwitchTopologyListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#runningDocument}.
	 * @param ctx the parse tree
	 */
	void enterRunningDocument(SwitchTopologyParser.RunningDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#runningDocument}.
	 * @param ctx the parse tree
	 */
	void exitRunningDocument(SwitchTopologyParser.RunningDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanDocument}.
	 * @param ctx the parse tree
	 */
	void enterVlanDocument(SwitchTopologyParser.VlanDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanDocument}.
	 * @param ctx the parse tree
	 */
	void exitVlanDocument(SwitchTopologyParser.VlanDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#briefDocument}.
	 * @param ctx the parse tree
	 */
	void enterBriefDocument(SwitchTopologyParser.BriefDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#briefDocument}.
	 * @param ctx the parse tree
	 */
	void exitBriefDocument(SwitchTopologyParser.BriefDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#portDocument}.
	 * @param ctx the parse tree
	 */
	void enterPortDocument(SwitchTopologyParser.PortDocumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#portDocument}.
	 * @param ctx the parse tree
	 */
	void exitPortDocument(SwitchTopologyParser.PortDocumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#configLine}.
	 * @param ctx the parse tree
	 */
	void enterConfigLine(SwitchTopologyParser.ConfigLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#configLine}.
	 * @param ctx the parse tree
	 */
	void exitConfigLine(SwitchTopologyParser.ConfigLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanItem}.
	 * @param ctx the parse tree
	 */
	void enterVlanItem(SwitchTopologyParser.VlanItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanItem}.
	 * @param ctx the parse tree
	 */
	void exitVlanItem(SwitchTopologyParser.VlanItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanHeader}.
	 * @param ctx the parse tree
	 */
	void enterVlanHeader(SwitchTopologyParser.VlanHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanHeader}.
	 * @param ctx the parse tree
	 */
	void exitVlanHeader(SwitchTopologyParser.VlanHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanEntry}.
	 * @param ctx the parse tree
	 */
	void enterVlanEntry(SwitchTopologyParser.VlanEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanEntry}.
	 * @param ctx the parse tree
	 */
	void exitVlanEntry(SwitchTopologyParser.VlanEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanSeparator}.
	 * @param ctx the parse tree
	 */
	void enterVlanSeparator(SwitchTopologyParser.VlanSeparatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanSeparator}.
	 * @param ctx the parse tree
	 */
	void exitVlanSeparator(SwitchTopologyParser.VlanSeparatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanId}.
	 * @param ctx the parse tree
	 */
	void enterVlanId(SwitchTopologyParser.VlanIdContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanId}.
	 * @param ctx the parse tree
	 */
	void exitVlanId(SwitchTopologyParser.VlanIdContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanName}.
	 * @param ctx the parse tree
	 */
	void enterVlanName(SwitchTopologyParser.VlanNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanName}.
	 * @param ctx the parse tree
	 */
	void exitVlanName(SwitchTopologyParser.VlanNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#vlanStatus}.
	 * @param ctx the parse tree
	 */
	void enterVlanStatus(SwitchTopologyParser.VlanStatusContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#vlanStatus}.
	 * @param ctx the parse tree
	 */
	void exitVlanStatus(SwitchTopologyParser.VlanStatusContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#portList}.
	 * @param ctx the parse tree
	 */
	void enterPortList(SwitchTopologyParser.PortListContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#portList}.
	 * @param ctx the parse tree
	 */
	void exitPortList(SwitchTopologyParser.PortListContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#portToken}.
	 * @param ctx the parse tree
	 */
	void enterPortToken(SwitchTopologyParser.PortTokenContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#portToken}.
	 * @param ctx the parse tree
	 */
	void exitPortToken(SwitchTopologyParser.PortTokenContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#nameToken}.
	 * @param ctx the parse tree
	 */
	void enterNameToken(SwitchTopologyParser.NameTokenContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#nameToken}.
	 * @param ctx the parse tree
	 */
	void exitNameToken(SwitchTopologyParser.NameTokenContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#briefItem}.
	 * @param ctx the parse tree
	 */
	void enterBriefItem(SwitchTopologyParser.BriefItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#briefItem}.
	 * @param ctx the parse tree
	 */
	void exitBriefItem(SwitchTopologyParser.BriefItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#briefHeader}.
	 * @param ctx the parse tree
	 */
	void enterBriefHeader(SwitchTopologyParser.BriefHeaderContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#briefHeader}.
	 * @param ctx the parse tree
	 */
	void exitBriefHeader(SwitchTopologyParser.BriefHeaderContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#briefEntry}.
	 * @param ctx the parse tree
	 */
	void enterBriefEntry(SwitchTopologyParser.BriefEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#briefEntry}.
	 * @param ctx the parse tree
	 */
	void exitBriefEntry(SwitchTopologyParser.BriefEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#addrOrUnassigned}.
	 * @param ctx the parse tree
	 */
	void enterAddrOrUnassigned(SwitchTopologyParser.AddrOrUnassignedContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#addrOrUnassigned}.
	 * @param ctx the parse tree
	 */
	void exitAddrOrUnassigned(SwitchTopologyParser.AddrOrUnassignedContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#briefField}.
	 * @param ctx the parse tree
	 */
	void enterBriefField(SwitchTopologyParser.BriefFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#briefField}.
	 * @param ctx the parse tree
	 */
	void exitBriefField(SwitchTopologyParser.BriefFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#portItem}.
	 * @param ctx the parse tree
	 */
	void enterPortItem(SwitchTopologyParser.PortItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#portItem}.
	 * @param ctx the parse tree
	 */
	void exitPortItem(SwitchTopologyParser.PortItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#portEntry}.
	 * @param ctx the parse tree
	 */
	void enterPortEntry(SwitchTopologyParser.PortEntryContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#portEntry}.
	 * @param ctx the parse tree
	 */
	void exitPortEntry(SwitchTopologyParser.PortEntryContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#ifname}.
	 * @param ctx the parse tree
	 */
	void enterIfname(SwitchTopologyParser.IfnameContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#ifname}.
	 * @param ctx the parse tree
	 */
	void exitIfname(SwitchTopologyParser.IfnameContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#keyWord}.
	 * @param ctx the parse tree
	 */
	void enterKeyWord(SwitchTopologyParser.KeyWordContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#keyWord}.
	 * @param ctx the parse tree
	 */
	void exitKeyWord(SwitchTopologyParser.KeyWordContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#genericLine}.
	 * @param ctx the parse tree
	 */
	void enterGenericLine(SwitchTopologyParser.GenericLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#genericLine}.
	 * @param ctx the parse tree
	 */
	void exitGenericLine(SwitchTopologyParser.GenericLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#blank}.
	 * @param ctx the parse tree
	 */
	void enterBlank(SwitchTopologyParser.BlankContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#blank}.
	 * @param ctx the parse tree
	 */
	void exitBlank(SwitchTopologyParser.BlankContext ctx);
	/**
	 * Enter a parse tree produced by {@link SwitchTopologyParser#elem}.
	 * @param ctx the parse tree
	 */
	void enterElem(SwitchTopologyParser.ElemContext ctx);
	/**
	 * Exit a parse tree produced by {@link SwitchTopologyParser#elem}.
	 * @param ctx the parse tree
	 */
	void exitElem(SwitchTopologyParser.ElemContext ctx);
}