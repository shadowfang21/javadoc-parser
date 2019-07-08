package com.sf.ebao.jdoc.parser.rule

import java.util.LinkedList

class JdocRuleSet(var methodName : String) {
	
	private var rules : MutableSet<JdocRule> = LinkedHashSet<JdocRule>();
			
	private var relatedMethod : MutableSet<String> = LinkedHashSet<String>();
	
	private var relatedRule : MutableSet<JdocRuleSet> = LinkedHashSet<JdocRuleSet>();
	
	
	public fun getRules() : MutableSet<JdocRule> {
		return rules;
	}
	
	public fun addRule(rule : JdocRule) {
		rules.add(rule);
	}
	
	public fun addMethod(method : String) {
		relatedMethod.add(method);
	}
	
	public fun showRule(prefix : String = "") {
		rules.forEach {
			println(prefix + methodName + "," + it);
		}
		relatedRule.forEach {
			it.showRule(prefix);
		}
	}
	
	public fun showMethod() {
		relatedMethod.forEach {
			println(methodName + "," + it)
		}
	}
	
	public fun evalLinkedRule(declaredMethods : List<JdocRuleSet>) {
		
		relatedMethod.forEach {
			val targetMethod = it;
            declaredMethods.find {
				it.methodName == targetMethod
			}?.let {
				relatedRule.add(it);
			}
		}
		
		
	}
}