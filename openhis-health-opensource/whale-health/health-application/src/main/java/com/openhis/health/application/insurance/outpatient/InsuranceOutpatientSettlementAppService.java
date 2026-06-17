/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.outpatient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.insurance.coordination.dto.OutpatientOpSettleCoordinationSnapshot;
import com.openhis.health.application.insurance.coordination.worker.YbCoordinationWorker;
import com.openhis.health.application.insurance.outpatient.request.GetOpInsuranceSettleStatusRequest;
import com.openhis.health.application.insurance.outpatient.response.GetOpInsuranceSettleStatusResponse;
import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import com.openhis.health.application.outpatient.financial.charge.service.OutpatientSettleLocalCompletionService;
import com.openhis.health.domain.financial.paymentreconciliation.command.MarkOutpatientPaymentSettlingCommand;
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
 * 门诊医保结算协同单门面（OP_SETTLE，06-协同单与 Worker §3）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>Gate：支付草稿须为 DRAFT，方可启动协同单并推进 SETTLING。</li>
 *   <li>Cmd：创建 {@link YbCoordinationBizType#OP_SETTLE} 协同单并异步派发 Worker。</li>
 *   <li>Asm：轮询协同单状态与本地落账快照，供前端展示结算进度。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(name = "opSettleCoordinationExecutor")
public class InsuranceOutpatientSettlementAppService {

    private final YbCoordinationDomainService coordinationDomainService;
    private final YbCoordinationQueryService coordinationQueryService;
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private YbCoordinationWorker coordinationWorker;

    /**
     * 启动门诊医保结算协同单：支付草稿 DRAFT→SETTLING，创建 OP_SETTLE 协同单并异步派发 Worker。
     *
     * @param command 含支付草稿、就诊、收费项与确认结算请求上下文
     * @return 协同单主键、支付状态 SETTLING 与异步结算模式标识
     */
    @Transactional
    public StartOpInsuranceSettleResult startOutpatientSettle(StartOpInsuranceSettleCommand command) {
        // Gate：支付草稿须存在且为 DRAFT，否则不允许启动医保协同
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(command.getPaymentDraftId());
        // Gate：非草稿态表示已在结算中或已完成，拒绝重复启动
        if (!PaymentStatus.DRAFT.equals(payment.getStatusEnum())) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_003, "支付草稿状态非法，无法启动协同单");
        }
        // Cmd：支付草稿推进 SETTLING，锁定本地落账窗口
        MarkOutpatientPaymentSettlingCommand settlingCmd = new MarkOutpatientPaymentSettlingCommand();
        settlingCmd.setPaymentId(command.getPaymentDraftId());
        settlingCmd.setOperatorId(command.getOperatorId());
        paymentReconciliationDomainService.markOutpatientPaymentSettling(settlingCmd);
        // Pre：组装协同单请求快照，供 Worker 平台调用与本地落账回放
        OutpatientOpSettleCoordinationSnapshot snapshot = new OutpatientOpSettleCoordinationSnapshot();
        snapshot.setPaymentDraftId(command.getPaymentDraftId());
        snapshot.setEncounterId(command.getEncounterId());
        snapshot.setPatientId(command.getPatientId());
        snapshot.setOperatorId(command.getOperatorId());
        snapshot.setChargeItemIds(command.getChargeItemIds());
        snapshot.setChrgBchnos(command.getChrgBchnos());
        snapshot.setConfirmRequest(command.getConfirmRequest());
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "协同单上下文序列化失败");
        }
        // Cmd：创建 OP_SETTLE 协同单并持久化
        CreateYbCoordinationCommand createCmd = new CreateYbCoordinationCommand();
        createCmd.setBizType(YbCoordinationBizType.OP_SETTLE);
        createCmd.setSchemeCode(InsuranceSchemeCode.CHS_NATIONAL_JILIN);
        createCmd.setBizId(command.getPaymentDraftId());
        createCmd.setAccountId(command.getAccountId());
        createCmd.setContractId(command.getContractId());
        createCmd.setPaymentId(command.getPaymentDraftId());
        createCmd.setPaymentNo(payment.getPaymentNo());
        createCmd.setEncounterId(command.getEncounterId());
        createCmd.setPatientId(command.getPatientId());
        createCmd.setOperatorId(command.getOperatorId());
        createCmd.setPriority(YbCoordinationPriority.P0);
        createCmd.setBlocking(true);
        createCmd.setRequestSnapshot(snapshotJson);
        createCmd.setMsgid(this.buildMsgid(command.getPaymentDraftId()));
        YbCoordination coordination = coordinationDomainService.create(createCmd);
        // Cmd：Worker 可用时异步派发，避免阻塞 HTTP 线程
        if (coordinationWorker != null) {
            coordinationWorker.dispatchAsync(coordination.getId());
        }
        // Asm：装配启动响应，告知前端进入异步结算轮询
        StartOpInsuranceSettleResult response = new StartOpInsuranceSettleResult();
        response.setPaymentId(command.getPaymentDraftId());
        response.setPaymentStatus(PaymentStatus.SETTLING.getCode());
        response.setSettlementMode(InsuranceCoordinationConstants.SETTLEMENT_MODE_INSURANCE_ASYNC);
        response.setCoordinationIds(Collections.singletonList(coordination.getId()));
        return response;
    }

    /**
     * 查询门诊医保结算协同单进度与支付状态。
     *
     * @param request 含支付草稿主键
     * @return 协同单状态、医保结算号、本地落账发票及完成标识
     */
    public GetOpInsuranceSettleStatusResponse getOutpatientInsuranceSettleStatus(
        GetOpInsuranceSettleStatusRequest request) {
        // Pre：按支付单加载全部协同单，筛选 OP_SETTLE 最新一条
        List<YbCoordination> coordinations = coordinationQueryService.listByPaymentId(request.getPaymentId());
        YbCoordination latest = null;
        // Loop：遍历协同单列表，取首个 OP_SETTLE 业务类型记录
        for (YbCoordination row : coordinations) {
            if (row != null && YbCoordinationBizType.OP_SETTLE.getCode().equals(row.getBizType())) {
                latest = row;
                break;
            }
        }
        // Gate：未找到 OP_SETTLE 协同单时无法查询进度
        if (latest == null) {
            throw new BusinessException(InsuranceCoordinationConstants.INS_012, "未找到门诊结算协同单");
        }
        // Pre：加载支付单当前状态，与协同单状态一并返回
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentId());
        // Asm：装配状态响应
        GetOpInsuranceSettleStatusResponse response = new GetOpInsuranceSettleStatusResponse();
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
        // Asm：响应快照含本地落账结果时回填发票 id
        if (StringUtils.isNotBlank(latest.getResponseSnapshot())) {
            try {
                OutpatientSettleLocalCompletionService.LocalCompletionResult result =
                    objectMapper.readValue(
                        latest.getResponseSnapshot(),
                        OutpatientSettleLocalCompletionService.LocalCompletionResult.class);
                if (result != null) {
                    response.setInvoiceId(result.getInvoiceId());
                }
            } catch (Exception ignored) {
                // 非本地落账快照时忽略，不影响协同单状态展示
            }
        }
        return response;
    }

    private String buildMsgid(Long paymentDraftId) {
        return "OC" + paymentDraftId + System.currentTimeMillis();
    }

    /** 启动门诊医保结算协同单入参（应用层内部命令）。 */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    public static class StartOpInsuranceSettleCommand {
        /** 支付草稿主键 {@code fin_payment_reconciliation.id}。 */
        private Long paymentDraftId;
        /** 医保合同主键。 */
        private Long contractId;
        /** 医保账户主键。 */
        private Long accountId;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /** 操作员主键。 */
        private Long operatorId;
        /** 本次结算收费项 id 列表。 */
        private List<Long> chargeItemIds;
        /** 医保收费批次号列表。 */
        private List<String> chrgBchnos;
        /** 确认结算请求快照，供 Worker 本地落账回放。 */
        private ConfirmOutpatientChargeSettlementRequest confirmRequest;
    }

    /** 启动门诊医保结算协同单出参（应用层内部结果）。 */
    @lombok.Data
    @lombok.experimental.Accessors(chain = true)
    public static class StartOpInsuranceSettleResult {
        /** 支付草稿主键。 */
        private Long paymentId;
        /** 支付状态 code（启动后为 SETTLING）。 */
        private String paymentStatus;
        /** 结算模式（INSURANCE_ASYNC）。 */
        private String settlementMode;
        /** 创建的协同单 id 列表。 */
        private List<Long> coordinationIds;
    }
}
