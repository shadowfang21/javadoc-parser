package com.sf.ebao.jdoc.parser

import java.io.File

class JdocParser constructor (file : File) {
	
	var code : String;
	
	init {
		code = File(ClassLoader.getSystemResource("ScPtValidateServiceImpl.java").path).readText();
	}
}