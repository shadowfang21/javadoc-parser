package com.sf.ebao.jdoc.parser.rule

enum class PublicTargetMethod(val strData : String) {
	
	validateNonacceptanceTransfer("保單多筆轉手作業"),
	validateAllocate("保單受理指派作業"),
	validateAllocateTransfer("保單受理轉手作業"),
	validateBatchUploadTransfer("個險批次上傳作業"),
	validateBatchAllUploadTransfer("個險批次上傳作業外部轉直營"),
	validatePolicyTransferable("保單單筆轉手作業"),
	validateGrpPolicyAppoint("團險受理轉手作業"),
	validateGrpUploadTransfer("團險批次上傳轉手作業"),
	validateBRBDBatchUploadTransfer("BRBD整批上傳轉手作業"),
	validatePrintTransferAlertAble("補印轉換通知書"),
	validatePolicyCancelable("取消轉手")
}