package com.sf.ebao.jdoc.parser

import java.lang.ClassLoader
import java.io.File
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.ASTVisitor
import org.eclipse.jdt.core.dom.Javadoc
import com.sf.ebao.jdoc.parser.rule.JdocRule
import org.eclipse.jdt.core.dom.TagElement
import org.eclipse.jdt.core.dom.MethodDeclaration
import org.eclipse.jdt.core.dom.Block
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.MethodInvocation
import com.sf.ebao.jdoc.parser.rule.JdocRuleSet
import org.eclipse.jdt.core.dom.ReturnStatement
import org.eclipse.jdt.core.dom.IMethodBinding
import org.eclipse.jdt.core.dom.IfStatement
import org.eclipse.jdt.core.dom.Expression
import java.util.LinkedList
import org.eclipse.jdt.core.dom.StringLiteral
import com.sf.ebao.jdoc.parser.rule.PublicTargetMethod

fun main(args : Array<String>) {
	val code : String = File(ClassLoader.getSystemResource("ScPtValidateServiceImpl.java").path).readText();
	
	val parser : ASTParser = ASTParser.newParser(AST.JLS3);
	parser.setSource(code.toCharArray());
	
	val unit : CompilationUnit = parser.createAST(null) as CompilationUnit;
	
	val methodRuleSet = LinkedList<JdocRuleSet>(); 
	
	unit.accept(object : ASTVisitor() {
		
		public override fun visit(node : MethodDeclaration) : Boolean {
			
//			if (node.getName().toString().startsWith("validatePolicyTransferable")) {
//				println("MethodDeclaration : ${node.getName()}");
				val body : Block = node.body;
				
				val visitor = JdocRuleVisitor("${node.getName().toString()}_${node.parameters().count()}");
				
				body.accept(visitor);
				
				methodRuleSet.add(visitor.ruleSet);
//			}
			return false;
		}
	});
	
	var msgSet = HashSet<String>();
	methodRuleSet
		.filter {
			val methodName : String = it.methodName;
			PublicTargetMethod.values().any{ methodName.startsWith(it.name) }
		}
		.forEach {
			
			val methodName : String = PublicTargetMethod.valueOf(it.methodName.split("_").first()).strData;
			
//			println("------- $methodName -------");
			it.evalLinkedRule(methodRuleSet);
			it.showRule(methodName + ",");
	//		it.showMethod();
			
	//		it.getRules().forEach {
	//			msgSet.addAll(it.key);
	//		}
		}
//	msgSet.forEach(::println);
}

class JdocRuleVisitor(var methodName : String) : ASTVisitor(false) {
	
	var ruleSet = JdocRuleSet(methodName);
		
	
	override fun visit(node: MethodInvocation): Boolean {

//		println("${node.expression?.toString()} . ${node.name.identifier}");

		if (node.name.identifier == "addIgnoreNull") {
			node.arguments()
				.map { it as Expression }
				.forEach { it.accept(this) };
		} else {
			if ("ScPtMsgVO" == node.expression?.toString()) {

				if (node.name.identifier.startsWith("warn") || node.name.identifier.startsWith("error")) {
					val desc: String = node.arguments()
							.filter {
								it is StringLiteral
							}
//					.forEach{
//						println(" arg : ${it.javaClass.name} _ ${it.toString()}");
//					}
							.joinToString(",");


					ruleSet.addRule(JdocRule(node.name.identifier, desc));
				} 
				node.arguments()
						.map { it as Expression }
						.forEach { it.accept(this) };
				
			} else {
				if (node.name.identifier.startsWith("warn") || node.name.identifier.startsWith("validate")) {
					ruleSet.addMethod("${node.name.identifier}_${node.arguments().count()}");
				}
			}
		} 

		
		return false;
	}

//	override fun visit(node: ReturnStatement): Boolean {
//		println(node.toString());
//
//		node.expression.accept(this);
//
//		return true;
//	}

//	override fun visit(node: IfStatement): Boolean {
//		node.thenStatement.accept(this);
//		node.elseStatement?.accept(this);
//		return true;
//	}

}