package com.sf.ebao.jdoc.parser.rule

data class JdocRule(var level : String , var desc : String) {
	
	var key : Set<String> = HashSet<String>();
	
	private var msgDesc : String?; 
	
	init {
		key = desc.split(",")
			.filter { it.startsWith("\"MSG_") }
			.map { it.replace("\"", "") }
			.toSet();
		
		msgDesc = desc.split(",")
			.filter { it.startsWith("\"MSG_") }
			.map { StringResource.valueOf(it.replace("\"", "")).strData }
			.joinToString(separator = "")
			
			
	}	
	
	
	@Override
	public override fun toString() : String {
		
		return when {
			this.level.startsWith("warn") -> "警告"
			this.level.startsWith("error") -> "錯誤"
			else -> this.level;
		} + ":" + if (key.isNotEmpty()) msgDesc else this.desc.replace("\"", "");
		
//		return "${if ("warn" == this.level) "警告" else "錯誤"} : $desc";
	}
	
}