/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.ATDManager.support;

import com.openhis.health.application.inpatient.nursestation.ATDManager.mapper.NurseStationATDManagerMapper;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.invoice.command.CancelInvoiceCommand;
import com.openhis.health.domain.administration.invoice.entity.Invoice;
import com.openhis.health.domain.administration.invoice.query.InvoiceQueryCommand;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.administration.invoice.service.InvoiceQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.command.CancelInpatientSettleAsRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.CompleteInpatientSettleCancelRefundCommand;
import com.openhis.health.domain.financial.paymentreconciliation.support.PaymentYbSettlementSupport;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.health.domain.workflow.servicerequest.command.RejectServiceRequestToDraftCommand;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.health.infrastructure.integration.einvoice.orchestration.BossElectronicInvoiceAfterCommitOrchestration;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.application.shared.validator.PatientEncounterConsistencyGate;
import com.openhis.health.application.inpatient.financial.settle.support.InpatientReverseSettlePreOrchestrator;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 出院召回内嵌反结写编排（AS-IP-NUR-002 ATD-07 步骤 6；护士站应用层独享）。
 *
 * <p>与收银台 {@code InpatientSettleAppService#reverseInpatientSettle} 编排序列对称，
 * 允许 {@code DISCHARGED} 态且不触发 COMPLETED 财务态回退；不跨 AS 抽取，Command 在本包内组装。</p>
 */
@Component
@RequiredArgsConstructor
public class RecallReverseSettleOrchestrator {

    /** 写路径操作人门控。 */
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /** 写路径校对人门控（撤销出院嘱）。 */

    /** 患者与就诊一致性门控。 */
    private final PatientEncounterConsistencyGate patientEncounterConsistencyGate;

    /** 就诊读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 收费明细写侧。 */
    private final ChargeItemDomainService chargeItemDomainService;

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 支付流水写侧。 */
    private final PaymentReconciliationDomainService paymentReconciliationDomainService;

    /** 发票写侧。 */
    private final InvoiceDomainService invoiceDomainService;

    /** 发票读侧。 */
    private final InvoiceQueryService invoiceQueryService;

    /** 出院类诊疗写侧。 */
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** ATD Mapper（出院嘱退回草稿）。 */
    private final NurseStationATDManagerMapper nurseStationATDManagerMapper;

    /** 反结 Pre 读侧与校验（module.querysupport，与收银台共用口径）。 */
    private final InpatientReverseSettlePreOrchestrator inpatientReverseSettlePreOrchestrator;

    private final BossElectronicInvoiceAfterCommitOrchestration bossElectronicInvoiceAfterCommitOrchestration;

    /**
     * 出院召回内嵌反结（ATD-07 步骤 6；允许 {@code DISCHARGED} 态）。
     *
     * @param encounterId 住院就诊 ID
     * @param patientId   患者 ID
     * @throws BusinessException EX-IP-ATD-502（无结算单或反结失败）、EX-IP-ATD-503（无出院嘱且非 DRAFT）
     */
    public void execute(Long encounterId, Long patientId) {
        // Pre：召回须先对末次正向出院全结单做对称反结。
        Long paymentId = inpatientReverseSettlePreOrchestrator.resolveLastForwardSuccessInpatientClinicPaymentId(encounterId);
        if (paymentId == null) {
            // Pre：无末次正向 SUCCESS 出院全结单，召回前置反结无法启动。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_502,
                "反结未成功，无法召回");
        }
        try {
            // Cmd：内嵌 FIN-004 对称反结（支付/ChargeItem/出院嘱/发票）。
            this.executeReverseSettleForRecall(paymentId, encounterId, patientId);
        } catch (BusinessException ex) {
            if (InpatientSettleConstants.EX_IP_RVS_004.equals(ex.getErrorCode())) {
                // 反结阶段无 VERIFIED 出院嘱，须映射 ATD-503。
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_ATD_503,
                    "未找到可撤回的出院医嘱");
            }
            // 内嵌反结其余失败统一映射 ATD-502。
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_ATD_502,
                "反结未成功，无法召回");
        }
    }

    /**
     * 召回场景对称反结内核（允许 {@code DISCHARGED}，不触发 COMPLETED 财务态回退）。
     *
     * @param paymentId   末次正向出院全结支付单 ID
     * @param encounterId 就诊 ID
     * @param patientId   患者 ID
     * @throws BusinessException EX-IP-RVS-001～005（由外层映射为 ATD-502/503）
     */
    private void executeReverseSettleForRecall(Long paymentId, Long encounterId, Long patientId) {
        // Gate：反结须已登录操作员与校对人（撤销出院嘱）。
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        Long performerCheckId = currentPractitionerResolver.requirePractitionerId(
            InpatientSettleConstants.EX_IP_RVS_001,
            "请先登录");
        LocalDateTime checkTime = LocalDateTime.now();
        // Pre：加载原正向成功结算单，非 SUCCESS 或非住院结算类型则拒绝。
        PaymentReconciliationQueryModel originalPayment =
            paymentReconciliationQueryService.mustGetPayment(paymentId);
        if (!PaymentStatus.SUCCESS.equals(originalPayment.getStatusEnum())) {
            // Pre：仅正向 SUCCESS 单可反结。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!PaymentKind.INPATIENT_CLINIC.equals(originalPayment.getKindEnum())) {
            // Pre：须为住院临床出院全结单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (originalPayment.getRelationId() != null) {
            // Pre：逆向单本身不可作为反结原单。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        if (!encounterId.equals(originalPayment.getEncounterId())
            || !patientId.equals(originalPayment.getPatientId())) {
            // Pre：支付单须与召回就诊/患者一致。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        EncounterDetailQueryModel encounter = encounterQueryService.mustGetEncounterOfClass(
            encounterId,
            EncounterClass.IMP.getCode(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "就诊记录不是住院类别，不能反结");
        patientEncounterConsistencyGate.requireRequestPatientMatchesEncounter(
            patientId,
            encounter.getPatientId(),
            InpatientSettleConstants.EX_IP_RVS_001,
            "患者标识与就诊不一致");
        // Pre：一笔结算仅允许一次反结，已存在逆向关联则拒绝。
        if (Boolean.TRUE.equals(
            paymentReconciliationQueryService.hasRefundByRelationId(paymentId).getExists())) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_001,
                "请刷新并重新选择结算单");
        }
        List<Long> paymentBoundChargeItemIds =
            inpatientReverseSettlePreOrchestrator.parseBoundChargeItemIds(originalPayment.getChargeItemIds());
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(paymentBoundChargeItemIds);
        List<ChargeItem> paymentBoundChargeItems = chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        // Pre：原单绑定费用须均为 SETTLED，否则数据已变化。
        inpatientReverseSettlePreOrchestrator.assertAllChargeItemsSettledForReverse(
            paymentBoundChargeItems, paymentBoundChargeItemIds, encounterId);
        // Pre：反结仅再生本支付单快照绑定的已结算行，历史 SETTLED 行保持终态供结算历史查阅（BR-IP-RVS-011）。
        List<Long> chargeItemIdsToRevert = paymentBoundChargeItemIds;
        // Pre：须存在可退回或已为草稿的医生开立出院嘱（category=34；BR-IP-RVS-007）。
        Long dischargeServiceRequestId =
            inpatientReverseSettlePreOrchestrator.resolveRejectableDischargeServiceRequestId(encounterId);
        if (dischargeServiceRequestId == null
            && !inpatientReverseSettlePreOrchestrator.hasDoctorDischargeRequestInDraft(encounterId)) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_004,
                "未找到可撤回的出院医嘱");
        }
        List<PaymentRecDetailQueryModel> paymentDetails =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(paymentId);
        CancelInpatientSettleAsRefundCommand cancelCommand =
            this.buildCancelInpatientSettleAsRefundCommand(originalPayment, paymentDetails, operatorId);
        try {
            // Cmd：新建逆向反结支付单（REFUND_PROCESSING）。
            Long refundPaymentId = paymentReconciliationDomainService.cancelInpatientSettleAsRefund(cancelCommand);
            if (!chargeItemIdsToRevert.isEmpty()) {
                BatchChargeItemIdsCommand revertChargeCmd =
                    this.buildBatchChargeItemIdsCommand(chargeItemIdsToRevert, operatorId);
                // Cmd：为本支付单快照内源 SETTLED 行批量插入 PENDING_SETTLEMENT 再生行（refund_id 链回源行）。
                chargeItemDomainService.revertInpatientSettledChargeItems(revertChargeCmd);
            }
            // Cmd：作废草稿或冲红已开具票据。
            this.voidInvoiceLinkedToPaymentOnCancel(paymentId, operatorId);
            if (dischargeServiceRequestId != null) {
                RejectServiceRequestToDraftCommand rejectCommand =
                    nurseStationATDManagerMapper.toRejectDischargeServiceRequestToDraftCommand(
                        dischargeServiceRequestId, operatorId, performerCheckId, checkTime);
                // Cmd：撤销出院嘱至草稿（召回后须重新开立/校对）；已为 DRAFT 时幂等跳过。
                serviceRequestDomainService.rejectServiceRequestToDraft(rejectCommand);
            }
            CompleteInpatientSettleCancelRefundCommand completeCommand =
                this.buildCompleteInpatientSettleCancelRefundCommand(paymentId, refundPaymentId, null);
            // Cmd：正向单标 REFUNDED、逆向单标 SUCCESS，闭环反结流水。
            paymentReconciliationDomainService.completeInpatientSettleCancelRefund(completeCommand);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_005,
                "反结失败，请以流水为准后重试");
        }
    }

    /**
     * 组装逆向反结支付单命令（对称复制正向明细，不含医保撤销快照）。
     */
    private CancelInpatientSettleAsRefundCommand buildCancelInpatientSettleAsRefundCommand(
        PaymentReconciliationQueryModel originalPayment,
        List<PaymentRecDetailQueryModel> paymentDetails,
        Long operatorId) {
        CancelInpatientSettleAsRefundCommand command = new CancelInpatientSettleAsRefundCommand();
        command.setRelationId(originalPayment.getId());
        command.setEncounterId(originalPayment.getEncounterId());
        command.setPatientId(originalPayment.getPatientId());
        command.setEntererId(operatorId);
        command.setTenderedAmount(originalPayment.getTenderedAmount());
        command.setDisplayAmount(originalPayment.getDisplayAmount());
        command.setChargeItemIds(originalPayment.getChargeItemIds());
        command.setKindEnum(originalPayment.getKindEnum());
        command.setPaymentEnum(PaymentType.UN_PAY.getCode());
        command.setPaymentDetails(this.buildRefundPaymentDetailItemCommands(paymentDetails));
        return command;
    }

    /**
     * 组装批量收费项反结再生命令（源 SETTLED 行主键列表）。
     */
    private BatchChargeItemIdsCommand buildBatchChargeItemIdsCommand(
        List<Long> chargeItemIds,
        Long operatorId) {
        BatchChargeItemIdsCommand command = new BatchChargeItemIdsCommand();
        command.setOperatorId(operatorId);
        List<BatchChargeItemIdsCommand.Item> items = new ArrayList<>();
        if (chargeItemIds != null) {
            // 遍历原单绑定 ID，跳过空值后逐条加入批量回退项。
            for (Long chargeItemId : chargeItemIds) {
                if (chargeItemId == null) {
                    continue;
                }
                BatchChargeItemIdsCommand.Item item = new BatchChargeItemIdsCommand.Item();
                item.setChargeItemId(chargeItemId);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 组装反结完成命令（关联原单与逆向单主键）。
     */
    private CompleteInpatientSettleCancelRefundCommand buildCompleteInpatientSettleCancelRefundCommand(
        Long originalPaymentId,
        Long reversePaymentId,
        String ybSetlId) {
        CompleteInpatientSettleCancelRefundCommand command = new CompleteInpatientSettleCancelRefundCommand();
        command.setOriginalPaymentId(originalPaymentId);
        command.setReversePaymentId(reversePaymentId);
        command.setYbSettlements(PaymentYbSettlementSupport.optionalConfirmedItem(ybSetlId));
        return command;
    }

    /**
     * 正向支付明细 → 逆向明细命令（保留 predecessor 链路与账户余额快照）。
     */
    private List<PaymentDetailItemCommand> buildRefundPaymentDetailItemCommands(
        List<PaymentRecDetailQueryModel> paymentDetails) {
        List<PaymentDetailItemCommand> items = new ArrayList<>();
        if (paymentDetails == null) {
            return items;
        }
        // 逐条复制正向明细字段，供逆向单对称落库。
        for (PaymentRecDetailQueryModel detail : paymentDetails) {
            if (detail == null) {
                continue;
            }
            PaymentDetailItemCommand item = new PaymentDetailItemCommand();
            item.setPredecessorId(detail.getId());
            item.setTargetEnum(detail.getTargetEnum());
            item.setAccountId(detail.getAccountId());
            item.setAccountCode(detail.getAccountCode());
            item.setPayEnum(detail.getPayEnum());
            item.setPayLevelEnum(detail.getPayLevelEnum());
            item.setAmount(detail.getAmount());
            item.setReturnAmount(detail.getReturnAmount());
            item.setChargeAmount(detail.getChargeAmount());
            item.setPayTransNo(detail.getPayTransNo());
            item.setPayTransText(detail.getPayTransText());
            item.setPayTransDate(detail.getPayTransDate());
            item.setBeforeBalance(detail.getBeforeBalance());
            item.setAfterBalance(detail.getAfterBalance());
            item.setResultEnum(detail.getResultEnum());
            items.add(item);
        }
        return items;
    }

    /**
     * 作废或冲红与结算单关联的发票（草稿 void / 已开具 writeoff+红字）。
     */
    private void voidInvoiceLinkedToPaymentOnCancel(Long paymentId, Long operatorId) {
        InvoiceQueryCommand queryCommand = new InvoiceQueryCommand();
        queryCommand.setPaymentId(paymentId);
        Invoice invoice = invoiceQueryService.getInvoiceByPaymentId(queryCommand);
        if (invoice == null || invoice.getId() == null) {
            // 无关联发票则跳过。
            return;
        }
        if (InvoiceStatus.CANCELLED.sameAs(invoice.getStatusEnum())) {
            // 已作废/冲红则幂等跳过。
            return;
        }
        CancelInvoiceCommand cancelCommand = new CancelInvoiceCommand();
        cancelCommand.setInvoiceId(invoice.getId());
        cancelCommand.setReason(InpatientSettleConstants.INVOICE_CANCEL_REASON_INPATIENT_SETTLE);
        cancelCommand.setOperatorId(operatorId);
        if (InvoiceStatus.DRAFT.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.voidDraftInvoice(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
            return;
        }
        if (InvoiceStatus.ISSUED.sameAs(invoice.getStatusEnum())) {
            invoiceDomainService.writeoffInvoiceAndCreateRedRecord(cancelCommand);
            bossElectronicInvoiceAfterCommitOrchestration.afterInvoiceCancelled(invoice.getId(), cancelCommand.getReason());
        }
    }
}
