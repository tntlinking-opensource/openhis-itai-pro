/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.inpatient.financial.settle.service.InpatientSettleLocalCompletionService;
import com.openhis.health.application.insurance.coordination.dto.InpatientIpSettleCoordinationSnapshot;
import com.openhis.health.application.insurance.coordination.worker.YbCoordinationWorker;
import com.openhis.health.application.insurance.inpatient.request.GetIpInsuranceSettleStatusRequest;
import com.openhis.health.application.insurance.inpatient.request.StartIpInsuranceSettleRequest;
import com.openhis.health.application.insurance.inpatient.response.GetIpInsuranceSettleStatusResponse;
import com.openhis.health.application.insurance.inpatient.response.StartIpInsuranceSettleResponse;
import com.openhis.health.domain.financial.paymentreconciliation.command.MarkInpatientPaymentSettlingCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.insurance.coordination.command.CreateYbCoordinationCommand;
import com.openhis.health.domain.insurance.coordination.entity.YbCoordination;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationDomainService;
import com.openhis.health.domain.insurance.coordination.service.YbCoordinationQueryService;
import com.openhis.health.domain.share.constants.InsuranceCoordinationConstants;
import com.openhis.health.domain.share.enums.InsuranceSchemeCode;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.YbCoordinationBizType;
import com.openhis.health.domain.share.enums.YbCoordinationPriority;
import com.openhis.health.domain.share.enums.YbCoordinationState;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 住院医保结算协同单门面（IP_SETTLE，06-协同单与 Worker §3）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：支付草稿须为 DRAFT，方可启动协同单并推进 SETTLING。</li>
 *   <li>Cmd：创建 {@link YbCoordinationBizType#IP_SETTLE} 协同单并异步派发 Worker。</li>
 *   <li>Asm：轮询协同单状态与本地落账快照，供前端展示结算进度。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = "ipSettleCoordinationExecutor")
public class InsuranceInpatientSettlementAppService {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationQueryService coordinationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private YbCoordinationWorker coordinationWorker;

    /**
     * 启动住院医保结算协同单：支付草稿 DRAFT→SETTLING，创建 IP_SETTLE 协同单并异步派发 Worker。
     *
     * @param request 含支付草稿、预结算、就诊与收费项上下文
     * @return 协同单主键、支付状态 SETTLING 与异步结算模式标识
     */
    @Transactional
    public StartIpInsuranceSettleResponse startInpatientSettle(StartIpInsuranceSettleRequest request) {
        // Gate：支付草稿须存在且为 DRAFT，否则不允许启动医保协同
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        // Gate：非草稿态表示已在结算中或已完成，拒绝重复启动
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_003, "支付草稿状态非法，无法启动协同单");
        }
        // Cmd：支付草稿推进 SETTLING，锁定本地落账窗口
        MarkInpatientPaymentSettlingCommand settlingCmd = new MarkInpatientPaymentSettlingCommand();
        settlingCmd.setPaymentId(request.getPaymentDraftId());
        settlingCmd.setOperatorId(request.getOperatorId());
        paymentReconciliationDomainService.markInpatientPaymentSettling(settlingCmd);
        // Pre：组装协同单请求快照，供 Worker 平台调用与本地落账回放
        InpatientIpSettleCoordinationSnapshot snapshot = new InpatientIpSettleCoordinationSnapshot();
        snapshot.setPaymentDraftId(request.getPaymentDraftId());
        snapshot.setPreSettleId(request.getPreSettleId());
        snapshot.setYbPreSettleId(request.getYbPreSettleId());
        snapshot.setEncounterId(request.getEncounterId());
        snapshot.setPatientId(request.getPatientId());
        snapshot.setOperatorId(request.getOperatorId());
        snapshot.setChargeItemIds(request.getChargeItemIds());
        snapshot.setDischargeFullSettle(request.getDischargeFullSettle());
        snapshot.setSettledBeforeCurrentBatch(request.getSettledBeforeCurrentBatch());
        snapshot.setBillDate(request.getBillDate());
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单上下文序列化失败");
        }
        // Cmd：创建 IP_SETTLE 协同单并持久化
        CreateYbCoordinationCommand createCmd = new CreateYbCoordinationCommand();
        createCmd.setBizType(YbCoordinationBizType.IP_SETTLE);
        createCmd.setSchemeCode(InsuranceSchemeCode.CHS_NATIONAL_JILIN);
        createCmd.setBizId(request.getPaymentDraftId());
        createCmd.setAccountId(request.getAccountId());
        createCmd.setContractId(request.getContractId());
        createCmd.setPaymentId(request.getPaymentDraftId());
        createCmd.setPaymentNo(payment.getPaymentNo());
        createCmd.setEncounterId(request.getEncounterId());
        createCmd.setPatientId(request.getPatientId());
        createCmd.setOperatorId(request.getOperatorId());
        createCmd.setPriority(YbCoordinationPriority.P0);
        createCmd.setBlocking(true);
        createCmd.setRequestSnapshot(snapshotJson);
        createCmd.setMsgid(this.buildMsgid(request.getPaymentDraftId()));
        YbCoordination coordination = coordinationDomainService.create(createCmd);
        // Cmd：Worker 可用时异步派发，避免阻塞 HTTP 线程
        if (coordinationWorker != null) {
            coordinationWorker.dispatchAsync(coordination.getId());
        }
        // Asm：装配启动响应，告知前端进入异步结算轮询
        StartIpInsuranceSettleResponse response = new StartIpInsuranceSettleResponse();
        response.setPaymentId(request.getPaymentDraftId());
        response.setPaymentStatus(PaymentStatus.SETTLING.getCode());
        response.setSettlementMode(InsuranceCoordinationConstants.SETTLEMENT_MODE_INSURANCE_ASYNC);
        response.setCoordinationIds(Collections.singletonList(coordination.getId()));
        return response;
    }

    /**
     * 查询住院医保结算协同单进度与支付状态。
     *
     * @param request 含支付草稿主键
     * @return 协同单状态、医保结算号、本地落账发票草稿及完成标识
     */
    public GetIpInsuranceSettleStatusResponse getInpatientInsuranceSettleStatus(
        GetIpInsuranceSettleStatusRequest request) {
        // Pre：按支付单加载全部协同单，筛选 IP_SETTLE 最新一条
        List<YbCoordination> coordinations = coordinationQueryService.listByPaymentId(request.getPaymentId());
        YbCoordination latest = null;
        // Loop：遍历协同单列表，取首个 IP_SETTLE 业务类型记录
        for (YbCoordination row : coordinations) {
            if (row != null && YbCoordinationBizType.IP_SETTLE.getCode().equals(row.getBizType())) {
                latest = row;
                break;
            }
        }
        // Gate：未找到 IP_SETTLE 协同单时无法查询进度
        if (latest == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "未找到住院结算协同单");
        }
        // Pre：加载支付单当前状态，与协同单状态一并返回
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
        // Asm：装配状态响应
        GetIpInsuranceSettleStatusResponse response = new GetIpInsuranceSettleStatusResponse();
        response.setCoordinationId(latest.getId());
        response.setPaymentId(request.getPaymentId());
        response.setState(latest.getState());
        String paymentStatusCode;
        if (payment.getStatusEnum() != null) {
            paymentStatusCode = payment.getStatusEnum().getCode();
        } else {
            paymentStatusCode = null;
        }
        response.setPaymentStatus(paymentStatusCode);
        response.setYbSetlId(latest.getPlatformSetlId());
        response.setErrorMsg(latest.getErrorMsg());
        response.setCompleted(YbCoordinationState.COMPLETED.getCode().equals(latest.getState()));
        // Asm：响应快照含本地落账结果时回填发票草稿 id
        if (StringUtils.isNotBlank(latest.getResponseSnapshot())) {
            try {
                InpatientSettleLocalCompletionService.LocalCompletionResult result =
                    objectMapper.readValue(
                        latest.getResponseSnapshot(),
                        InpatientSettleLocalCompletionService.LocalCompletionResult.class);
                if (result != null) {
                    response.setInvoiceDraftId(result.getInvoiceDraftId());
                }
            } catch (Exception ignored) {
                // 非本地落账快照时忽略，不影响协同单状态展示
            }
        }
        return response;
    }

    private String buildMsgid(Long paymentDraftId) {
        return "YC" + paymentDraftId + System.currentTimeMillis();
    }
}
