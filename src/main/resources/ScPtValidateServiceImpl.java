package com.ebao.ls.sc.serviceImpl.transfer;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ebao.ls.chl.bs.IndividualAgentService;
import com.ebao.ls.chl.service.AgentService;
import com.ebao.ls.chl.service.ChannelOrgService;
import com.ebao.ls.chl.vo.AgentPositionVO;
import com.ebao.ls.chl.vo.ChannelOrgVO;
import com.ebao.ls.pa.pub.ref.api.prd.ProductService;
import com.ebao.ls.pub.util.TGLDateUtil;
import com.ebao.ls.sc.Constant;
import com.ebao.ls.sc.agent.vo.BusinessCate;
import com.ebao.ls.sc.agent.vo.FakeAgent;
import com.ebao.ls.sc.data.bo.PolicyProducerRole;
import com.ebao.ls.sc.service.agent.AgentQueryService;
import com.ebao.ls.sc.service.agent.AgentQueryService.ActiveAgentStatusPredicdate;
import com.ebao.ls.sc.service.agent.AgentQueryService.InforceAgentStatusPredicdate;
import com.ebao.ls.sc.service.agent.AgentQueryService.LeaveAgentStatusPredicdate;
import com.ebao.ls.sc.service.agent.AgentRewardPunishScService;
import com.ebao.ls.sc.service.agent.AgentScService;
import com.ebao.ls.sc.service.org.ScPersonalInfoLockService;
import com.ebao.ls.sc.service.policy.ContractMasterService;
import com.ebao.ls.sc.service.transfer.CommChoice;
import com.ebao.ls.sc.service.transfer.PolicyLastTransferInfo;
import com.ebao.ls.sc.service.transfer.PolicyNotSyncException;
import com.ebao.ls.sc.service.transfer.PolicyProducerRoleService;
import com.ebao.ls.sc.service.transfer.ScGrpTransferCaseService;
import com.ebao.ls.sc.service.transfer.ScPolicyProducerChgService;
import com.ebao.ls.sc.service.transfer.ScPolicyTransferAllocService;
import com.ebao.ls.sc.service.transfer.ScPolicyTransferCaseService;
import com.ebao.ls.sc.service.transfer.ScPtCaseStatus;
import com.ebao.ls.sc.service.transfer.ScPtManyToManyVO;
import com.ebao.ls.sc.service.transfer.ScPtOneToOneVO;
import com.ebao.ls.sc.service.transfer.ScPtPoliciesDetailService;
import com.ebao.ls.sc.service.transfer.ScPtPolicyKeepService;
import com.ebao.ls.sc.service.transfer.ScPtPolicyType;
import com.ebao.ls.sc.service.transfer.ScPtQueryService;
import com.ebao.ls.sc.service.transfer.ScPtReportInfoService;
import com.ebao.ls.sc.service.transfer.ScPtValidateContext;
import com.ebao.ls.sc.service.transfer.ScPtValidateService;
import com.ebao.ls.sc.service.transfer.ScTransferPolicyService;
import com.ebao.ls.sc.serviceImpl.transfer.PolicyProducerRoleServiceImpl.ProducerIdPredicate;
import com.ebao.ls.sc.vo.AgentChlVO;
import com.ebao.ls.sc.vo.BRBDCommonPtFileVO;
import com.ebao.ls.sc.vo.ChannelRole;
import com.ebao.ls.sc.vo.CommonPtFileVO;
import com.ebao.ls.sc.vo.CommonPtFileVO.ScPtFileTransferType;
import com.ebao.ls.sc.vo.ContractMasterVO;
import com.ebao.ls.sc.vo.GrpCommonPtFileVO;
import com.ebao.ls.sc.vo.ProducerRole;
import com.ebao.ls.sc.vo.ScPolicyProducerChgVO;
import com.ebao.ls.sc.vo.ScPolicyTransferCaseVO;
import com.ebao.ls.sc.vo.ScPolicyTransferVO;
import com.ebao.ls.sc.vo.ScPtMsgVO;
import com.ebao.ls.sc.vo.ScPtNewRoleVO;
import com.ebao.ls.sc.vo.ScPtPoliciesDetailVO;
import com.ebao.ls.sc.vo.ScPtValidateResult;
import com.ebao.ls.sc.vo.SingleCommonPtFileVO;
import com.ebao.ls.sc.vo.UserScChannelRoleVO;
import com.ebao.pub.framework.AppContext;
import com.ebao.pub.i18n.lang.CodeTable;
/**
 * <p>Title: 保單轉手驗證用</p>
 * <p>Description: Function Description Only vegetable</p>
 * <p>Copyright: Copyright (c) 2016</p>
 * <p>Company: TGL Co., Ltd.</p>
 * <p>Create Time: Dec 2, 2016</p> 
 * @author 
 * <p>Update Time: Dec 2, 2016</p>
 * <p>Updater: TGL155</p>
 * <p>Update Comments: </p>
 * 
 * <RULE_DEF>
 *   <RULE name="warnCaseExists" level="warn">檢核保單是否存在尚未歸檔的受理案件，受理編號尚未歸檔</RULE>
 *   <RULE name="nothingChange" level="error">未修改任何資料</RULE>
 * </RULE_DEF>
 */
public class ScPtValidateServiceImpl implements ScPtValidateService {
    
    private static final Logger logger = LoggerFactory.getLogger(ScPtValidateServiceImpl.class);

    private static final String LEAVE_REASON = "51";
    private static final String DISMISS_CHANNEL = "40";
    private static final String ORPHAN_OUT_REASON_31 = "31";
    private static final String ORPHAN_OUT_REASON_32 = "32";
    private static final String ORPHAN_IN_REASON_33 = "33"; //轉入孤保
    private static final String ORPHAN_OUT_REASON_34 = "34";
    
    /** 完全離職受理原因 */
    private static final Set<String> LEAVE_ACEPT_REASON = new HashSet<String>();
    
    /** 孤兒轉出受理原因 */
    private static final Set<String>[] ORPHAN_ACEPT_REASON = new HashSet[3];
    
    /**新服務業務員{msg}1,2,3**/
    private static final String[] SERVICE_MSG_CODE = new String[]{"MSG_1257188", "MSG_1257189", "MSG_1257190"};
    /**新領佣業務員{msg}1,2,3**/
    private static final String[] COMMISSION_MSG_CODE = new String[]{"MSG_1257194", "MSG_1257195", "MSG_1257196"};
    
    /** 新服務業務員{msg} **/
    private static final String NEW_SERVICE_MSG_CODE = "MSG_1261308";
    
    /** 原業務員 **/
    private static final String ORI_AGENT_MSG_CODE = "MSG_1262329";

    /** 新領佣業務員{msg} **/
    private static final String NEW_COMM_MSG_CODE = "MSG_1261306";
    
    @Resource(name = ChannelOrgService.BEAN_DEFAULT)
    private ChannelOrgService channelOrgService;
    
    @Resource
    private ScPersonalInfoLockService scPersonalInfoLockService; 
    
    @Resource
    private ScPtReportInfoService scPtReportInfoService;
    
    @Resource
    private ScPolicyProducerChgService scPolicyProducerChgService;
    
    @Resource
    private ScPolicyTransferCaseService scPolicyTransferCaseService;
    
    @Resource
    private ScPolicyTransferAllocService scPolicyTransferAllocService;
    
    @Resource
    private ScPtQueryService scPtQueryService;
    
    @Resource
    private AgentScService agentScService;
    
    @Resource
    private AgentQueryService agentQueryService;
    
    /** copy from agentInfoRegisterCodeValidator **/
    @Resource
    private AgentService agentService;

    
    @Resource
    private PolicyProducerRoleService policyProducerRoleService;
    
    @Resource
    private ScPtPoliciesDetailService scPtPoliciesDetailService;
    
    @Resource
    private ScGrpTransferCaseService scGrpTransferCaseService;
    
    @Resource(name = IndividualAgentService.BEAN_DEFAULT)
    private IndividualAgentService individualAgentService;
    
    @Resource
    private ContractMasterService contractMasterService;
    
    @Resource
    private ScPtPolicyKeepService scPtPolicyKeepService;
    
    @Resource(name = ProductService.BEAN_DEFAULT)
    private ProductService productService;
    
    @Resource
    private AgentRewardPunishScService agentRewardPunishService;
    
    @Resource
    private ScTransferPolicyService scTransferPolicyService;
    
    static {
        LEAVE_ACEPT_REASON.add("13"); //轉任
        LEAVE_ACEPT_REASON.add("14"); //轉任
        LEAVE_ACEPT_REASON.add(LEAVE_REASON); //離職
        LEAVE_ACEPT_REASON.add("52"); //退休
        LEAVE_ACEPT_REASON.add("53"); //身故
        
        final Set<String> type1 = new HashSet<String>(3);
        type1.add(ORPHAN_OUT_REASON_31);
        type1.add(ORPHAN_OUT_REASON_34);
        final Set<String> type2 = new HashSet<String>(3);
        type2.add(ORPHAN_OUT_REASON_31);
        type2.add(ORPHAN_OUT_REASON_32);
        final Set<String> type3 = new HashSet<String>(3);
        type3.add(ORPHAN_OUT_REASON_31);
        type3.add(ORPHAN_OUT_REASON_32);
        type3.add(ORPHAN_OUT_REASON_34);
        
        ORPHAN_ACEPT_REASON[0] = type1;
        ORPHAN_ACEPT_REASON[1] = type2;
        ORPHAN_ACEPT_REASON[2] = type3;
    }
    
    @Override
    public ScPtMsgVO validateSyncPolicyTransferToken(long policyId,
                    Long changeIdToken) throws PolicyNotSyncException {
        
        final ScPolicyProducerChgVO lastActiveChange = scPolicyProducerChgService.getLastActiveChange(policyId);
        
        if (lastActiveChange != null) {
            if (!ObjectUtils.equals(lastActiveChange.getChangeId(), changeIdToken)) {
                throw new PolicyNotSyncException();
            }
        } else {
            if (changeIdToken != null) {
                throw new PolicyNotSyncException();
            }
        }
        
        return null;
    }
    
    @Override
    public ScPtValidateResult validateNonacceptanceTransfer(ScPtValidateContext context, 
                    ScPolicyTransferVO vo) {
        final ScPtValidateResult result = new ScPtValidateResult();
        
        if (result.addIgnoreNull(this.validateScPolicyTransferVO(vo))) {
            return result;
        }
        
        if (vo.getOriAgentId() == null) { //原業務員不可空白
        	result.addIgnoreNull(ScPtMsgVO.error("MSG_1262313", "MSG_1261627"));
        	return result;
        }
        
        //受理原因不得為空
        if (StringUtils.isBlank(vo.getAcptReason())) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1255945", "MSG_1261627"));
            return result;
        }
        
        final ContractMasterVO policy = contractMasterService.load(vo.getPolicyId());
        
        result.addIgnoreNull(this.validatePolicyAgentTransferedToday(vo.getPolicyId(), 
                        vo.getOriAgentId(), 
                        vo.getNewServiceRegisterId() != null, 
                        vo.getNewCommRegisterId() != null));
        
        final AgentChlVO oriAgent = context.getInputAgent(agentScService, vo.getOriAgentId());
        
        ProducerRole[] role = null;
        //轉出業務員是否存在於保單之中
        if (vo.getNewServiceRegisterId() != null && vo.getNewCommRegisterId() != null) {
            role = new ProducerRole[]{ProducerRole.COMMISION, ProducerRole.SERVICE};
        } else if (vo.getNewServiceRegisterId() != null) {
            role = new ProducerRole[]{ProducerRole.SERVICE};
        } else if (vo.getNewCommRegisterId() != null) {
            role = new ProducerRole[]{ProducerRole.COMMISION};
        } else {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_242614")); //格試錯誤
        }
        Map<String, Object> oldAgentInfo = policyProducerRoleService.getCurrentAgentById(policy.getPolicyId(),
                        oriAgent.getAgentId(), role);
        
        if (oldAgentInfo == null) {
        	result.addIgnoreNull(ScPtMsgVO.error("MSG_1262297")); //非屬原業務員保單
        	return result;
        }
        
        
        boolean hasChanged = false;
        if (vo.getNewServiceRegisterId() != null) {
            if (!vo.getNewServiceRegisterId().equals(vo.getOriAgentId())) {
                final AgentChlVO serviceAgent = context.getInputAgent(agentScService, vo.getNewServiceRegisterId());
                //PCR 300148 : 20190703增加 新增檢核條件：當另一共享業務員或服務業務員不具一般人身保險資格時由代數主管或直屬主管承接
                ScPtValidateResult anotherResult = this.validateAgentTest(context, serviceAgent, NEW_SERVICE_MSG_CODE, policy.getPolicyId());
                if (anotherResult != null && !anotherResult.getMsgs().isEmpty()) {
                    
                    if (anotherResult.hasError()) {
                    	if (null != vo.getOriNewServiceAgentId()) {
                    		final AgentChlVO oriServiceAgent = context.getInputAgent(agentScService, vo.getOriNewServiceAgentId());
                        	vo.setNewServiceRegisterId(oriServiceAgent.getAgentId());
                        	vo.setNewServiceRegisterCode(oriServiceAgent.getRegisterCode());
                        	vo.setNewServiceRegisterName(oriServiceAgent.getAgentName());
                    	}
                    }
                }
                
                final AgentChlVO serviceNewAgent = context.getInputAgent(agentScService, vo.getNewServiceRegisterId());
                
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                this.validateAgentNotGrpFake(serviceNewAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, serviceNewAgent));
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oriAgent, serviceNewAgent)));
                
                result.addIgnoreNull(this.validateAgentTest(context, serviceNewAgent, NEW_SERVICE_MSG_CODE, policy.getPolicyId()));
                
                result.addIgnoreNull(this.validateServiceShareLeaveAgent(policy.getPolicyId(), serviceNewAgent));
                
                hasChanged = true;
            }
        }
        if (vo.getNewCommRegisterId() != null) {
            if (!vo.getNewCommRegisterId().equals(vo.getOriAgentId())) {
                final AgentChlVO commAgent = context.getInputAgent(agentScService, vo.getNewCommRegisterId());
                //PCR 300148 : 20190703增加 新增檢核條件：當另一共享業務員或服務業務員不具一般人身保險資格時由代數主管或直屬主管承接
                ScPtValidateResult anotherResult = this.validateAgentTest(context, commAgent, NEW_COMM_MSG_CODE, policy.getPolicyId());
                if (anotherResult != null && !anotherResult.getMsgs().isEmpty()) {
                    
                    if (anotherResult.hasError()) {
                    	if (null != vo.getOriNewCommAgentId()) {
                    		final AgentChlVO oriCommAgent = context.getInputAgent(agentScService, vo.getOriNewCommAgentId());
                        	vo.setNewCommRegisterId(oriCommAgent.getAgentId());
                        	vo.setNewCommRegisterCode(oriCommAgent.getRegisterCode());
                        	vo.setNewCommRegisterName(oriCommAgent.getAgentName());
                    	}
                    }
                }
                
                final AgentChlVO commNewAgent = context.getInputAgent(agentScService, vo.getNewCommRegisterId());
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                this.validateAgentNotGrpFake(commNewAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, commNewAgent));
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oriAgent, commNewAgent)));
                
                result.addIgnoreNull(this.validateAgentTest(context, commNewAgent, NEW_COMM_MSG_CODE, policy.getPolicyId()));
                
//                result.addIgnoreNull(this.validateMergeCommChoice(policy.getPolicyId(), 
//                        vo.getNewCommRegisterId(), CommChoice.fromCode(vo.getNewCommChoice())));
                result.addIgnoreNull(this.validateCommonChoiceX(policy.getPolicyId(), oriAgent, CommChoice.fromCode(vo.getNewCommChoice())));
                
                hasChanged = true;
            }
        }
        
        if (!hasChanged) {
            result.addIgnoreNull(this.warnNothingChange(1L, 1L));
        }

        if (vo.getPrintTransferNotify()) {
            result.addIgnoreNull(this.validateTransferAlertVersion(vo.getAcptReason(), false));
        }
        
        result.addIgnoreNull(this.warnCaseExist(vo.getPolicyId(), vo.getNewStdDateDate()));
        result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(vo.getNewStdDateDate()));
        
        if (!result.addIgnoreNull(this.validateAceptReasonOrphanOut(vo.getAcptReason())) ) {
        	result.addIgnoreNull(validateOrphanFieldTrasnferOut(vo.getNewServiceRegisterId() != null, oriAgent, 
                    vo.getAcptReason(), vo.getPrintTransferNotify(), null, null));
        }
        
        result.addIgnoreNull(validateOrphanIn(vo.getAcptReason(), oriAgent));
        result.addIgnoreNull(this.validateAgentDate(vo.getAcptReason(), oriAgent, vo.getNewStdDateDate()));
        result.addIgnoreNull(this.validateDismissChannel(vo.getAcptReason(), false));
        result.addIgnoreNull(this.validateShareCount(vo.getPolicyId()));
        
        return result;
    }
    
    @Override
    public ScPtValidateResult validateAllocate(ScPtValidateContext context, ScPolicyTransferVO vo) {
        
        final ScPtValidateResult result = new ScPtValidateResult();
        
        if (result.addIgnoreNull(this.validateScPolicyTransferVO(vo))) {
            return result;
        }
        
        final ScPolicyTransferCaseVO caseVO = context.getCaseVO(scPolicyTransferCaseService, vo.getCaseId());
        final AgentChlVO oldAgent = context.getInputAgent(agentScService, caseVO.getAgentId());
        
        final ContractMasterVO policy = contractMasterService.load(vo.getPolicyId());
        
        boolean hasChange = false;
        if (vo.getNewServiceRegisterId() != null) {
            if (!vo.getNewServiceRegisterId().equals(caseVO.getAgentId())) {
                final AgentChlVO serviceAgent = context.getInputAgent(agentScService, vo.getNewServiceRegisterId());
                
                //PCR 300148 : 20190703增加 新增檢核條件：當另一共享業務員或服務業務員不具一般人身保險資格時由代數主管或直屬主管承接
                ScPtValidateResult anotherResult = this.validateAgentTest(context, serviceAgent, NEW_SERVICE_MSG_CODE, policy.getPolicyId());
                if (anotherResult != null && !anotherResult.getMsgs().isEmpty()) {
                    
                    if (anotherResult.hasError()) {
                    	if (null != vo.getOriNewServiceAgentId()) {
                    		final AgentChlVO oriServiceAgent = context.getInputAgent(agentScService, vo.getOriNewServiceAgentId());
                        	vo.setNewServiceRegisterId(oriServiceAgent.getAgentId());
                        	vo.setNewServiceRegisterCode(oriServiceAgent.getRegisterCode());
                        	vo.setNewServiceRegisterName(oriServiceAgent.getAgentName());
                    	}
                    }
                }
                
                final AgentChlVO serviceNewAgent = context.getInputAgent(agentScService, vo.getNewServiceRegisterId());
                
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                this.validateAgentNotGrpFake(serviceNewAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, serviceNewAgent));
                
                result.addIgnoreNull(this.validateAgentTest(context, serviceNewAgent, NEW_SERVICE_MSG_CODE, vo.getPolicyId()));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oldAgent, serviceNewAgent)));
                
                result.addIgnoreNull(this.validateServiceShareLeaveAgent(policy.getPolicyId(), serviceNewAgent));
                
                
                hasChange = true;
            }
        }
        if (vo.getNewCommRegisterId() != null) {
            if (!vo.getNewCommRegisterId().equals(caseVO.getAgentId())) {
                final AgentChlVO commAgent = context.getInputAgent(agentScService, vo.getNewCommRegisterId());
                //PCR 300148 : 20190703增加 新增檢核條件：當另一共享業務員或服務業務員不具一般人身保險資格時由代數主管或直屬主管承接
                ScPtValidateResult anotherResult = this.validateAgentTest(context, commAgent, NEW_COMM_MSG_CODE, policy.getPolicyId());
                if (anotherResult != null && !anotherResult.getMsgs().isEmpty()) {
                    
                    if (anotherResult.hasError()) {
                    	if (null != vo.getOriNewCommAgentId()) {
                    		final AgentChlVO oriCommAgent = context.getInputAgent(agentScService, vo.getOriNewCommAgentId());
                        	vo.setNewCommRegisterId(oriCommAgent.getAgentId());
                        	vo.setNewCommRegisterCode(oriCommAgent.getRegisterCode());
                        	vo.setNewCommRegisterName(oriCommAgent.getAgentName());
                    	}                    	
                    }
                }
                
                final AgentChlVO commNewAgent = context.getInputAgent(agentScService, vo.getNewCommRegisterId());
               
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                this.validateAgentNotGrpFake(commNewAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, commNewAgent));
              
                result.addIgnoreNull(this.validateAgentTest(context, commNewAgent, NEW_COMM_MSG_CODE, vo.getPolicyId()));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oldAgent, commNewAgent)));
                
//                result.addIgnoreNull(this.validateMergeCommChoice(policy.getPolicyId(), 
//                                vo.getNewCommRegisterId(), CommChoice.fromCode(vo.getNewCommChoice())));
                result.addIgnoreNull(this.validateCommonChoiceX(policy.getPolicyId(), oldAgent, CommChoice.fromCode(vo.getNewCommChoice())));
                
                hasChange = true;
            }
        }
        
        //無更動資料應該是輸入的業務員資訊都等於原本的業務員
        if (!hasChange) {
            result.addIgnoreNull(this.warnNothingChange(1L, 1L));
        }
        result.addIgnoreNull(this.validateShareCount(vo.getPolicyId()));
        
        result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(vo.getNewStdDateDate()));
        result.addIgnoreNull(this.warnCaseExist(vo.getPolicyId(), caseVO.getCaseId(), vo.getNewStdDateDate()));
        
        result.addIgnoreNull(validateOrphanIn(caseVO.getAcptReason(), oldAgent));
        result.addIgnoreNull(this.validateDirectorCfmDateAndCommSettleDate(caseVO.getAcptReason(), vo.getNewStdDateDate(), oldAgent));
        result.addIgnoreNull(validateOrphanFieldTrasnferOut(vo.getNewServiceRegisterId() != null, oldAgent, 
        		caseVO.getAcptReason(), vo.getPrintTransferNotify(), null, null));
        result.addIgnoreNull(this.validateDismissChannel(caseVO.getAcptReason(), false));
        return result;
    }
    
    @Override
    public ScPtValidateResult validateAllocateTransfer(ScPtValidateContext context, ScPolicyTransferVO vo) {
        
        ScPtValidateResult result = new ScPtValidateResult();
        
        result.addIgnoreNull(this.validatePolicyAgentTransferedToday(vo.getPolicyId(), 
                        context.getCaseVO(scPolicyTransferCaseService, vo.getCaseId()).getAgentId(), 
                        vo.getNewServiceRegisterId() != null, 
                        vo.getNewCommRegisterId() != null));
        
//        result.addIgnoreNull(this.validatePolicyTransfered(vo.getCaseId(), vo.getPolicyId()));
        
        final ScPolicyTransferCaseVO caseVO = context.getCaseVO(scPolicyTransferCaseService, vo.getCaseId());
        final AgentChlVO agent = context.getInputAgent(agentScService, caseVO.getAgentId());
        result.addIgnoreNull(this.validateAgentDate(caseVO.getAcptReason(), agent, vo.getNewStdDateDate()));
        
        if (vo.getPrintTransferNotify()) {
            result.addIgnoreNull(this.validateTransferAlertVersion(caseVO.getAcptReason(), false));
        }
        
        result.addIgnoreNull(this.validateAllocate(context, vo));
        return result;
    }
    
    /**
     * <RULE_SET>
     * 	  	<RULE level="error">釋出類別錯誤</RULE>
     * 	  	<RULE level="error">受理原因代碼欄位不可空白/受理原因代碼輸入錯誤</RULE>
     * 	  	<RULE level="error">新佣金選擇欄輸入錯誤</RULE>
     * 	  	<RULE level="warn">序號未連續</RULE>
     * </RULE_SET> 
     */
    @Override
    public ScPtValidateResult validateFormat(final CommonPtFileVO row) {
        
        final ScPtValidateResult result = new ScPtValidateResult();
        
        //空白表示服務權和佣金權和100%
        final ScPtFileTransferType policyType = ScPtFileTransferType.fromValue(row.getPolicyType());
        if (policyType != null) {
            row.setPtPolicyType(policyType);
        } else {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_4098", "MSG_209622")); //釋出類別錯誤
        }
        
        if (StringUtils.isBlank(row.getAcptReason())) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1261374")); //受理原因代碼欄位不可空白
        } else {
            if (StringUtils.isBlank(CodeTable.getCodeById("T_SC_PT_ACCEPTANCE_REASON", row.getAcptReason()))) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261457")); //受理原因代碼輸入錯誤
            }
        }
        
      //當類別不為MODIFY時，轉出業務員為必輸
        if (!ScPtFileTransferType.MODIFY.equals(row.getPtPolicyType())) {
            
            if (StringUtils.isBlank(row.getFromAgentRegisterCode())) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262313", "MSG_1261627"));
            }
            if (StringUtils.isBlank(row.getNewServiceRegisterCode1()) && StringUtils.isBlank(row.getNewCommRegisterCode())) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262593", "MSG_1261627"));
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262595", "MSG_1261627"));
            }
        }
        
        if (!row.isGrp()) { //團險不檢核以下欄位
          //若有轉佣金權且不為預設的話要計算出結果
            if (StringUtils.isNotBlank(row.getNewCommChoice1())) {
                try {
                    CommChoice.valueOf(row.getNewCommChoice1());
                } catch (Exception e) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261372")); //新佣金選擇欄輸入錯誤
                }
            } else {
//                if (ScPtFileTransferType.MODIFY.equals(policyType)) {
//                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261373")); //新佣金選擇不可空白
//                }
            }
            
            if (StringUtils.isNotBlank(row.getVisitDeadline())) {
                if (!CommonPtFileVO.validateDateFormat(row.getVisitDeadline())) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261376")); //約訪截止日期格式錯誤
                }
            }
            if (StringUtils.isNotBlank(row.getVisitingListDate())) {
                if (!CommonPtFileVO.validateDateFormat(row.getVisitingListDate())) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261377")); //拜會清單日期格式錯誤
                }
            }
        }
        
        if (StringUtils.isNotBlank(row.getUploadDirectorCfmDate())) {
            if (!CommonPtFileVO.validateDateFormat(row.getUploadDirectorCfmDate())) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261378")); //STD主管確認日格式錯誤
            }
        } 
        if (StringUtils.isNotBlank(row.getIsPrintTransferAlert())) {
            if (!Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert())) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262264")); //轉換通知書格式錯誤
            }
        }
        if (StringUtils.isEmpty(row.getSn()) || !row.getSn().equals(String.valueOf(row.getIndex()))) {
            result.addIgnoreNull(ScPtMsgVO.warn("MSG_1262355")); //序號未連續
        }
        
        return result;
    }

    /**
     * <p>Description : 檢核姓名//原業務員字號與原業務員姓名不符</p>
     * <RULE_SET>
     * 	  	<RULE level="error">檢核姓名//原業務員字號與原業務員姓名不符</RULE>
     * </RULE_SET> 
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 30, 2017</p>
     * @param dbName
     * @param fileName
     * @return
     */
    private ScPtMsgVO validateName(String dbName, String fileName) {
        ScPtMsgVO msg = null;
        
        //null == null, not null == not null
        if (!StringUtils.equals(dbName, fileName)) {
            //blank == null, null == blank
            if (StringUtils.isNotBlank(dbName) || StringUtils.isNotBlank(fileName)) {
                msg = ScPtMsgVO.error("MSG_1262597");
            }
        }
        return msg;
    }
    
    /**
     * 批次上傳修改檢核
     * @param context
     * @param row
     * @return
     */
    private ScPtValidateResult validateBatchModify(final ScPtValidateContext context, final SingleCommonPtFileVO row) {
    	final ScPtValidateResult result = new ScPtValidateResult();
    	
    	//上次轉手是否有修改領佣權
        final PolicyLastTransferInfo lastTransInfo = scPolicyProducerChgService.getPolicyLastTransferInfo(row.getPolicyId());
        
        if (lastTransInfo != null) {
            if (lastTransInfo.isCommChange()) {
                row.setIsLastChangeComm(true);
            }
            
            if (result.addIgnoreNull(this.validateLastTransAgentInfo(lastTransInfo, row))) {
            	return result;
            }
            
            //如果上傳檔案沒有傳入主管確認日的話就用上次的確認來計算
            if (StringUtils.isBlank(row.getUploadDirectorCfmDate())) {
            	
            	Date date = lastTransInfo.getDirectorCfmDate();
            	
            	if (date == null) { //dc data might be null
            		date = lastTransInfo.getChangeDate();
            	}
            	row.setUploadDirectorCfmDate(TGLDateUtil.format(date));
            }
            
            final Date directorDate = CommonPtFileVO.parseDate(row.getUploadDirectorCfmDate()); //default or 
            
        	result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(directorDate));
            
        	AgentChlVO oldAgent = null;
            //old agent related check
            if (StringUtils.isNotEmpty(row.getFromAgentRegisterCode())) {
            	long agentId = lastTransInfo.getOldAgentId(row.getFromAgentRegisterCode());
            	
            	final AgentChlVO agent = context.getInputAgent(agentScService, agentId);
            	
            	if (agent != null) {
            		
            		oldAgent = agent;
            		
            		if (!ChannelRole.getRoleFromAgentCate(agent.getAgentCate()).isExtenal()) {
            			result.addIgnoreNull(this.validateAgentDate(row.getAcptReason(), agent, directorDate));
                        
                        result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                        StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()),
                                        agent, row.getAcptReason(), 
                                        Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert()), 
                                        row.getVisitingListDate(), row.getVisitDeadline()));
                        
                        result.addIgnoreNull(validateOrphanIn(row.getAcptReason(), agent));
                        
                        result.addIgnoreNull(this.validateDismissChannel(row.getAcptReason(), false));
            		}
            	}
            }
            
            //new service agent related check
            if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
            	long agentId = lastTransInfo.getNewAgentId(row.getNewServiceRegisterCode1(), ProducerRole.SERVICE);
            	
            	final AgentChlVO agent = context.getInputAgent(agentScService, agentId);
            	
            	result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateDirectorCfmDateAndProbationDate(directorDate, oldAgent,  agent)));
            }
            
            //new comm agent related check
            if (StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
            	
            	long agentId= lastTransInfo.getNewAgentId(row.getNewCommRegisterCode(), ProducerRole.COMMISION);
            	
            	final AgentChlVO agent = context.getInputAgent(agentScService, agentId);
            	
            	result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateDirectorCfmDateAndProbationDate(directorDate, oldAgent, agent)));
            	
            	if (StringUtils.isNotEmpty(row.getNewCommChoice1())) {
                	result.addIgnoreNull(this.validateCommonChoiceX(lastTransInfo, CommChoice.valueOf(row.getNewCommChoice1())));
                }
            }
            
            result.addIgnoreNull(this.validateVisitDate(
            		CommonPtFileVO.parseNullableDate(row.getVisitingListDate()), CommonPtFileVO.parseNullableDate(row.getVisitDeadline())));
            
        } else {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1261348"));
        }
    	return result;
    }
    
    @Override
    public ScPtValidateResult validateBatchUploadTransfer(final ScPtValidateContext context, final SingleCommonPtFileVO row) {
        
        
        final ScPtValidateResult result = new ScPtValidateResult();
        
        result.addIgnoreNull(this.validateFormat(row));
        
        if (!result.hasError()) {
            //validate content
            
            final ContractMasterVO policy = contractMasterService.getPolicyByPolicyCode(row.getPolicyCode());
           
            if (policy == null) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1253498")); //保單號碼不存在
                return result;
            }
            row.setPolicyId(policy.getPolicyId());
            
            //沒權限舊不驗證其他東西
            if (result.addIgnoreNull(this.validateExecutorRight(
                            context.getUserChannelRole(), policy.getChannelType().toString()))) {
                return result;
            }
            
            //如果為轉手的話
            if (!ScPtFileTransferType.MODIFY.equals(row.getPtPolicyType())) {
            	
            	result.addIgnoreNull(this.validatePolicyHolder(policy.getPolicyId(), row.getHolderCertiCode()));
                result.addIgnoreNull(this.validatePolicyInsuredList(policy.getPolicyId(), row.getInsuredCertiCode()));
            	
            	final Date directorDate = CommonPtFileVO.parseDate(row.getUploadDirectorCfmDate());
            	result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(directorDate));
                
                AgentChlVO oldAgent = null;
                if (StringUtils.isNotEmpty(row.getFromAgentRegisterCode())) { //前面format就已經判斷此欄位
                    
                    ProducerRole[] role = null;
                    //轉出業務員是否存在於保單之中
                    if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()) && StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
                        role = new ProducerRole[]{ProducerRole.COMMISION, ProducerRole.SERVICE};
                    } else if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
                        role = new ProducerRole[]{ProducerRole.SERVICE};
                    } else if (StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
                        role = new ProducerRole[]{ProducerRole.COMMISION};
                    } else {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_242614")); //格試錯誤
                    }
                    
                    Map<String, Object> oldAgentInfo = policyProducerRoleService.getCurrentAgentByCode(policy.getPolicyId(),
                                    row.getFromAgentRegisterCode(), role);
                    
                    if (oldAgentInfo != null) {
                        
                        final Long oldAgentId = MapUtils.getLong(oldAgentInfo, "AGENT_ID");
                        
                        row.setFromAgentId(oldAgentId);
                        
                        if ("N".equals(MapUtils.getString(oldAgentInfo, "IS_CHANNEL"))) { //person
                            oldAgent = context.getInputAgent(agentScService, oldAgentId);
                            
                            result.addIgnoreNull(ScPtMsgVO.decorator(ORI_AGENT_MSG_CODE, 
                                            this.validateName(oldAgent.getAgentName(), row.getFromAgentName())));
                            
                            result.addIgnoreNull(this.validateAgentDate(row.getAcptReason(), oldAgent, directorDate));
                            
                            result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                            StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()),
                                            oldAgent, row.getAcptReason(), 
                                            Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert()), 
                                            row.getVisitingListDate(), row.getVisitDeadline()));
                            
                            result.addIgnoreNull(validateOrphanIn(row.getAcptReason(), oldAgent));
                            
                            result.addIgnoreNull(this.validateDismissChannel(row.getAcptReason(), false));
                            
                        } else {
                            result.addIgnoreNull(ScPtMsgVO.decorator(ORI_AGENT_MSG_CODE, 
                                            this.validateName(MapUtils.getString(oldAgentInfo, "NAME"), row.getFromAgentName())));
                            result.addIgnoreNull(ScPtMsgVO.decorator(ORI_AGENT_MSG_CODE, warnAgentInternalChannel()));
                        }
                        //檢核轉出欄位今日是否已轉手
                        result.addIgnoreNull(this.validatePolicyAgentTransferedToday(policy.getPolicyId(), 
                                        oldAgentId, 
                                        StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()), 
                                        StringUtils.isNotEmpty(row.getNewCommRegisterCode())));
                        
                    } else {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262297")); //非屬原業務員保單
                    }
                }
                
                Boolean hasChange = null;
                
                //新服務業務員檢核
                if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
                    /**
                     * 限定僅內部通路業務員
                     */
                    final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewServiceRegisterCode1());
                    
                    if (newAgent == null) {
                        
                        if (!result.addIgnoreNull(
                                        ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                                        this.validateIsChannelCode(row.getNewServiceRegisterCode1())))) {
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                        }
                    } else {
                        
                        hasChange = (Boolean) ObjectUtils.defaultIfNull(hasChange, Boolean.FALSE);
                        
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                        this.validateName(newAgent.getAgentName(), row.getNewServiceName1())));
                        
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentCate(newAgent, false)));
                        
                        
                        if (!newAgent.getAgentId().equals(row.getFromAgentId())) {
                            row.setNewServiceAgentId(newAgent.getAgentId());
                            
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                            this.validateAgentNotGrpFake(newAgent)));
                            result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, newAgent));
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateDirectorCfmDateAndProbationDate(directorDate, oldAgent,  newAgent)));
                            
                            result.addIgnoreNull(this.validateAgentTest(context, newAgent, NEW_SERVICE_MSG_CODE, policy.getPolicyId()));
                            
                            result.addIgnoreNull(this.validateServiceShareLeaveAgent(policy.getPolicyId(), newAgent));
                            result.addIgnoreNull(this.warnShareServiceMerged(row));
                            
                            hasChange = true;
                        }

                    }
                }
                
                if (StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
                    /**
                     * 限定僅內部通路業務員
                     */
                    final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewCommRegisterCode());
                    
                    if (newAgent == null) {
                        if (!result.addIgnoreNull(
                                        ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                                        this.validateIsChannelCode(row.getNewCommRegisterCode())))) {
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                        }
                    } else {
                        hasChange = (Boolean) ObjectUtils.defaultIfNull(hasChange, Boolean.FALSE);
                        
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                        this.validateName(newAgent.getAgentName(), row.getNewCommName())));
                        
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentCate(newAgent, false)));
                        
                        if (!newAgent.getAgentId().equals(row.getFromAgentId())) {
                            
                            row.setNewCommAgentId(newAgent.getAgentId());
                            
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                            this.validateAgentNotGrpFake(newAgent)));
                            result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, newAgent));
                            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateDirectorCfmDateAndProbationDate(directorDate, oldAgent, newAgent)));
                            
                            result.addIgnoreNull(this.validateAgentTest(context, newAgent, NEW_COMM_MSG_CODE, policy.getPolicyId()));
                            
                            
                            //檢查是否為非共享件                            
                            boolean notShareCommStatus = false;
                            if (StringUtils.isNotBlank(row.getCommShareRate1()) && !row.getCommShareRate1().equals("100")) {
                            	if (StringUtils.isNotBlank(row.getCommAgentRegisterCode1())) {
                                	final AgentChlVO commAgent1 = context.getAgentByRegisterCode(agentQueryService, row.getCommAgentRegisterCode1());
                                	if (BusinessCate.isFakePerson(commAgent1)) {
                                		notShareCommStatus = true;
                                	}
                                }
                            	if (StringUtils.isNotBlank(row.getCommAgentRegisterCode2())){
                                	final AgentChlVO commAgent2= context.getAgentByRegisterCode(agentQueryService, row.getCommAgentRegisterCode2());
                                	if (BusinessCate.isFakePerson(commAgent2)) {
                                		notShareCommStatus = true;
                                	}
                                }
                            	if (StringUtils.isNotBlank(row.getCommAgentRegisterCode3())) {
                                	final AgentChlVO commAgent3= context.getAgentByRegisterCode(agentQueryService, row.getCommAgentRegisterCode3());
                                	if (BusinessCate.isFakePerson(commAgent3)) {
                                		notShareCommStatus = true;
                                	}
                                }
                            }
                            
                            result.addIgnoreNull(this.warnNotShareCommMerged(row,notShareCommStatus));
                            
                            if (!notShareCommStatus) {
                            	result.addIgnoreNull(this.warnShareCommMerged(row));
                            }
                            
                            //檢核佣金選擇與共享件是否適用
                            if (StringUtils.isNotBlank(row.getNewCommChoice1())) {
//                                result.addIgnoreNull(this.validateMergeCommChoice(policy.getPolicyId(), 
//                                                newAgent.getAgentId(), CommChoice.valueOf(row.getNewCommChoice1())));
                                result.addIgnoreNull(this.validateCommonChoiceX(row.getPolicyId(), oldAgent, CommChoice.valueOf(row.getNewCommChoice1())));
                            } else {
                                
                                //當佣金選擇空白的時候設定預設並檢核之
                                if (oldAgent != null) {
                                    final CommChoice defaultCommChoice = 
                                                    scTransferPolicyService.getDefaultCommChoice(row.getPolicyId(), newAgent.getAgentId(), oldAgent.getAgentId());
                                    row.setNewCommChoice1(defaultCommChoice.name());
//                                    result.addIgnoreNull(this.validateMergeCommChoice(policy.getPolicyId(), 
//                                                    newAgent.getAgentId(), CommChoice.valueOf(row.getNewCommChoice1())));

                                }
                            }
                            
                            hasChange = true;
                        }
                    }
                }
                
                if (Boolean.FALSE.equals(hasChange)) {
                    result.addIgnoreNull(this.warnNothingChange(1, 1));
                }
                
                result.addIgnoreNull(this.warnCaseExist(policy.getPolicyId(), directorDate));
                result.addIgnoreNull(this.validateShareCount(policy.getPolicyId()));
                result.addIgnoreNull(this.validateVisitDate(
                		CommonPtFileVO.parseNullableDate(row.getVisitingListDate()), CommonPtFileVO.parseNullableDate(row.getVisitDeadline())));
            } else {
            	result.addIgnoreNull(this.validateBatchModify(context, row));
            }
            
            if (Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert())) {
                if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
                    result.addIgnoreNull(this.validateTransferAlertVersion(row.getAcptReason(), false));
                } 
            }
        }
        return result;
    }
    
    /**
     * <RULE_SET>
     * 	  	<RULE level="error">非屬外部通路保單</RULE>
     * </RULE_SET> 
     */
    @Override
	public ScPtValidateResult validateBatchAllUploadTransfer(ScPtValidateContext context, SingleCommonPtFileVO row) {

        final ScPtValidateResult result = new ScPtValidateResult();
        
        result.addIgnoreNull(this.validateFormat(row));
        
        if (!result.hasError()) {
            //validate content
            
            final ContractMasterVO policy = contractMasterService.getPolicyByPolicyCode(row.getPolicyCode());
           
            if (policy == null) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1253498")); //保單號碼不存在
                return result;
            }
            row.setPolicyId(policy.getPolicyId());
            
            //直接綁定此作業限定外部轉入直營通路
            if (!ChannelRole.getRoleFromChannelType(String.valueOf(policy.getChannelType())).isExtenal()) {
            	result.addIgnoreNull(ScPtMsgVO.errorMsg("非屬外部通路保單"));
                return result;
            } 
            
            //沒權限舊不驗證其他東西
            if (result.addIgnoreNull(this.validateExecutorRight(
                            context.getUserChannelRole(), policy.getChannelType().toString()))) {
                return result;
            }
            
            if (result.addIgnoreNull(this.validatePolicyTransferedThisDay(policy.getPolicyId()))) {
                return result;
            }
            
            final Date directorDate = CommonPtFileVO.parseDate(row.getUploadDirectorCfmDate());
            
            result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(directorDate));
            
            
            //新服務業務員檢核
            if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
                /**
                 * 限定僅內部通路業務員
                 */
                final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewServiceRegisterCode1());
                
                if (newAgent == null) {
                    if (!result.addIgnoreNull(
                                    ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                                    this.validateIsChannelCode(row.getNewServiceRegisterCode1())))) {
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                    }
                } else {
                	
                	if (!result.addIgnoreNull(this.validateAceptReasonOrphanOut(row.getAcptReason())) ) {
                		result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()),
                                null, row.getAcptReason(), 
                                Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert()), 
                                row.getVisitingListDate(), row.getVisitDeadline()));
                	}
                	
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                    this.validateName(newAgent.getAgentName(), row.getNewServiceName1())));
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentCate(newAgent, false)));
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                    this.validateAgentNotGrpFake(newAgent)));
                    result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, newAgent));

                    result.addIgnoreNull(this.validateAgentTest(context, newAgent, NEW_SERVICE_MSG_CODE, policy.getPolicyId()));
                    
                    row.setNewServiceAgentId(newAgent.getAgentId());
                }
            }
            
            if (StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
                /**
                 * 限定僅內部通路業務員
                 */
                final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewCommRegisterCode());
                
                if (newAgent == null) {
                    if (!result.addIgnoreNull(
                                    ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                                    this.validateIsChannelCode(row.getNewCommRegisterCode())))) {
                        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                    }
                } else {
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                    this.validateName(newAgent.getAgentName(), row.getNewCommName())));
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentCate(newAgent, false)));
                    
                    row.setNewCommAgentId(newAgent.getAgentId());
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                    this.validateAgentNotGrpFake(newAgent)));
                    result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, newAgent));

                    result.addIgnoreNull(this.validateAgentTest(context, newAgent, NEW_COMM_MSG_CODE, policy.getPolicyId()));
                }
            }
            
            result.addIgnoreNull(this.warnCaseExist(policy.getPolicyId(), directorDate));

            if (Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert())) {
                if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
                    result.addIgnoreNull(this.validateTransferAlertVersion(row.getAcptReason(), false));
                } 
            }
        }
        return result;
	}
    
    @Override
    public ScPtValidateResult validatePolicyTransferable(
                    ScPtManyToManyVO vo) {
        final ScPtValidateContext context = new ScPtValidateContext();
        final ScPtValidateResult result = new ScPtValidateResult(); 
        
        result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, validateAgentCount(vo.getOriCommAgent(),
                        vo.getNewCommAgent())));
        //取消服務業務員數量限制
//        result.addIgnoreNull(this.concateMsgIfNotNull(NEW_SERVICE_MSG_CODE, validateAgentCount(vo.getServiceAgent(),
//                            vo.getNewServiceAgent())));
        
        //2016/01/25 如果有不可少轉多則直接離開
        if (result.hasError()) {
            return result;
        }
        
        result.addIgnoreNull(this.warnNothingChange(vo));
        
        //input scope validate
        if (!vo.getNewCommAgent().isEmpty()) {
            result.addIgnoreNull(this.validateCommissionRateTotal(
                            CollectionUtils.collect(vo.getNewCommAgent(), ScPtNewRoleVO.getAssignRateTransformer())));
            result.addIgnoreNull(this.validateCommRateZero(vo.getNewCommAgent()));
        }
        
        
        result.addIgnoreNull(this.warnCaseExist(vo.getPolicyId(), vo.getChgVO().getStdDirectorCfmDate()));
        
        final ContractMasterVO policyVO = contractMasterService.load(vo.getPolicyId());
        
        //  外部通路
        if (vo.isTransferToExternal()) {
            
            long channelOrgId = policyVO.getChannelOrgId();
            
            if (vo.getNewServiceAgent().isEmpty()) {
                //直營轉外部的時候不能只有轉"服務業務員"
                if (!ChannelRole.getRoleFromChannelType(String.valueOf(policyVO.getChannelType())).isExtenal()) {
                    if (!vo.getNewBrbdServiceAgent().isEmpty()) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262388"));
                        return result;
                    }
                }
            } else {
                //如果只是單純轉服務業務員但沒有轉分支
                channelOrgId = vo.getNewServiceAgent().get(0).getAgentId();
            }
            
            result.addIgnoreNull(this.warnExternalServiceAgentChannelOrg(
            				channelOrgService.getChannelOrg(channelOrgId), 
                            vo.getNewBrbdServiceAgent(), SERVICE_MSG_CODE));
            
            result.addIgnoreNull(this.validateAgent(vo.getNewBrbdServiceAgent(), SERVICE_MSG_CODE));
            /** 此保單需要的資格證種類 */
            
            result.addIgnoreNull(this.validateAgentTest(context, vo.getNewBrbdServiceAgent(), SERVICE_MSG_CODE, vo.getPolicyId()));
            
        } else {
        	result.addIgnoreNull(this.validateVisitDate(vo.getChgVO().getVisitingListDate(), vo.getChgVO().getVisitDeadline()));
            result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(vo.getChgVO().getStdDirectorCfmDate()));
//            result.addIgnoreNull(this.warnAcceptanceReason(vo.getChgVO().getAcptReason()));
            
            //要印轉換通知書
            if (vo.getPrintTransferNotify()) {
                
                result.addIgnoreNull(this.validatePolicyStatePrintTransferAlert(policyVO.getRiskStatus()));
                if (CollectionUtils.isNotEmpty(vo.getNewServiceAgent())) {
                    result.addIgnoreNull(this.validateTransferAlertVersion(vo.getChgVO().getAcptReason(), false));
                } else {
                    if (CollectionUtils.isNotEmpty(vo.getNewCommAgent())) {
                        result.addIgnoreNull(this.validateTransferAlertWithNoService());
                    }
                }
            }
            result.addIgnoreNull(this.warnServiceAgentFakeExists(vo.getNewServiceAgent()));
            
            result.addIgnoreNull(this.validateAgent(vo.getNewServiceAgent(), SERVICE_MSG_CODE));
            result.addIgnoreNull(this.validateAgentTest(context, vo.getNewServiceAgent(), SERVICE_MSG_CODE, vo.getPolicyId()));
            
            
            result.addIgnoreNull(this.validateAgent(vo.getNewCommAgent(), COMMISSION_MSG_CODE));
            result.addIgnoreNull(this.validateAgentTest(context, vo.getNewCommAgent(), COMMISSION_MSG_CODE, vo.getPolicyId()));
        }
        
        vo.analysis(); //排除未轉手資料
        
        //盡量減少查尋吧
        boolean hasTransfered = this.validatePolicyTransferedThisDay(vo.getPolicyId()) != null;        
        
        boolean orphanCheckFlag = false; //用來處理只檢核一次的flag
        for (ScPtOneToOneVO transferVO : vo.getAnalysisedTrans()) {
            
            //有已經轉手過的就直接離開
            if (hasTransfered && result.addIgnoreNull(this.validatePolicyAgentTransferedToday(vo.getPolicyId(), 
                            transferVO.getFromAgentId(), 
                            transferVO.getNewServiceId() != null, 
                            transferVO.getNewCommId() != null))) {
                break;
            }
            if (!vo.isTransferToExternal()) {
                
            	result.addIgnoreNull(this.validateDismissChannel(vo.getChgVO().getAcptReason(), 
            			ChannelRole.getRoleFromAgentCate(transferVO.getFromAgentRole().getAgentChlVO(agentScService).getAgentCate()).isExtenal()));
            	
                result.addIgnoreNull(validateOrphanIn(vo.getChgVO().getAcptReason(), 
                                transferVO.getFromAgentRole().getAgentChlVO(agentScService)));
                
                //單筆轉手改成警告
                result.addIgnoreNull(ScPtMsgVO.warn(this.validateAgentDate(vo.getChgVO().getAcptReason(),
                                transferVO.getFromAgentRole().getAgentChlVO(agentScService), vo.getChgVO().getStdDirectorCfmDate())));
                
                ScPtMsgVO commReturnCheck = null;
                if (transferVO.getToCommRole() != null) {
                    commReturnCheck = this.validateDirectorCfmDateAndProbationDate(vo.getChgVO().getStdDirectorCfmDate(), 
                                    transferVO.getFromAgentRole().getAgentChlVO(agentScService), 
                                    transferVO.getToCommRole().getAgentChlVO(agentScService));
                }
                
                ScPtMsgVO serviceReturnCheck = null;
                if (transferVO.getToServiceRole() != null) {
                    serviceReturnCheck = this.validateDirectorCfmDateAndProbationDate(vo.getChgVO().getStdDirectorCfmDate(), 
                                    transferVO.getFromAgentRole().getAgentChlVO(agentScService), 
                                    transferVO.getToServiceRole().getAgentChlVO(agentScService));
                }
                //回任檢核, 因為fromagent一定相同所以只顯示一個就好
                if (commReturnCheck != null || serviceReturnCheck != null) {
                    
                    final AgentChlVO formAgent = transferVO.getFromAgentRole().getAgentChlVO(agentScService);
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(formAgent.getRegisterCode() + formAgent.getAgentName(), 
                                    (ScPtMsgVO) ObjectUtils.defaultIfNull(commReturnCheck, serviceReturnCheck)));
                }
                
                //當完全沒有異動服務業務員的時候要驗異動領佣業務雸
                if (vo.getServiceAgentDetail().getTransferedData().isEmpty() && !orphanCheckFlag) {
                    orphanCheckFlag = result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                    false, //根據總服務變更來判斷有沒以異動服務業務員
                                    transferVO.getFromAgentRole().getAgentChlVO(agentScService), 
                                    vo.getChgVO().getAcptReason(), vo.getPrintTransferNotify(), 
                                    vo.getChgVO().getVisitingListDate(), vo.getChgVO().getVisitDeadline()));
                } else {
                    //當有異動服務業務員的時候只有檢核服務業務員異動相關才有意義
                    if (transferVO.getNewServiceId() != null && !orphanCheckFlag) {
                        orphanCheckFlag = result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                        true, //根據總服務變更來判斷有沒以異動服務業務員
                                        transferVO.getFromAgentRole().getAgentChlVO(agentScService), 
                                        vo.getChgVO().getAcptReason(), vo.getPrintTransferNotify(), 
                                        vo.getChgVO().getVisitingListDate(), vo.getChgVO().getVisitDeadline()));
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * <RULE_SET>
     * 	  	<RULE level="error">非有效保單不可移轉</RULE>
     * 	  	<RULE level="error">新服務業務員及新領佣業務員須為同一人</RULE>
     * 	  	<RULE level="error">不可只有轉出領佣權</RULE>
     * </RULE_SET>
     */
	@Override
    public ScPtValidateResult validateGrpPolicyAppoint(
                    final ScPtValidateContext context, final ScPolicyTransferVO vo, Map<String, Object> grpInfo) {
        final ScPtValidateResult result = new ScPtValidateResult();
        
        if (result.addIgnoreNull(this.validateScPolicyTransferVO(vo))) {
            return result;
        }
        
        if (!"true".equals(MapUtils.getString(grpInfo, "IS_ACTIVE_STATUS"))) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1262907")); //非有效保單不可移轉
            return result;
        }
        
        //團險不可同時轉領佣與服務
        if (vo.getNewCommRegisterId() != null && vo.getNewServiceRegisterId() != null
                        && !vo.getNewCommRegisterId().equals(vo.getNewServiceRegisterId())) {
            
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1262735"));
            return result;
        }
        
        if (vo.getNewCommRegisterId() != null && vo.getNewServiceRegisterId() == null) {
        	result.addIgnoreNull(ScPtMsgVO.errorMsg("不可只有轉出領佣權"));
        	return result;
        }
        
        final ScPolicyTransferCaseVO caseVO = context.getCaseVO(scPolicyTransferCaseService, vo.getCaseId());
        final AgentChlVO oldAgent = context.getInputAgent(agentScService, caseVO.getAgentId());
        
        
        AgentChlVO newServiceAgent = null;
		if (vo.getNewServiceRegisterId() != null) {
        	if (!vo.getNewServiceRegisterId().equals(caseVO.getAgentId())) {
	            final AgentChlVO newAgent = context.getInputAgent(agentScService, vo.getNewServiceRegisterId());
	            
	            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentCate(newAgent, false)));
	            
	            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentNotCoreFake(newAgent)));
	            result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, newAgent));
	            result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                        validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oldAgent, newAgent)));
	            result.addIgnoreNull(this.warnShareCommMerged(vo));
	            
	            newServiceAgent = newAgent;
	        }
        }
        
        AgentChlVO newCommAgent = null;
        if (vo.getNewCommRegisterId() != null) {
        	if (!vo.getNewCommRegisterId().equals(caseVO.getAgentId())) {
        		final AgentChlVO newAgent = context.getInputAgent(agentScService, vo.getNewCommRegisterId());
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentCate(newAgent, false)));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentNotCoreFake(newAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, newAgent));
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                        validateDirectorCfmDateAndProbationDate(vo.getNewStdDateDate(), oldAgent, newAgent)));
                result.addIgnoreNull(this.warnShareCommMerged(vo));
                
                newCommAgent = newAgent;
            }
        }
        
        result.addIgnoreNull(this.validateGrpOriAgent(oldAgent, newServiceAgent, newCommAgent));
        result.addIgnoreNull(validateOrphanIn(caseVO.getAcptReason(), oldAgent));
        result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(vo.getNewStdDateDate()));
        result.addIgnoreNull(warnCaseExists(vo.getPolicyCode(), caseVO.getCaseId()));
        result.addIgnoreNull(this.validateAgentDate(caseVO.getAcptReason(), oldAgent, vo.getNewStdDateDate()));
        result.addIgnoreNull(this.validateDismissChannel(caseVO.getAcptReason(), false));
        result.addIgnoreNull(this.validateTransferAlertVersion(caseVO.getAcptReason(), true));
        //團險不輸入日期，所以要自己造假
        result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                        vo.getNewServiceRegisterId() != null,
                        oldAgent, caseVO.getAcptReason(), 
                        vo.getPrintTransferNotify(), 
                        ORPHAN_OUT_REASON_31.equals(caseVO.getAcptReason()) ? "fake" : null, 
                        ORPHAN_OUT_REASON_31.equals(caseVO.getAcptReason()) ? "fake" : null));
        
        return result;
    }
    
	/**
     * <RULE_SET>
     * 	  	<RULE level="error">非有效保單不可移轉</RULE>
     * 	  	<RULE level="error">新服務業務員及新領佣業務員須為同一人</RULE>
     * 	  	<RULE level="error">不可只有轉出領佣權</RULE>
     * 	  	<RULE level="error">孤保轉出屬團險事業部權限無權執行</RULE>
     * </RULE_SET>
	 */
    @Override
    public ScPtValidateResult validateGrpUploadTransfer(
                    ScPtValidateContext context, GrpCommonPtFileVO row) {
        
        final ScPtValidateResult result = new ScPtValidateResult();
        

        if (StringUtils.isNotEmpty(row.getGrpAgentCate())) {
            final ChannelRole policyChannel = ChannelRole.getRoleFromAgentCate(row.getGrpAgentCate());
            
            if (policyChannel != null) {
              //沒權限舊不驗證其他東西
                if (result.addIgnoreNull(this.validateExecutorRight(
                                context.getUserChannelRole(), policyChannel.getChannelType()))) {
                    return result;
                }
            } else {
                result.addIgnoreNull(this.validateExecutorRight(context.getUserChannelRole(), null));
                return result;
            }
        }

        if (!row.getIsActiveState()) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1262907")); //非有效保單不可移轉
            return result;
        }
        
        final Date directorDate = CommonPtFileVO.parseDate(row.getUploadDirectorCfmDate());
        
        //類別為空白時表示100%業務員，因此可以直接取用主業務員。相反則是取轉出業務員欄位
        result.addIgnoreNull(this.validateDirectorCfmDateLargerThanSystemDate(directorDate));
        
        //團險不可同時轉領佣與服務
        if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()) && StringUtils.isNotEmpty(row.getNewCommRegisterCode())
                        && !StringUtils.equals(row.getNewCommRegisterCode(), row.getNewServiceRegisterCode1())) {
            
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1262735"));
            
            return result;
        } 
        
        if (StringUtils.isNotEmpty(row.getNewCommRegisterCode()) && StringUtils.isEmpty(row.getNewServiceRegisterCode1())) {
        	result.addIgnoreNull(ScPtMsgVO.errorMsg("不可只有轉出領佣權"));
        	return result;
        }
        
        AgentChlVO oldAgent = null;
        if (StringUtils.isNotEmpty(row.getFromAgentRegisterCode())) {
        	
        	if (!FakeAgent.isGrpFakeAgent(row.getFromAgentRegisterCode())) {
        		oldAgent = getGrpFromAgent(context, row.getPolicyCode(), row.getFromAgentRegisterCode(), false);
                
                if (oldAgent != null && validateGrpFromAgent(row, oldAgent)) {
                    
                    row.setFromAgentId(oldAgent.getAgentId());
                    row.setFromAgentCertiCode(oldAgent.getCertiCode());
                    
                    result.addIgnoreNull(ScPtMsgVO.decorator(ORI_AGENT_MSG_CODE, 
                                    this.validateName(oldAgent.getAgentName(), row.getFromAgentName())));
                    //轉出業務員是否存在於保單之中
                    
                    result.addIgnoreNull(this.validateAgentDate(row.getAcptReason(), oldAgent, directorDate));
                    
                    //團險不輸入日期，所以要自己造假
                    result.addIgnoreNull(this.validateOrphanFieldTrasnferOut(
                                    StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()),
                                    oldAgent, row.getAcptReason(), 
                                    Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert()), 
                                    ORPHAN_OUT_REASON_31.equals(row.getAcptReason()) ? "fake" : null, 
                                    ORPHAN_OUT_REASON_31.equals(row.getAcptReason()) ? "fake" : null));
                    
                    result.addIgnoreNull(validateOrphanIn(row.getAcptReason(), oldAgent));
                    
                    result.addIgnoreNull(this.validateDismissChannel(row.getAcptReason(), 
                    		ChannelRole.getRoleFromAgentCate(oldAgent.getAgentCate()).isExtenal()));
                    
                } else {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262297")); //非屬原業務員保單
                }
        	} else {
        		result.addIgnoreNull(ScPtMsgVO.errorMsg("屬團險事業部權限無權執行")); 
        	}
        }
        
        
        AgentChlVO newServiceAgent = null;
        AgentChlVO newCommAgent = null;
        //新服務業務員檢核
        if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1())) {
            /**
             * 限定僅內部通路業務員
             */
            final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewServiceRegisterCode1());
            
            if (newAgent == null) {
                if (!result.addIgnoreNull(
                                ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                                this.validateIsChannelCode(row.getNewServiceRegisterCode1())))) {
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                }
            } else {
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                                this.validateName(newAgent.getAgentName(), row.getNewServiceName1())));
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentCate(newAgent, false)));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, this.validateAgentNotCoreFake(newAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_SERVICE_MSG_CODE, newAgent));
                
                result.addIgnoreNull(this.warnShareServiceMerged(row));
                
                row.setNewServiceAgentCertiCode(newAgent.getCertiCode());
                row.setNewServiceAgentId(newAgent.getAgentId());
                newServiceAgent = newAgent;
            }
        }
        
        if (StringUtils.isNotEmpty(row.getNewCommRegisterCode())) {
            /**
             * 限定僅內部通路業務員
             */
            final AgentChlVO newAgent = context.getAgentByRegisterCode(agentQueryService, row.getNewCommRegisterCode());
            
            if (newAgent == null) {
                if (!result.addIgnoreNull(
                                ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                                this.validateIsChannelCode(row.getNewCommRegisterCode())))) {
                    result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, ScPtMsgVO.error("MSG_1256601"))); //新業務員不存在
                }
            } else {
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, 
                                this.validateName(newAgent.getAgentName(), row.getNewCommName())));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentCate(newAgent, false)));
                
                result.addIgnoreNull(ScPtMsgVO.decorator(NEW_COMM_MSG_CODE, this.validateAgentNotCoreFake(newAgent)));
                result.addIgnoreNull(this.validateAgent(context, NEW_COMM_MSG_CODE, newAgent));
                
                result.addIgnoreNull(this.warnShareCommMerged(row));
                
                row.setNewCommAgentId(newAgent.getAgentId());
                row.setNewCommAgentCertiCode(newAgent.getCertiCode());
                newCommAgent = newAgent;
            }
        }
        
        if (Constant.YES_NO_ARRAY_Y.equals(row.getIsPrintTransferAlert())) {
            //回任業務員不產生轉手通知書
            if (StringUtils.isNotEmpty(row.getNewServiceRegisterCode1()) && 
                            !StringUtils.equals(row.getFromAgentRegisterCode(), row.getNewServiceRegisterCode1())) {
                result.addIgnoreNull(this.validateTransferAlertVersion(row.getAcptReason(), true));
            } 
        }
        
        result.addIgnoreNull(warnCaseExists(row.getPolicyCode()));
        result.addIgnoreNull(this.validateGrpOriAgent(oldAgent, newServiceAgent, newCommAgent));
        
        
        return result;
    }
    
    /**
     * <p>Description : 根據各種邏輯來取得團險轉手的原業務員</p>
     * <p>1. 根據受理中取得</p>
     * <p>2. 如果是同類轉</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 23, 2017</p>
     * @param context
     * @param policyCode
     * @param registerCode
     * @return
     */
    private AgentChlVO getGrpFromAgent(final ScPtValidateContext context, 
                    final String policyCode, final String registerCode, boolean isLogicAgentReturn) {
        
        final Long agentId = scGrpTransferCaseService.getGrpAceptAgent(registerCode, policyCode);
        
        AgentChlVO agent = null;
        if (agentId != null) {
            agent =  context.getInputAgent(agentScService, agentId);
        } else {
            //如果是回任的轉手
            if (isLogicAgentReturn) {
                agent = context.getGrpFromAgentByRegisterCode(agentQueryService, registerCode);
            } else {
                agent = context.getAgentByRegisterCode(agentQueryService, registerCode);
            }
        }
        return agent;
    }
    
    
    /**
     * <p>Description : 檢核原業務員是否存在於保單</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 1, 2017</p>
     * @param row
     * @param oldAgent
     * @return
     */
    private boolean validateGrpFromAgent(GrpCommonPtFileVO row,
                    AgentChlVO oldAgent) {
        
        if (StringUtils.isNotBlank(row.getNewServiceRegisterCode1()) && StringUtils.isNotBlank(row.getNewCommRegisterCode())) {
            return (StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode1()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode2()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode3()) ) && (
                            StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode1()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode2()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode3()));
        } else if (StringUtils.isNotBlank(row.getNewServiceRegisterCode1())) {
            return StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode1()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode2()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getServiceCertiCode3());
        } else if (StringUtils.isNotBlank(row.getNewCommRegisterCode())) {
            return StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode1()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode2()) ||
                            StringUtils.equals(oldAgent.getCertiCode(), row.getCommCertiCode3());
        } else {
            return false;
        }
    }

    /**
     * <RULE_SET>
     * 	  	<RULE level="error">保單號碼不存在</RULE>
     * 	  	<RULE level="error">已由直營業務員服務無法移轉</RULE>
     * 	  	<RULE level="error">已由直營業務員服務無法移轉</RULE>
     * </RULE_SET>
     */
    @Override
    public ScPtValidateResult validateBRBDBatchUploadTransfer(final ScPtValidateContext context, BRBDCommonPtFileVO row) {
        
        final ScPtValidateResult result = new ScPtValidateResult();
        
        if (StringUtils.isBlank(row.getAgentCode()) && StringUtils.isBlank(row.getServiceChannelCode()) 
                        && StringUtils.isBlank(row.getPolicyCode())
                        && StringUtils.isBlank(row.getCommChannelCode())) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_910001171")); //資料錯誤
            return result;
        }
        
        //為了給直營轉入外部使用，跳過相關保單的檢核
        boolean isForceTransfer = MapUtils.getBooleanValue(context.getParameter(), "forceTransfer");
        
        final ContractMasterVO policy = contractMasterService.getPolicyByPolicyCode(row.getPolicyCode());
        
        if (policy == null) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1253498")); //policy not exists
            return result;
        }
        row.setPolicyId(policy.getPolicyId());
        
        if (result.addIgnoreNull(this.validateExecutorRight(context.getUserChannelRole(), policy.getChannelType().toString()))) {
            return result;
        }
        
        if (result.addIgnoreNull(this.validatePolicyStateTransferable(policy.getRiskStatus()))) {
        	return result;
        }
        
        if (result.addIgnoreNull(this.validatePolicyTransferedThisDay(policy.getPolicyId()))) {
            return result;
        }
        
        //服務與領佣須相同
        if (!isForceTransfer && result.addIgnoreNull(this.validatePolicyServiceCommCompanyEqual(policy.getPolicyId()))) {
            return result;
        }
        
        if (isForceTransfer) {
        	ProducerRole[] role = null;
            //轉出業務員是否存在於保單之中
            if (StringUtils.isNotEmpty(row.getServiceChannelCode()) && StringUtils.isNotEmpty(row.getCommChannelCode())) {
                role = new ProducerRole[]{ProducerRole.COMMISION, ProducerRole.SERVICE};
            } else if (StringUtils.isNotEmpty(row.getServiceChannelCode())) {
                role = new ProducerRole[]{ProducerRole.SERVICE};
            } else if (StringUtils.isNotEmpty(row.getCommChannelCode())) {
                role = new ProducerRole[]{ProducerRole.COMMISION};
            } else {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_242614")); //格試錯誤
            }
            
            if (policyProducerRoleService.checkPolicyContainAnyNonFakeAgent(policy.getPolicyId(), role)) {
            	result.addIgnoreNull(ScPtMsgVO.errorMsg("已由直營業務員服務無法移轉"));
            	return result;
            }
        }
        
        final ChannelOrgVO policyOrg = channelOrgService.getChannelOrg(policy.getChannelOrgId());
        ChannelOrgVO serviceOrg = policyOrg; //default as current policy channel
        
        
        if (StringUtils.isNotEmpty(row.getServiceChannelCode())) {
            
        	serviceOrg = channelOrgService.findByChannelCode(row.getServiceChannelCode(), 2);
            
            if (serviceOrg != null) {
            	if (!isForceTransfer && (serviceOrg.getParentId() == null || policyOrg.getParentId() == null ||
                                !policyOrg.getParentId().equals(serviceOrg.getParentId()))) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1256133", "MSG_1256128"));
                }
                row.setServiceChannelId(serviceOrg.getChannelId());
            } else {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1256133", "MSG_1256601"));
            }
        }
        
        ChannelOrgVO commOrg = null;
        if (StringUtils.isNotEmpty(row.getCommChannelCode())) {
            commOrg = channelOrgService.findByChannelCode(row.getCommChannelCode(), 2);
            
            if (commOrg != null) {
                if (!isForceTransfer && (commOrg.getParentId() == null || policyOrg.getParentId() == null ||
                                !policyOrg.getParentId().equals(commOrg.getParentId()))) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1256134", "MSG_1256128"));
                }
                row.setCommChannelId(commOrg.getChannelId());
            } else {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1256134", "MSG_1256601"));
            }
        }
        
        if (StringUtils.isNotEmpty(row.getAgentCode())) {
            final AgentChlVO agentChl = context.getAgentByRegisterCode(agentQueryService, row.getAgentCode());
            
            if (agentChl != null) {
                result.addIgnoreNull(this.validateAgentStatus(agentChl));
                
                result.addIgnoreNull(validateAgentTest(context, agentChl, NEW_SERVICE_MSG_CODE, policy.getPolicyId()));
                
                //serviceOrg nullable
                if (serviceOrg != null) { //如果服務單位不為null，這是因為上傳的服務單位可能是錯的
                	result.addIgnoreNull(ScPtMsgVO.decorator(NEW_SERVICE_MSG_CODE, 
                            this.warnExternalServiceAgentChannelOrg(serviceOrg, 
                            		channelOrgService.getChannelOrg(agentChl.getChannelOrgId()))));
                }
                
                row.setAgentId(agentChl.getAgentId());
            } else {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1256471")); //agent not exists
            }
        }
        return result;
    }
    
    @Override
	public ScPtValidateResult validatePolicyTransferedElsewhere(Long detailChangeId, Long lastChangeId) {
    	
    	final ScPtValidateResult result = new ScPtValidateResult();
    	
    	if (!ObjectUtils.equals(detailChangeId, lastChangeId)) {
    		result.addIgnoreNull(ScPtMsgVO.errorMsg("保單已執行後續轉手，不可於此受理執行取消轉手"));
    	}
    	
		return result;
	}
    
    /**
     * 保費時計入帳日 > 最後轉手日則不能轉手
     * <RULE_SET>
     * 	  	<RULE level="error">系統轉置歷史資料不可取消</RULE>
     * 	  	<RULE level="error">保單已入帳不可轉手</RULE>
     * </RULE_SET>
     * return true if cancelable
     */
    @Override
    public ScPtValidateResult validatePolicyCancelable(long policyId) {
        ScPtValidateResult result = new ScPtValidateResult();
        
        PolicyLastTransferInfo lastChange = scPolicyProducerChgService.getPolicyLastTransferInfo(policyId);
        
        if (lastChange != null) {
            //DC導入資料不可取消轉手
            if (315 == lastChange.getInsertedBy().longValue()) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262638"));
            } else {
            	if (lastChange.isCommChange()) {
            		final Date lastFinishDate = lastChange.getChangeDate();
                    
                    final Date policyIncomeDate = contractMasterService.getLastPaidDate(policyId);
                    
                    if (policyIncomeDate != null && DateUtils.truncatedCompareTo(lastFinishDate, policyIncomeDate, Calendar.DATE) <= 0) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1261349")); //保費已入帳
                    } //else pass
            	}
            }
        } else {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1261348")); //尚未有轉手紀錄
        }
        return result;
    }
    
    /**
     * <RULE_SET>
     * 	  	<RULE level="error">保單尚未有轉手紀錄</RULE>
     * </RULE_SET>
     */
    @Override
    public ScPtValidateResult validatePrintTransferAlertAble(long policyId) {
        ScPtValidateResult result = new ScPtValidateResult();
        final ScPolicyProducerChgVO lastChange = 
                        scPolicyProducerChgService.getLastActiveChange(policyId);
        
        if (lastChange != null) {
            //get transfer reason
            //當是DC轉入的單的時候不檢核
            if (lastChange.getInsertedBy().longValue() != 315l) {
                result.addIgnoreNull(this.validateTransferAlertVersion(lastChange.getAcptReason(), false));
            }
        } else {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1261348")); //尚未有轉手紀錄
        }
        return result;
    }
    
    /**
     * <p>Description : 非有效或停校保單無法列印轉換通知書</p>
     * <RULE_SET>
     * 	  	<RULE level="error">非有效或停效保單無法列印轉換通知書</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 24, 2017</p>
     * @param liabilityState
     * @return
     */
    public ScPtMsgVO validatePolicyStatePrintTransferAlert(Integer liabilityState) {
        ScPtMsgVO msg = null;
        if (!new Integer(1).equals(liabilityState) && !new Integer(2).equals(liabilityState)) {
            msg = ScPtMsgVO.error("MSG_1262641");
        }
        return msg;
    }
    
    

    /**
     * <p>Description : 僅轉領佣權且要列印轉換通知書</p>
     * <RULE_SET>
     * 	  	<RULE level="error">未異動服務業務員無法列印轉換通知書</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 4, 2017</p>
     * @param hasService
     * @return
     */
    private ScPtMsgVO validateTransferAlertWithNoService() {
        return ScPtMsgVO.error("MSG_1262611");
    }
    
    @Override
    public ScPtMsgVO validateValidateLastTransferNoService(long policyId) {
        
        final PolicyLastTransferInfo policyLastTransferInfo = scPolicyProducerChgService.getPolicyLastTransferInfo(policyId);
        
        if (policyLastTransferInfo != null && !policyLastTransferInfo.isServiceChange()) {
            return this.validateTransferAlertWithNoService();
        }
        return null;
    }
    
    /**
     * <p>Description : 佣金筆利總和為1(100)</p>
     * <RULE_SET>
     * 	  	<RULE level="error">領佣比例總合須為100</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 17, 2016</p>
     * @param allRate
     * @return
     */
    @Override
    public ScPtMsgVO validateCommissionRateTotal(Collection<BigDecimal> allRate) {
        BigDecimal total = BigDecimal.ZERO;
        ScPtMsgVO msg = null;
        
        for (BigDecimal commRate : allRate) {
            total = total.add(commRate);
        }
        
        if (BigDecimal.ONE.compareTo(total) != 0) {
            msg = ScPtMsgVO.error("MSG_1261346"); //請檢視新領佣業務員或新佣金比率
        }
        
        return msg;
    }
    
    /**
     * <RULE_SET>
     * 	  	<RULE level="error">服務與領佣分支單位所屬經代公司不同</RULE>
     * </RULE_SET>
     */
    @Override
    public ScPtMsgVO validateChannelParent(ChannelOrgVO origOrg,
                    ChannelOrgVO destOrg) {
        ScPtMsgVO msg = null;
     // 須相同經代公司
        if (origOrg.getParentId() != null
                        && destOrg.getParentId() != null) {
            if (origOrg.getParentId().longValue() != destOrg
                            .getParentId()
                            .longValue()) {
                // 服務與領佣分支單位所屬經代公司不同
                msg = ScPtMsgVO.error("MSG_1256128");
            }
        } else {
            // 資料錯誤理論上這個不可能發生
            msg = ScPtMsgVO.error("MSG_1253495");
        }
        return msg;
    }
    
    /**
     * 未生校保單不可轉手
     * @return
     */
    private ScPtMsgVO validatePolicyStateTransferable(Integer liabilityState) {
    	ScPtMsgVO msg = null;
        if (new Integer(0).equals(liabilityState)) {
            msg = ScPtMsgVO.error("未生效保單不可轉手");
        }
        return msg;
    }
    
    /**
     * <p>Description : 原保單佣金選擇為X不可變更</p>
     * <RULE_SET>
     * 	  	<RULE level="error">原保單佣金選擇為X不可變更</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Apr 5, 2017</p>
     * @param oldCommonChoice
     * @param newCommonChoice
     * @return
     */
    private ScPtMsgVO validateCommonChoiceX(long policyId, AgentChlVO oriAgent, CommChoice newCommChoice) {
        ScPtMsgVO msg = null;
        
        if (newCommChoice != null && !CommChoice.X.equals(newCommChoice) && oriAgent != null) {
        	final Map<String, Object> commRole = policyProducerRoleService.getCommPolicyProducer(policyId, oriAgent.getAgentId());
        	
        	if (null != commRole  && !commRole.isEmpty()) {
        		final Integer oldCommChoice = MapUtils.getInteger(commRole, "COMMISSION_CHOICE");
            	
            	if (CommChoice.X.equals(CommChoice.fromCode(oldCommChoice))) {
            		msg = ScPtMsgVO.error("MSG_1261359"); // 原保單佣金選擇為X不可變更
                }
        	}        	
        }
        return msg;
    }
    
    /**
     * 修改時檢核佣金選則
     * @param lastTransInfo
     * @param newCommChoice
     * @return
     */
    private ScPtMsgVO validateCommonChoiceX(final PolicyLastTransferInfo lastTransInfo, CommChoice newCommChoice) {
    	ScPtMsgVO msg = null;
    	
    	if (newCommChoice != null && !CommChoice.X.equals(newCommChoice) && lastTransInfo != null && lastTransInfo.isLastCommoiceX()) {
    		msg = ScPtMsgVO.error("MSG_1261359"); // 原保單佣金選擇為X不可變更
    	}
    	
    	return msg;
    }
    
    /**
     * 檢核上傳檔案是否與最後轉手內容相同
     * @param lastTransInfo
     * @return
     */
    private ScPtMsgVO validateLastTransAgentInfo(final PolicyLastTransferInfo lastTransInfo, final SingleCommonPtFileVO row) {
    	ScPtMsgVO msg = null;
    	
    	if (lastTransInfo != null && !lastTransInfo.validateTransferInfo(row.getFromAgentRegisterCode(), 
    			row.getNewCommRegisterCode(), row.getNewServiceRegisterCode1())) {
    		msg = ScPtMsgVO.errorMsg("上傳業務員非前次上傳業務員");
    	}
    	
    	return msg;
    }
    
    /**
     * <p>Description : 相同領佣業務員不同佣金選擇無法合併</p>
     * <RULE_SET>
     * 	  	<RULE level="error">相同領佣業務員不同佣金選擇無法合併</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 25, 2017</p>
     * @param toAgentId
     * @param commChoice
     * @return
     */
//    private ScPtMsgVO validateMergeCommChoice(long policyId, long toAgentId, CommChoice commChoice) {
//        ScPtMsgVO msg = null;
//        
//        //為了節省效能如果確定是最高等的就不需要比了
//        if (!commChoice.isMax()) {
//            Map<String, Object> commRole = policyProducerRoleService.getCommPolicyProducer(policyId, toAgentId);
//            
//            if (commRole != null) {
//                final Integer oldCommChoice = MapUtils.getInteger(commRole, "COMMISSION_CHOICE");
//                
//                if (commChoice.compareLevel(CommChoice.fromCode(oldCommChoice)) < 0) {
//                    msg = ScPtMsgVO.error("MSG_1262644");
//                }
//            }
//        }
//        return msg;
//    }
    
    /**
     * <p>Description : 同一保單同一天只可轉手一次不然就是要去取消轉手</p>
     * <RULE_SET>
     * 	  	<RULE level="error">此保單今日已轉手</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Apr 6, 2017</p>
     * @param policyId
     * @return
     */
    private ScPtMsgVO validatePolicyTransferedThisDay(long policyId) {
        ScPtMsgVO msg = null;
        
        ScPolicyProducerChgVO lastActiveChange = scPolicyProducerChgService.getLastActiveChange(policyId);
        
        if (lastActiveChange != null && DateUtils.truncatedCompareTo(AppContext.getCurrentUserLocalTime(), 
                        lastActiveChange.getChangeDate(), Calendar.DATE) <= 0) {
            msg = ScPtMsgVO.error("MSG_1262213"); // 此保單今日已轉手
        }
        return msg;
    }
    
    /**
     * <p>Description : 檢核該保單欄位今日是否已轉手</p>
     * <RULE_SET>
     * 	  	<RULE level="error">此欄位位置今日已轉手</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Jul 31, 2017</p>
     * @param policyId
     * @param fromAgentId
     * @param producerRole
     * @return
     */
    private ScPtMsgVO validatePolicyAgentTransferedToday(long policyId, long fromAgentId, boolean transferService, boolean transferComm) {
        ScPtMsgVO msg = null;
        
        ProducerRole[] producerRole = null;
        if (transferService && transferComm) {
            producerRole = new ProducerRole[]{ProducerRole.SERVICE, ProducerRole.COMMISION};
        } else if (transferService) {
            producerRole = new ProducerRole[] {ProducerRole.SERVICE};
        } else if (transferComm) {
            producerRole = new ProducerRole[] {ProducerRole.COMMISION};
        } else {
            throw new IllegalArgumentException("service, comm can't both null");
        }
        
        if (policyProducerRoleService.checkPolicyAgentTransfered(policyId, fromAgentId, producerRole)) {
            msg = ScPtMsgVO.error("MSG_1262602"); //此欄位今日已轉手
        }
        
        return msg;
    }
    
    
    
    /**
     * <p>Description : 通路權限控管，無權更改</p>
     * <RULE_SET>
     * 	  	<RULE level="error">通路權限控管，無權執行</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Jan 26, 2017</p>
     * @param channelType
     * @return
     */
    private ScPtMsgVO validateExecutorRight(final UserScChannelRoleVO userChannelRole, 
                    final String channelType) {
        ScPtMsgVO msg = null;
        
        if (!userChannelRole.hasChannelTypeRight(channelType)) {
            msg = ScPtMsgVO.error("MSG_1261351");
        }
        
        return msg;
    }
    
    /**
     * <p>Description : by agent validate</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : May 31, 2017</p>
     * @param agentVO
     * @param prefix
     * @return
     */
    private ScPtValidateResult validateAgent(AgentChlVO agentVO) {
        final ScPtValidateResult result = new ScPtValidateResult();
        //normat agent
        if (this.isLegalAgent(agentVO)) {
            if (result.addIgnoreNull(this.validateAgentStatus(agentVO))) {
                return result;
            }    
//            if (result.addIgnoreNull(this.concateMsgIfNotNull(prefix,
//                            this.validateAgentRegisterCode(agentVO)))) {
//                return result;
//            }
            if (result.addIgnoreNull(
                            this.validateAgentCaseExists(agentVO.getAgentId()))) {
                return result;
            }
            if (result.addIgnoreNull(this.validateAgentPunished(agentVO.getAgentId()))) {
                return result;
            }
            result.addIgnoreNull(this.warnAgentGrade(agentVO));
        }
        return result;
    }
    /**
     * <p>Description : warp with context cache</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 30, 2017</p>
     * @param context
     * @param agentVO
     * @param prefix
     */
    private ScPtValidateResult validateAgent(ScPtValidateContext context, final String prefix, AgentChlVO agentVO) {
        
        ScPtValidateResult validateResult = context.getAgentValidateResult(prefix, agentVO.getAgentId());
        if (validateResult == null) {
            validateResult = ScPtMsgVO.decorator(prefix, this.validateAgent(agentVO));
            context.putAgentValidateResult(prefix, agentVO.getAgentId(), validateResult);
        } 
        return validateResult;
    }
    
    /**
     * <p>Description : wrap for service and comm</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param iter
     * @param prefix
     * @param checkDate
     * @param types
     * @return
     */
    private ScPtValidateResult validateAgent(final List<ScPtNewRoleVO> newAgentRoles, final String[] prefix) {
        final ScPtValidateResult result = new ScPtValidateResult();
        int i = 0;
        for  (final ScPtNewRoleVO vo : newAgentRoles) {
            final AgentChlVO agentVO = vo.getAgentChlVO(agentScService);
            result.addIgnoreNull(ScPtMsgVO.decorator(prefix[i], this.validateAgentNotGrpFake(agentVO)));
            result.addIgnoreNull(ScPtMsgVO.decorator(prefix[i], this.validateAgent(agentVO)));
            i++;
        }
        return result;
    }
    
    /**
     * <p>Description : 檢核業務員是否為直營或外部</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員需為外部通路業務員</RULE>
     * 	  	<RULE level="error">業務員非屬直營通路</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Jul 27, 2017</p>
     * @param agent
     * @param requireExternal
     * @return
     */
    private ScPtMsgVO validateAgentCate(AgentChlVO agent, boolean requireExternal) {
        ScPtMsgVO msg = null;
        
        ChannelRole role = ChannelRole.getRoleFromAgentCate(agent.getAgentCate());
        
        if (requireExternal != role.isExtenal()) {
            if (requireExternal) {
                msg = ScPtMsgVO.error("MSG_1262599"); //需為外部通路業務員
            } else {
                msg = ScPtMsgVO.error("MSG_1262673"); //非屬直營通路
            }
        }
        return msg;
    }
    
    /**
     * <p>Description : 業務人員須在職</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員為留停狀態</RULE>
     * 	  	<RULE level="error">業務員必須在職</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 28, 2016</p>
     * @param agentChl
     * @return
     */
    private ScPtMsgVO validateAgentStatus(AgentChlVO agentChl) {
        ScPtMsgVO msg = null;
        
        if (!new ActiveAgentStatusPredicdate().evaluate(agentChl)) { //不為在職
            
            if (new InforceAgentStatusPredicdate().evaluate(agentChl)) { //不為離職或未報到(就是表留停)
                msg = ScPtMsgVO.error("MSG_1262628"); //留停
            } else {
                msg = ScPtMsgVO.error("MSG_1261416"); //須在職
            }
        }
        return msg;
    }
    
    /**
     * <p>Description : 我也忘了未甚麼要加上受理原因判斷了</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員已受理取號</RULE>
     * 	  	<RULE level="warn">業務員已受理取號</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Mar 6, 2017</p>
     * @param agentId
     * @return
     */
    private ScPtMsgVO validateAgentCaseExists(long agentId) {
        ScPtMsgVO msg = null;
        
        final ScPolicyTransferCaseVO caseVO = scPolicyTransferCaseService.getUnArchiveCaseByAgentId(agentId);
        
        if (caseVO != null) {
            
            if (LEAVE_ACEPT_REASON.contains(caseVO.getAcptReason())) {
                msg = ScPtMsgVO.error("MSG_1262029"); //已受理取號
            } else {
                msg = ScPtMsgVO.warn("MSG_1262029"); //已受理取號
            }
        }
        return msg;
    }
    
    
    
    /**
     * <p>Description : wrap for service and comm</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param iter
     * @param prefix
     * @param checkDate
     * @param types
     * @return
     */
    private ScPtValidateResult validateAgentTest(ScPtValidateContext context,
                    final List<ScPtNewRoleVO> newAgentRoles, 
                    final String[] prefix, long policyId) {
        final ScPtValidateResult result = new ScPtValidateResult();
        int i = 0;
        for  (final ScPtNewRoleVO vo : newAgentRoles) {
            final AgentChlVO agentVO = vo.getAgentChlVO(agentScService);
            
            //normat agent
            //result.addIgnoreNull(this.validateAgentTest(context, agentVO, prefix[i], policyId));
            result.addIgnoreNull(this.validateAgentTest(context, agentVO));
            i++;
        }
        return result;
    }
    
    /**
     * <p>Description : wrap for service and comm</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param iter
     * @param prefix
     * @param checkDate
     * @param types
     * @return
     */
//    private ScPtValidateResult validateAgentTest(ScPtValidateContext context, final AgentChlVO agentVO, 
//                    final String prefix, long policyId) {
//        final ScPtValidateResult result = new ScPtValidateResult();
//
//        //normat agent
//        if (this.isLegalAgent(agentVO)) {
//            if (this.validateAgentStatus(agentVO) == null) {
//            	
//            	final Set<Integer> types = this.getQualificationTypes(context, policyId); //cached
//            	final Set<Integer> agentTypeSet = this.getAgentTestType(context, agentVO.getAgentId()); //cachced
//            	
//                for (Integer typeId : types) {
//                    result.addIgnoreNull(ScPtMsgVO.decorator(prefix,
//                                    this.validateAgentTest(agentTypeSet, typeId)));
//                }
//            }
//        }
//        
//        return result;
//    }
    
    
    /**
     * <p>Description : 外不通路新服務業務員與AGY通路新服務業務員才驗證資格</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員不具一般人身保險資格</RULE>
     * 	  	<RULE level="warn">業務員不具資格</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 15, 2016</p>
     * @param agent
     * @param checkDate
     * @param types
     * @return
     */
    private ScPtMsgVO validateAgentTest(Set<Integer> agentTypeSet, Integer typeId) {
        ScPtMsgVO msg = null;
        
        if (!agentTypeSet.contains(typeId)) {
            /**保單所屬類別(傳統/外幣/投資)證照登錄日>=審閱期>申請日，不符出錯誤訊息：<業務員>不具招攬該類商品執照**/
            
            final Map<String, String> parameter = new HashMap<String, String>();
            parameter.put("typeName", CodeTable.getCodeDesc("T_TEST_TYPE", String.valueOf(typeId)));
            
            //一般人身為錯誤、其他為警告。懶的改table，先hard code
            if (0 == typeId.intValue()) {
                msg = ScPtMsgVO.error("MSG_1262385", parameter);
            } else {
                msg = ScPtMsgVO.warn("MSG_1262385", parameter);
            }
        } 
        return msg;
    }
    
    /**
     * <p>Description : 業務員在懲處期間</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員尚在懲處期間</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Apr 12, 2017</p>
     * @param agentId
     * @return
     */
    private ScPtMsgVO validateAgentPunished(long agentId) {
        ScPtMsgVO msg = null;
        
        if (agentRewardPunishService.isAgentExternalPunished(agentId, AppContext.getCurrentUserLocalTime())) {
            msg = ScPtMsgVO.error("MSG_1262254"); //在懲處期間
        }
        
        return msg;
    }
    
    /**
     * <p>Description : 業務員不可為個險虛擬業務員</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員不得為個險虛擬業務員</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 12, 2017</p>
     * @param registerCode
     * @return
     */
    private ScPtMsgVO validateAgentNotCoreFake(AgentChlVO agent) {
        ScPtMsgVO msg = null;
        
        if (BusinessCate.isFakeBusinessCate(agent.getBusinessCate()) &&
                        FakeAgent.isCoreFakeAgent(agent.getRegisterCode())) {
            msg = ScPtMsgVO.error("MSG_1262864");
        }
        
        return msg;
    }
    
    /**
     * <p>Description : 業務員不得為團險虛擬業務員</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員不得為團險虛擬業務員</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 12, 2017</p>
     * @param registerCode
     * @return
     */
    private ScPtMsgVO validateAgentNotGrpFake(AgentChlVO agent) {
        ScPtMsgVO msg = null;
        
        if (BusinessCate.isFakeBusinessCate(agent.getBusinessCate()) &&
                        FakeAgent.isGrpFakeAgent(agent.getRegisterCode())) {
            msg = ScPtMsgVO.error("MSG_1262865");
        }
        
        return msg;
    }
    
    /**
     * <p>Description : 不可少未轉多位</p>
     * <RULE_SET>
     * 	  	<RULE level="error">領佣業務員不可少位轉多位業務員</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 17, 2016</p>
     * @param oriCommAgent Long not null
     * @param newCommAgent Long not null
     * @return
     */
    private <O, N> ScPtMsgVO validateAgentCount(Collection<O> oriAgent, Collection<N> newAgent) {
        ScPtMsgVO msg = null;
        
        if (newAgent.size() > oriAgent.size()) {
            msg = ScPtMsgVO.error("MSG_1261347");
        }
        
        return msg;
    }
    
    /**
     * <p>Description : 當受理原因如下：，實際轉手日須大於酬佣結算日及離職手續完成日</p>
     * <RULE_SET>
     * 	  	<RULE level="error">原業務員尚未完成離職手續</RULE>
     * 	  	<RULE level="error">實際轉手日須大於原業務員離職手續完成日</RULE>
     * 	  	<RULE level="error">原業務員尚未完成酬佣結算</RULE>
     * 	  	<RULE level="error">STD主管確認日須大於原業務員酬佣結算日</RULE>
     * 	  	<RULE level="error">實際轉手日須大於原業務員酬佣結算日</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Mar 6, 2017</p>
     * @param now
     * @param acptReason
     * @return
     */
    private ScPtValidateResult validateAgentDate(final String acptReason, final AgentChlVO agent, final Date directorCfmDate) {
        ScPtValidateResult result = new ScPtValidateResult();
        
        if (LEAVE_ACEPT_REASON.contains(acptReason)) {
            
            if (BusinessCate.isLegalPerson(agent.getBusinessCate())) {
                final Date now = DateUtils.truncate(AppContext.getCurrentUserLocalTime(), Calendar.DATE);
                
                if (agent.getRoutineCompleteDate() == null) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262274")); //業務員尚未完成離職手續
                } else {
                    if (now.compareTo(agent.getRoutineCompleteDate()) <= 0) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262340")); //實際轉手日須大於離職手續完成日
                    }
                }
                if (agent.getCommSettleDate() == null) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262273")); //業務員尚未完成酬佣結算
                } else {
                    if (directorCfmDate != null && directorCfmDate.compareTo(agent.getCommSettleDate()) <= 0) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1256949")); //STD主管確認日須大於酬佣結算日
                    }
                    if (now.compareTo(agent.getCommSettleDate()) <= 0) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262341")); //實際轉手日須大於酬佣結算日
                    }
                }
            }
        }
        return result;
    }
    
    /**
     * 團險轉出及轉入不可相同
     * <RULE_SET>
     * 	  	<RULE level="error">原業務員等於新業務員_無須執行轉手</RULE>
     * </RULE_SET>
     * @param oriAgent
     * @param newAgent
     * @return
     */
    @Override
    public ScPtMsgVO validateGrpOriAgent(AgentChlVO oriAgent, AgentChlVO newServiceAgent, AgentChlVO newCommAgent) {
    	ScPtMsgVO msg = null;
        
    	if (oriAgent != null) {
    		if (newServiceAgent != null) {
    			if (StringUtils.equals(oriAgent.getCertiCode(), newServiceAgent.getCertiCode())) {
                    msg = ScPtMsgVO.errorMsg("原業務員等於新業務員_無須執行轉手");
                }
    		} else if (newCommAgent != null) {
    			if (StringUtils.equals(oriAgent.getCertiCode(), newCommAgent.getCertiCode())) {
                    msg = ScPtMsgVO.errorMsg("原業務員等於新業務員_無須執行轉手");
                }
    		}
    		
    	}
    	return msg;
    }

    /**
     * <p>Description : 業務員需要是同時擁有服務權根佣金權且為100%</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param policyId
     * @param agentId
     * @return
     */
    private ScPtMsgVO validatePolicyAgentMustOwnAll(long policyId) {
        ScPtMsgVO msg = null;
        
        if (!policyProducerRoleService.isAgentOwnAll(policyId)) {
            msg = ScPtMsgVO.error("MSG_1261432"); //原業務員非同時為服務及100%領佣業務員
        }
        
        return msg;
    }
    

    /**
     * <p>Description : 保單服務與領佣須相同但不需100%</p>
     * <RULE_SET>
     * 	  	<RULE level="error">此保單服務與領佣公司不同不可移轉</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param policyId
     * @param agentId
     * @return
     */
    private ScPtMsgVO validatePolicyServiceCommCompanyEqual(long policyId) {
        ScPtMsgVO msg = null;
        
        if (policyProducerRoleService.isPolicyServiceCommCompanyDiff(policyId)) {
            msg = ScPtMsgVO.error("此保單服務與領佣公司不同不可移轉"); //此保單服務與領佣不同不可移轉
        }
        return msg;
    }
    
    
    
    
    /**
     * <p>Description : 驗證欄位輸入</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 24, 2016</p>
     * @param vo
     * @return
     */
    private ScPtValidateResult validateScPolicyTransferVO(ScPolicyTransferVO vo) {
        ScPtValidateResult result = new ScPtValidateResult();
        
        if (vo.getPolicyType() != null) {
            if (ScPtPolicyType.COMM_R_TRANS.equals(vo.getPolicyType()) && vo.getNewCommRegisterId() == null) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261306", "MSG_1261627"));
            } else if (ScPtPolicyType.SERVICE_R_TRANS.equals(vo.getPolicyType()) && vo.getNewServiceRegisterId() == null) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261308", "MSG_1261627"));
            } else {
                if (vo.getNewCommRegisterId() == null && vo.getNewServiceRegisterId() == null) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261308", "MSG_1261627"));
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1261306", "MSG_1261627"));
                }
            }
        } else {
            if (vo.getNewCommRegisterId() == null && vo.getNewServiceRegisterId() == null) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261308", "MSG_1261627"));
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1261306", "MSG_1261627"));
            }
        }
        
        
        //只有轉服務權的話可以不輸入佣金選擇
        if (vo.getNewCommRegisterId() != null && vo.getNewCommChoice() == null) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1255690", "MSG_1261627"));
        }
        if (vo.getNewStdDateDate() == null) {
            result.addIgnoreNull(ScPtMsgVO.error("MSG_1255687", "MSG_1261627"));
        }
//        if (StringUtils.isBlank(vo.getAcptReason())) {
//            result.addIgnoreNull(ScPtMsgVO.error("MSG_1255945", "MSG_910001171"));
//        }
        return result;
    }
    
    
    
    /**
     * <p>Description : 業務人員在職時：業務人員尚未登錄</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 28, 2016</p>
     * @param agentChl
     * @return
     */
//    private ScPtMsgVO validateAgentRegisterCode(AgentChlVO agentChl) {
//        ScPtMsgVO msg = null;
//        
//        if (StringUtils.isEmpty(agentChl.getRegisterCode())) {
//            msg = ScPtMsgVO.error("MSG_1261418");
//        }
//        return msg;
//    }

    /**
     * <p>Description : 三人合作件需至保單單筆查詢轉手作業處理</p>
     * <RULE_SET>
     * 	  	<RULE level="error">三人合作件需至保單單筆查詢轉手作業處理</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 22, 2016</p>
     * @param policyId
     * @return
     */
    private ScPtMsgVO validateShareCount(long policyId) {
        ScPtMsgVO msg = null;
        
        if (policyProducerRoleService.checkThreeRoleShareCase(policyId)) {
            msg = ScPtMsgVO.error("三人共享件請至保單單筆轉手作業執行");
        }
        
        return msg;
    }
    
    private ScPtMsgVO validateServiceShareLeaveAgent(long policyId, AgentChlVO newServiceAgent) {
    	ScPtMsgVO msg = null;
    	
    	if (newServiceAgent != null) {
    		if (BusinessCate.isFakeBusinessCate(newServiceAgent.getBusinessCate()) && 
    				!FakeAgent.isFakeFakeAgent(newServiceAgent.getRegisterCode())) {
	    		
	    		List<PolicyProducerRole> currentServiceRoles = 
	    				policyProducerRoleService.findActivePolicyProducerRole(policyId, null, ProducerRole.SERVICE);
	    		
	    		//validate if share case
	    		if (currentServiceRoles.size() == 2) {
	    			//要轉入的人在該保單下的role 
        			final Collection<PolicyProducerRole> anotherOldAgentRoles = CollectionUtils.selectRejected(currentServiceRoles, 
        					new ProducerIdPredicate(newServiceAgent.getAgentId()));
        	        //validate if it's not transfer to another agent
        	        if (!anotherOldAgentRoles.isEmpty()) {
        	        	
        	        	long otherAgentId = anotherOldAgentRoles.iterator().next().getProducerId();
        	        	
        	        	if (scPolicyTransferCaseService.isAlreadyAccpt(otherAgentId)) {
        	        		msg = ScPtMsgVO.warnMsg("共享服務業務員已存在受理，若為同日執行轉手須至保單單筆轉手作業執行");
        	        	} else {
            	        	final AgentChlVO agent = agentScService.load(anotherOldAgentRoles.iterator().next().getProducerId());
            	        	
            	        	if (new LeaveAgentStatusPredicdate().evaluate(agent)) {
            	        		msg = ScPtMsgVO.warnMsg("共享服務業務員皆已離職，若為同日執行轉手須至保單單筆轉手作業執行");
            	        	}        	        		
        	        	}
        	        }
	    		}
	    		
	    	}
    	}
    	return msg;
    }
    
    /**
     * <p>Description : 保單已轉手</p>
     * <RULE_SET>
     * 	  	<RULE level="error">保單已轉手</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 22, 2016</p>
     * @param policyId
     * @return
     */
    private ScPtMsgVO validatePolicyTransfered(long caseId, long policyId) {
        ScPtMsgVO msg = null;
        
        ScPtPoliciesDetailVO detail = 
                        scPtPoliciesDetailService.findPoliciesDetailByCaseIdAndPolicyId(caseId, policyId);
        
        if (detail != null && ScPtCaseStatus.TRANSFERED.getValue() == detail.getPolicyTransferStatus().intValue()) {
            msg = ScPtMsgVO.error("MSG_1261392");
        }
        return msg;
    }
    
   /**
     * <p>Description : 如果轉入不為虛擬業務員不可為零</p>
     * <RULE_SET>
     * 	  	<RULE level="error">新領佣業務員其佣金比率不可為0</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 17, 2016</p>
     * @param commRole
     * @return
     */
    private ScPtMsgVO validateCommRateZero(List<ScPtNewRoleVO> commRole) {
        ScPtMsgVO msg = null;
                        
        for (ScPtNewRoleVO role : commRole) {
            if (isLegalAgent(role.getAgentChlVO(agentScService))
                            && BigDecimal.ZERO.equals(role.getAssignRate())) {
                msg =  ScPtMsgVO.error("MSG_1261352");
                break;
            }
            
        }
        return msg;
    }
    
    
    
    /**
     * <p>Description : 回任才用的檢核，要排除不同業務員主管確認日>=業務員報聘日</p>
     * <RULE_SET>
     * 	  	<RULE level="error">回任業務員尚未報聘</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Sep 18, 2017</p>
     * @param directorCfmDate
     * @param oriAgent
     * @return
     */
    private ScPtMsgVO validateDirectorCfmDateAndProbationDate(final Date directorCfmDate, final AgentChlVO oriAgent, final AgentChlVO newAgent) {
        ScPtMsgVO msg = null;
        
        if (oriAgent == null || newAgent == null) {
            return null;
        }
        
        if (ObjectUtils.equals(oriAgent.getAgentId(), newAgent.getAgentId())) {
            return null;
        }
        //回任業務員才有意義
        if (StringUtils.equals(oriAgent.getCertiCode(), newAgent.getCertiCode()) && 
                        StringUtils.equals(oriAgent.getRegisterCode(), newAgent.getRegisterCode())) {
            if (directorCfmDate != null && (newAgent.getProbationDate() == null || directorCfmDate.compareTo(newAgent.getProbationDate()) < 0)) {
                msg = ScPtMsgVO.error("MSG_1262683");
            }
        }
        return msg;
    }
    
    /**
     * <p>Description : STD主管確認日小於上一次酬佣結算日</p>
     * <RULE_SET>
     * 	  	<RULE level="error">業務員尚未完成酬佣結算</RULE>
     * 		<RULE level="error">STD主管確認日須大於酬佣結算日</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 19, 2016</p>
     * @param directorCfmDate
     * @return
     */
    private ScPtMsgVO validateDirectorCfmDateAndCommSettleDate(final String acptReason, final Date directorCfmDate, 
                    final AgentChlVO agent) {
        ScPtMsgVO msg = null;
        
        if (LEAVE_ACEPT_REASON.contains(acptReason)) {
        	if (BusinessCate.isLegalPerson(agent.getBusinessCate())) {
        		if (agent.getCommSettleDate() == null) {
                    msg = ScPtMsgVO.error("MSG_1262273"); //業務員尚未完成酬佣結算
                } else {
                    if (directorCfmDate != null && directorCfmDate.compareTo(agent.getCommSettleDate()) <= 0) {
                        msg = ScPtMsgVO.error("MSG_1256949"); //STD主管確認日須大於酬佣結算日
                    } 
                }
        	}
        }
        return msg;
    }
    
    /**
     * <p>Description : 主管確認日需小於系統日</p>
     * <RULE_SET>
     * 	  <RULE level="error">主管確認日大於實際轉手日期</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Apr 28, 2017</p>
     * @param directorCfmDate
     * @return
     */
    private ScPtMsgVO validateDirectorCfmDateLargerThanSystemDate(final Date directorCfmDate) {
        ScPtMsgVO msg = null;
        
        if (directorCfmDate != null) {
            if (DateUtils.truncatedCompareTo(directorCfmDate, AppContext.getCurrentUserLocalTime(), Calendar.DATE) > 0) {
                msg = ScPtMsgVO.error("MSG_1261379"); //STD主管確認日大於實際轉手日期
            }
        }
        return msg;
    }
    
    /**
     * 約訪截止日邏輯檢核
     * <RULE_SET>
     * 	  <RULE level="error">拜會清單日不可小於系統日</RULE>
     * 	  <RULE level="error">約訪截止日不可小於系統日</RULE>
     * </RULE_SET>
     * @param visitingDate
     * @param visitDeadlineDate
     * @return
     */
    private ScPtValidateResult validateVisitDate(Date visitingDate, Date visitDeadlineDate) {
    	ScPtValidateResult result = new ScPtValidateResult();
        
    	final Date now = DateUtils.truncate(AppContext.getCurrentUserLocalTime(), Calendar.DATE);
    	
        if (visitingDate != null) {
        	if (visitingDate.compareTo(now) < 0) {
        		result.addIgnoreNull(ScPtMsgVO.errorMsg("拜會清單日不可小於系統日"));
        	}
        }
        if (visitDeadlineDate != null) {
        	if (visitDeadlineDate.compareTo(now) < 0) {
        		result.addIgnoreNull(ScPtMsgVO.errorMsg("約訪截止日不可小於系統日"));
        	}
        }
        return result;
    }
    
    /**
     * <p>Description : 轉入估保時原業務員不可為</p>
     * <RULE_SET>
     * 	  <RULE level="error">原服務業務員非一般業務員_原因碼錯誤</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 17, 2017</p>
     * @param acptReason
     * @param oriAgent
     * @return
     */
    private ScPtMsgVO validateOrphanIn(String acptReason, AgentChlVO oriAgent) {
        ScPtMsgVO msg = null;
        
        if (oriAgent != null && ORPHAN_IN_REASON_33.equals(acptReason)) {
            if (!BusinessCate.isLegalPerson(oriAgent.getBusinessCate())) {
                msg = ScPtMsgVO.error("原服務業務員非一般業務員_原因碼錯誤"); //原服務業務員非實際業務員_原因碼錯誤 
            }
        }
        
        return msg;
    }
    
    /**
     * 原因碼40(通路解散)
		轉出業務員＝原業務員＝外部通路
		→這原因碼只適用原業務員為外部通路不適用直營通路
     * <RULE_SET>
     * 	  <RULE level="error">原因碼40_原業務員須為外部通路</RULE>
     * </RULE_SET>
     * @param aceptReason
     * @param isExternal
     * @return
     */
    private ScPtMsgVO validateDismissChannel(String aceptReason, boolean isExternal) {
    	ScPtMsgVO msg = null;
    	
    	if (!isExternal && DISMISS_CHANNEL.equals(aceptReason)) {
    		msg = ScPtMsgVO.errorMsg("原因碼40_原業務員須為外部通路");
    	}
    	return msg;
    }
    
    /**
     * 某些作業是沒有拜會清單日或是約訪截止日的轉手作業。所以直接限制不可使用31
     * <RULE_SET>
     * 	  <RULE level="error">原因碼31_不適用本作業</RULE>
     * </RULE_SET>
     * @param acptReason
     * @return
     */
    private ScPtMsgVO validateAceptReasonOrphanOut(String acptReason) {
    	ScPtMsgVO msg = null;
    	if (ORPHAN_OUT_REASON_31.equals(acptReason)) {
    		msg = ScPtMsgVO.errorMsg("原因碼31_不適用本作業");
    	}
    	return msg;
    }
    
    /**
     * <p>Description : 受理原因為31時對應欄位檢核，轉換通知書、約訪截止日、拜會清單日必輸</p>
     * <RULE_SET>
     * 	  <RULE level="error">拜會清單日欄位不可空白</RULE>
     * 	  <RULE level="error">約訪截止日欄位不可空白</RULE>
     * 	  <RULE level="error">孤保約訪截止日、拜會清單日原因碼為31或34</RULE>
     * 	  <RULE level="error">原服務業務員非孤保業務員_原因碼錯誤</RULE>
     * 	  <RULE level="error">非異動服務業務員不適用孤保轉出原因碼</RULE>
     * </RULE_SET>	
     * <p>Created By : TGL155</p>
     * <p>Create Time : Apr 19, 2017</p>
     * @param acptReason
     * @param isPrintAlert
     * @param visitingDate
     * @param visitDeadlineDate
     * @return
     */
    private ScPtValidateResult validateOrphanFieldTrasnferOut(boolean isTransferService, AgentChlVO oriAgent, String acptReason, 
                    boolean isPrintAlert, 
                    Object visitingDate, Object visitDeadlineDate) {
        
        final ScPtValidateResult result = new ScPtValidateResult();
        
        final boolean hasVisitingDate = visitingDate != null && StringUtils.isNotEmpty(visitingDate.toString());
        final boolean hasDeadlineDate = visitDeadlineDate != null && StringUtils.isNotEmpty(visitDeadlineDate.toString());
        
        if (ORPHAN_ACEPT_REASON[0].contains(acptReason)) {
            if (ORPHAN_OUT_REASON_31.equals(acptReason)) {
                if (!hasVisitingDate) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262266")); //拜會清單日欄位不可空白
                }
                if (!hasDeadlineDate) {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262265")); //約訪截止日欄位不可空白
                }
            }
        } else {
            if (hasVisitingDate || hasDeadlineDate) {
                result.addIgnoreNull(ScPtMsgVO.error("MSG_1262752")); //孤保約訪截止日、拜會清單日原因碼為31或34
            }
        }
        
        if (ORPHAN_ACEPT_REASON[2].contains(acptReason)) {
            if (ORPHAN_OUT_REASON_34.equals(acptReason)) {
                if (hasVisitingDate || hasDeadlineDate) {
                    if (isTransferService) {
                        if (!BusinessCate.isFakePerson(oriAgent)) {
                            result.addIgnoreNull(ScPtMsgVO.error("MSG_1262753")); //原服務業務員非孤保業務員_原因碼錯誤
                        }
                    } else {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262755")); //非異動服務業務員不適用孤保轉出原因碼
                    }
                }
            } else {
                if (isTransferService) {
                    if (!BusinessCate.isFakePerson(oriAgent)) {
                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262753")); //原服務業務員非孤保業務員_原因碼錯誤
                    }
                } else {
                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262755")); //非異動服務業務員不適用孤保轉出原因碼
                }
            }
        }
          
//        if (isTransferService) {
//            if (BusinessCate.isFakePerson(oriAgent)) {
//                if (ORPHAN_ACEPT_REASON[0].contains(acptReason)) {
//                    if (!isPrintAlert) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1261375")); //轉換通知書不可空白
//                    }
//                    if (!hasVisitingDate) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262266")); //拜會清單日欄位不可空白
//                    }
//                    if (!hasDeadlineDate) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262265")); //約訪截止日欄位不可空白
//                    }
//                } else {
//                    if (hasVisitingDate || hasDeadlineDate) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262752")); //孤保約訪截止日、拜會清單日原因碼為31或34
//                    }
//                }
//            } else {
//                if (ORPHAN_ACEPT_REASON[1].contains(acptReason) && (!hasVisitingDate && !hasDeadlineDate)) { //31,32
//                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262753")); //原服務業務員非孤保業務員_原因碼錯誤
//                } else if (ORPHAN_ACEPT_REASON[2].contains(acptReason) && (hasVisitingDate || hasDeadlineDate)) { //31,32,34
//                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262753")); //原服務業務員非孤保業務員_原因碼錯誤
//                    result.addIgnoreNull(ScPtMsgVO.error("MSG_1262754")); //原服務業務員非孤保業務員_約訪截止日、拜會清單日欄位不可有值
//                }
//            }
//        } else {
//            if (BusinessCate.isFakePerson(oriAgent)) {
//                if (ORPHAN_ACEPT_REASON[2].contains(acptReason)) {
//                    if (hasVisitingDate || hasDeadlineDate) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262755")); //非異動服務業務員不適用孤保轉出原因碼
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262756")); //非異動服務業務員約訪截止日、拜會清單日欄位不可有值
//                    } else {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262755")); //非異動服務業務員不適用孤保轉出原因碼
//                    }
//                } else {
//                    if (hasVisitingDate || hasDeadlineDate) {
//                        result.addIgnoreNull(ScPtMsgVO.error("MSG_1262756")); //非異動服務業務員約訪截止日、拜會清單日欄位不可有值
//                    }
//                }
//            }
//        }
        return result;
    }
    
    
    
    /**
     * <p>Description : 非屬直營通路</p>
     * <RULE_SET>
     * 	  <RULE level="error">新業務員非屬直營通路</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Sep 18, 2017</p>
     * @param channelCode
     * @return
     */
    private ScPtMsgVO validateIsChannelCode(String channelCode) {
        ScPtMsgVO msg = null;
        
        if (channelOrgService.findByChannelCode(channelCode) != null) {
            msg = ScPtMsgVO.error("MSG_1262673");
        }
        
        return msg;
    }
    
    /**
     * <p>Description : 轉換通知書是否套印版本</p>
     * <RULE_SET>
     * 	  <RULE level="error">轉換通知書未設定套印版本</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 4, 2017</p>
     * @param acptReason
     * @return
     */
    private ScPtMsgVO validateTransferAlertVersion(String acptReason, boolean isGrpPolicy) {
        ScPtMsgVO msg = null;
        
        if (acptReason == null || !scPtReportInfoService.checkTransferAlertDesc(acptReason, isGrpPolicy)) {
            msg = ScPtMsgVO.error("MSG_1261354");
        }
        return msg;
    }
    
    
    
    /**
     * <p>Description : 要保人錯誤</p>
     * <RULE_SET>
     * 	  <RULE level="error">要保人ID與原保單要保人ID不符</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Mar 13, 2017</p>
     * @param policyId
     * @param certiCode
     * @return
     */
    private ScPtMsgVO validatePolicyHolder(long policyId, String certiCode) {
        ScPtMsgVO msg = null;
        
//        if (!contractMasterService.validatePolicyHolder(policyId, certiCode)) {
            msg = ScPtMsgVO.error("MSG_1262205"); //要保人錯誤
//        }
        
        return msg;
    }

    /**
     * <p>Description : 被保人錯誤</p>
     * <RULE_SET>
     * 	  <RULE level="error">被保人ID與原保單被保人ID不符</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Mar 13, 2017</p>
     * @param policyId
     * @param certiCode
     * @return
     */
    private ScPtMsgVO validatePolicyInsuredList(long policyId, String certiCode) {
        ScPtMsgVO msg = null;
        
//        if (!contractMasterService.validatePolicyInsuredList(policyId, certiCode)) {
            msg = ScPtMsgVO.error("MSG_1262206"); // 被保人錯誤
//        }
        
        return msg;
    }
    
    
    
    /**
     * <p>Description : 異動生效日須在酬佣劾算年月之後</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Aug 17, 2017</p>
     * @param channelType
     * @return
     */
//    private ScPtMsgVO validatePersonnalInfoLock(int channelType) {
//        ScPtMsgVO msg = null;
//        boolean isLock = scPersonalInfoLockService.getLockWithEffectiveDate(channelType, 
//                        DateFormatUtils.format(AppContext.getCurrentUserLocalTime(), "yyyyMMdd"));
//        if (isLock) {
//            msg = ScPtMsgVO.error("MSG_1261013");
//        }
//        return msg;
//    }
    
    
    /**
     * <p>Description : 警告新職員職級為需為承攬至</p>
     * <RULE_SET>
     * 	  <RULE level="warn">新業務員職級為需為承攬制</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 24, 2016</p>
     * @param newAentId
     * @return
     */
    @Override
    public ScPtMsgVO warnAgentGrade(AgentChlVO newAgent) {
        ScPtMsgVO msg = null;
        
        //外部通路人員不會有職級
        if (newAgent.getAgentGrade() != null) {
            final AgentPositionVO agentGrade = agentScService.findAgentGradeById(newAgent.getAgentGrade());
            
            if (agentGrade != null && Constant.YES_NO_ARRAY_N.equals(agentGrade.getIsHireGrade())) {
                msg = ScPtMsgVO.warn("MSG_1261632");
            }
        }
        return msg;
    }
    
    /**
     * <p>Description : 非屬直營通路</p>
     * <RULE_SET>
     * 	  <RULE level="warn">原業務員非屬直營通路</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Sep 11, 2017</p>
     * @param agent
     * @return
     */
    private ScPtMsgVO warnAgentInternalChannel() {
        return ScPtMsgVO.warn("MSG_1262673");
    }
    
//    /**
//     * <p>Description : 原留停業務員尚未辦理復職或離職</p>
//     * <p>Created By : TGL155</p>
//     * <p>Create Time : Dec 26, 2016</p>
//     * @param policyId
//     * @param newServiceRegisterId
//     * @return
//     */
//    private ScPtMsgVO warnAgentRestNotYetReturn(long policyId,
//                    Long newServiceRegisterId) {
//        ScPtMsgVO msg = null;
//        if (scPtQueryService.checkAgentRestReturned(policyId, newServiceRegisterId)) {
//            msg = ScPtMsgVO.warn("MSG_1261435");
//        }
//        return msg;
//    }
//    
//    /**
//     * <p>Description : 原短期退休業務員尚未辦理回任或離職</p>
//     * <p>Created By : TGL155</p>
//     * <p>Create Time : Dec 26, 2016</p>
//     * @param policyId
//     * @param newServiceRegisterId
//     * @return
//     */
//    private ScPtMsgVO warnAgentLeaveNotYetReturn(long policyId,
//                    Long newServiceRegisterId) {
//        ScPtMsgVO msg = null;
//        if (scPtQueryService.checkAgentLeaveReturned(policyId, newServiceRegisterId)) {
//            msg = ScPtMsgVO.warn("MSG_1261436");
//        }
//        return msg;
//    }
    
    /**
     * <p>Description : 新外部經代服務業務員是否與服務分支相同</p>
     * <RULE_SET>
     * 	  <RULE level="warn">新外部經代服務業務員要與服務經代分支相同公司</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : May 19, 2017</p>
     * @param channelOrgId
     * @param agent
     * @return
     */
    private ScPtValidateResult warnExternalServiceAgentChannelOrg(ChannelOrgVO serviceRoleChannelOrg,
                    final List<ScPtNewRoleVO> newAgentRoles, final String[] prefix) {
        final ScPtValidateResult result = new ScPtValidateResult();
        int i = 0;
        for  (final ScPtNewRoleVO vo : newAgentRoles) {
            final AgentChlVO agentVO = vo.getAgentChlVO(agentScService);
            final ChannelOrgVO agentRoleChannelOrg = channelOrgService.getChannelOrg(agentVO.getChannelOrgId());
            result.addIgnoreNull(ScPtMsgVO.decorator(prefix[i], 
                                this.warnExternalServiceAgentChannelOrg(serviceRoleChannelOrg, 
                                                agentRoleChannelOrg))); //不存在於該保單的服務分支
            i++;
        }
        return result;
    }
    
    /**
     * <p>Description : 新外部經代服務業務員要與服務經代分支相同公司</p>
     * <br>不進行null check 如果是資料異常的時候會希望轉手失敗
     * <RULE_SET>
     * 	  <RULE level="warn">新外部經代服務業務員要與服務經代分支相同公司</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : May 19, 2017</p>
     * @param channelOrgId
     * @param agent
     * @return
     */
    private ScPtMsgVO warnExternalServiceAgentChannelOrg(ChannelOrgVO serviceRoleChannelOrg,
                    final ChannelOrgVO agentRoleChannelOrg) {
        ScPtMsgVO msg = null;
        
        if (!ObjectUtils.equals(serviceRoleChannelOrg.getParentId(), agentRoleChannelOrg.getParentId())) {
            msg = ScPtMsgVO.warn("MSG_1256128").append(" : " + agentRoleChannelOrg.getChannelCode()); //須相同經代公司
        }
        
        return msg;
    }
    
    /**
     * <RULE_SET>
     * 	  <RULE level="warn">未修改任何資料</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 7, 2016</p>
     * @param vo
     * @return
     */
    private ScPtMsgVO warnNothingChange(ScPtManyToManyVO vo) {
        ScPtMsgVO msg = null;
        
        if (vo.getNewCommAgent().size() > 0) {
            if (vo.getOriCommAgent().size() == vo.getNewCommAgent().size()) {
                for (int i = 0 ; i < vo.getOriCommAgent().size() ; i++) {
                    final ScPtNewRoleVO commNew = vo.getNewCommAgent().get(i);
                    if (commNew.getAgentId() != vo.getOriCommAgent().get(i).getAgentId() ||
                                    !commNew.getAssignRate().equals(vo.getOriCommAgent().get(i).getAssignRate()) ||
                                    !commNew.getCommChoice().equals(vo.getOriCommAgent().get(i).getCommChoice())) {
                        return msg;
                    }
                }
            } else {
                return msg;
            }
        }
        if (vo.getNewServiceAgent().size() > 0) {
            if (vo.getOriServiceAgent().size() == vo.getNewServiceAgent().size()) {
                for (int i = 0 ; i < vo.getOriServiceAgent().size() ; i++) {
                    final ScPtNewRoleVO serviceNew = vo.getNewServiceAgent().get(i);
                    if (serviceNew.getAgentId() != vo.getOriServiceAgent().get(i).getAgentId()) {
                        return msg;
                    }
                }
            } else {
                return msg;
            }
        }
        if (vo.getNewBrbdServiceAgent().size() > 0) {
            if (vo.getNewBrbdServiceAgent().size() == vo.getOriBrbdServiceAgent().size()) {
                for (int i = 0 ; i < vo.getNewBrbdServiceAgent().size() ; i++) {
                    final ScPtNewRoleVO brbdServiceNew = vo.getNewBrbdServiceAgent().get(i);
                    if (brbdServiceNew.getAgentId() != vo.getOriBrbdServiceAgent().get(i).getAgentId()) {
                        return msg;
                    }
                } 
            } else {
                return msg;
            }
        }
        return ScPtMsgVO.error("MSG_1261434");
    }
    
    /**
     * <RULE_SET>
     * 	  <RULE level="warn">未修改任何資料</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 22, 2016</p>
     * @param vo
     * @return
     */
    private ScPtMsgVO warnNothingChange(long policyServiceAgent, long newAgentId) {
        ScPtMsgVO msg = null;
        
        if (policyServiceAgent == newAgentId) {
            msg = ScPtMsgVO.error("MSG_1261434");
        }
        return msg;
    }
    
    /**
     * <RULE_SET>
     * 	  <RULE level="warn">服務業務員不應同時有虛擬業務員與一般業務員</RULE>
     * 	  <RULE level="warn">服務業務員不應同時有多個虛擬業務員</RULE>
     * </RULE_SET>
     * @param newServiceRole
     * @return
     */
    private ScPtMsgVO warnServiceAgentFakeExists(List<ScPtNewRoleVO> newServiceRole) {
    	ScPtMsgVO msg = null;
    	
    	if (newServiceRole.size() > 1) { //is share case
    		int fakeCount = 0;
    		int realCount = 0;
    		
    		for (ScPtNewRoleVO role : newServiceRole) {
    			final AgentChlVO agent = role.getAgentChlVO(agentScService);
    			if (BusinessCate.isFakePerson(agent)) {
    				fakeCount++;
    			} else if (BusinessCate.isLegalPerson(agent.getBusinessCate()) || FakeAgent.isFakeFakeAgent(agent.getRegisterCode())) {
    				realCount++;
    			}
    		}
    		
    		if (fakeCount > 0 && realCount > 0) {
    			msg = ScPtMsgVO.warn("MSG_1262955"); //不應為一虛一實
    		} else if (fakeCount > 1) {
    			msg = ScPtMsgVO.warn("MSG_1262954"); //不應為兩個虛擬業務員
    		}
    	}
    	
    	return msg;
    }
    
    /**
     * <RULE_SET>
     * 		<RULE level="warn">檢核保單是否存在尚未歸檔的受理案件，受理編號尚未歸檔</RULE>
     * </RULE_SET>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 1, 2016</p>
     * @param policyId
     * @return
     */
    private ScPtValidateResult warnCaseExist(long policyId, final Long caseId, final Date directorDate) {
        final ScPtValidateResult result = new ScPtValidateResult();
                        
        final List<Map<String, Object>> caseInfo = scPtQueryService.getUnArchiveCasePolicy(policyId);
        
        boolean checkDiffDirectorDate = true;
    	
        for (Map<String, Object> map : caseInfo) {
        	
        	//檢核保單主管確認日是否一致
        	if (directorDate != null && checkDiffDirectorDate) {
        		if (scPtPoliciesDetailService.containDifferentDirectorDate(MapUtils.getLong(map, "CASE_ID"), directorDate)) {
        			checkDiffDirectorDate = false;
        			
        			result.addIgnoreNull(ScPtMsgVO.warnMsg("主管確認日與受理案件中其他保單不同"));
        		}
        	}
        	
            if (caseId != null && caseId.equals(MapUtils.getLong(map, "CASE_ID"))) {
                continue;
            }
            final Map<String, String> parameter = new HashMap<String, String>();
            parameter.put("caseNo", ObjectUtils.toString(map.get("CASE_NO")));
            result.addIgnoreNull(ScPtMsgVO.warn("MSG_1261558", parameter));
        }
        
        return result;
    }
    
    /**
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 1, 2016</p>
     * @param policyId
     * @return
     */
    private ScPtValidateResult warnCaseExist(long policyId, final Date directorDate) {
        return this.warnCaseExist(policyId, null, directorDate);
    }
    
    /**
     * <RULE_SET>
     * 		<RULE level="warn">檢核保單是否存在尚未歸檔的受理案件，受理編號尚未歸檔</RULE>
     * </RULE_SET>
     * @param grpPolicyCode
     * @param caseId
     * @return
     */
    private ScPtValidateResult warnCaseExists(String grpPolicyCode, Long caseId) {
        ScPtValidateResult result = new ScPtValidateResult();
        
        final List<Map<String, Object>> caseInfo = scPtQueryService.getUnArchiveCaseGrpPolicy(grpPolicyCode);
        
        if (!caseInfo.isEmpty()) {
            for (Map<String, Object> map : caseInfo) {
            	
            	if (caseId != null && caseId.equals(MapUtils.getLong(map, "CASE_ID"))) {
                    continue;
                }
                final Map<String, String> parameter = new HashMap<String, String>();
                parameter.put("caseNo", ObjectUtils.toString(map.get("CASE_NO")));
                result.addIgnoreNull(ScPtMsgVO.warn("MSG_1261558", parameter));
            }
        }
        return result;
    }
    
    /**
     * 
     * 檢核保單是否存在尚未歸檔的受理案件，受理編號尚未歸檔
     * 
     */
    private ScPtValidateResult warnCaseExists(String grpPolicyCode) {
        return this.warnCaseExists(grpPolicyCode, null);
    }
    
    private ScPtMsgVO warnShareCommMerged(ScPolicyTransferVO vo) {
    	if (vo.isPolicyShareComm() && vo.getOriNewCommAgentId() != null) {
    		return ScPtMsgVO.warnMsg("轉入共享領佣業務員");
    	}
    	return null;
    }
    
    private ScPtMsgVO warnShareServiceMerged(ScPolicyTransferVO vo) {
    	if (vo.isPolicyShareService() && vo.getOriNewServiceAgentId() != null) {
    		return ScPtMsgVO.warnMsg("轉入共享服務業務員");
    	}
    	return null;
    }
    
    private ScPtMsgVO warnShareCommMerged(CommonPtFileVO vo) {
    	if (vo.validateTransferToCommShareCase()) {
    		return ScPtMsgVO.warnMsg("共享件應優先轉入共享領佣業務員");
    	}
    	return null;
    }
    
    private ScPtMsgVO warnShareServiceMerged(CommonPtFileVO vo) {
    	if (vo.validateTransferToServiceShareCase()) {
    		return ScPtMsgVO.warnMsg("共享件應優先轉入共享服務業務員");
    	}
    	return null;
    }
    
    private ScPtMsgVO warnNotShareCommMerged(CommonPtFileVO vo,boolean notShareCommStatus ) {
    	if (vo.validateTransferToNotCommShareCase( notShareCommStatus)) {
    		return ScPtMsgVO.warnMsg("非共享件應優先轉入服務業務員");
    	}
    	return null;
    }
    
    /**
     * <p>Description : 原業務員在職，佣金選擇為N,"", S時不可轉給虛擬業務員</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Nov 24, 2016</p>
     * @param oldAgentId
     * @param newAgentId
     * @param commChoice
     * @return
     */
//    private ScPtMsgVO warnFakeAgent(AgentChlVO oldAgent, AgentChlVO newAgent, Integer commChoice) {
//        ScPtMsgVO msg = null;
//        
//        //N, S
//        if (oldAgent != null && newAgent != null) {
//            if (commChoice == null || commChoice.intValue() == 6 || commChoice.intValue() == 4) {
//                //在職
//                if (oldAgent.getAgentStatus().intValue() == 0 && !isLegalAgent(newAgent)) {
//                    msg = ScPtMsgVO.warn("MSG_1261397");
//                }
//            }
//        }
//        return msg;
//    }
    
    
    /**
     * <p>Description : 判斷是否為虛擬</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 20, 2016</p>
     * @param agentChl
     * @return
     */
    private boolean isLegalAgent(AgentChlVO agentChl) {
        if (BusinessCate.isLegalPerson(agentChl.getBusinessCate())) {
            return true;
        }
        return false;
    }
    
    /**
     * <p>Description : copy from agentInfoRegisterCodeValidator 此保單需要的資格證種類</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Dec 6, 2016</p>
     * @param coverages
     * @param applyDate
     * @return
     */
    private Set<Integer> getQualificationTypes(ScPtValidateContext context, long policyId) {
    	
    	if (context.getPolicyTest(policyId) == null) {
    		context.cachePolicyTest(policyId, contractMasterService.getQualificationTypes(policyId));
    	}
    	return context.getPolicyTest(policyId);
    }
    
    /**
     * <p>Description : 取得業務員資格烈表(with context cache) </p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : May 31, 2017</p>
     * @param context
     * @param agentId
     * @return
     */
    private Set<Integer> getAgentTestType(ScPtValidateContext context, long agentId) {
        
        if (context.getAgentTest(agentId) == null) {
            context.cacheAgentTest(agentId, individualAgentService.getAgentValidateTestInfoTypeSet(agentId));
        }
        return context.getAgentTest(agentId);
    }

 
    
    /**
     * <p>Description : 轉手前與轉手後是否同一人</p>
     * <p>Created By : TGL155</p>
     * <p>Create Time : Sep 6, 2017</p>
     * @param oriAgent
     * @param newServiceAgent
     * @return
     */
    @Override
    public boolean agentPartyEquals(AgentChlVO oriAgent, AgentChlVO newAgent) {
        if (oriAgent != null && newAgent != null) {
            ObjectUtils.equals(oriAgent.getPartyId(), newAgent.getPartyId());
        }
        return false;
    }

	
}
