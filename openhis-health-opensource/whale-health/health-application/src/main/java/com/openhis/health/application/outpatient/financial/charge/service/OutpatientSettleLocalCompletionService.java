/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.charge.service;

import com.openhis.health.application.outpatient.financial.charge.mapper.OutpatientChargeMapper;
import com.openhis.health.application.outpatient.financial.charge.request.ConfirmOutpatientChargeSettlementRequest;
import com.openhis.health.application.outpatient.financial.charge.response.ConfirmOutpatientChargeSettlementResponse;
import com.openhis.health.domain.administration.chargeitem.command.BatchChargeItemIdsCommand;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.invoice.service.InvoiceDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.command.AbortPreSettlementBeforePaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.financial.paymentreconciliation.command.SettleOutpatientPaymentCommand;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentRecDetailQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationDomainService;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdispense.command.PromoteDispenseToPreparationCommand;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseDomainService;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseDomainService;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestDomainService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestDomainService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊结算协同单本地落账服务（2207 平台步骤成功后执行）。
 *
 * <p>职责：支付正式落账、收费项结算、医嘱状态同步、发票草稿与陈旧草稿清理；
 * 由 {@code OpSettleCoordinationExecutor} 在平台确认后回调。</p>
 */
@Service
@RequiredArgsConstructor
public class OutpatientSettleLocalCompletionService {

    private final PaymentReconciliationDomainService paymentReconciliationDomainService;
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final InvoiceDomainService invoiceDomainService;
    private final OutpatientChargeMapper outpatientChargeMapper;
    private final MedicationRequestDomainService medicationRequestDomainService;
    private final MedicationDispenseDomainService medicationDispenseDomainService;
    private final MedicationDispenseQueryService medicationDispenseQueryService;
    private final DeviceRequestDomainService deviceRequestDomainService;
    private final DeviceDispenseDomainService deviceDispenseDomainService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ServiceRequestDomainService serviceRequestDomainService;

    /** 门诊协同本地落账结果：支付单 ID 与发票草稿 ID。 */
    @Data
    public static class LocalCompletionResult {
        /** 正式结算后的支付单主键。 */
        private Long paymentId;
        /** 支付完成后创建的发票草稿主键。 */
        private Long invoiceId;
    }

    /**
     * 从结算确认请求执行本地落账（协同单 2207 成功后）。
     *
     * @param request       前端/快照还原的确认请求
     * @param operatorId    操作人主键
     * @param ybSettleIds   医保结算 ID 串（写入支付单）
     * @return 落账结果（paymentId、invoiceId）
     * @throws BusinessException 上下文缺失或草稿/明细非法时抛出
     */
    public LocalCompletionResult completeFromConfirmRequest(
        ConfirmOutpatientChargeSettlementRequest request,
        Long operatorId,
        String ybSettleIds) {
        // Gate：协同本地落账须具备确认请求、草稿 ID 与操作人。
        if (request == null || request.getPaymentDraftId() == null || operatorId == null) {
            throw new BusinessException(OutpatientChargeConstants.MESSAGE_CODE_SETTLE_DRAFT_INVALID, "协同单本地落账上下文缺失");
        }
        request.setYbSettleIds(ybSettleIds);
        // Pre：加载支付草稿、明细与待结算收费项实体。
        PaymentReconciliationQueryModel payment =
            paymentReconciliationQueryService.mustGetPayment(request.getPaymentDraftId());
        List<PaymentRecDetailQueryModel> details =
            paymentReconciliationQueryService.listPaymentRecDetailsByPaymentId(request.getPaymentDraftId());
        ChargeItemQueryCommand chargeItemQueryCommand = new ChargeItemQueryCommand();
        chargeItemQueryCommand.setIds(request.getChargeItemIds());
        List<ChargeItem> chargeItems = chargeItemQueryService.loadChargeItemsByIds(chargeItemQueryCommand);
        // Cmd：正式结算支付单并推进收费项至已结算。
        SettleOutpatientPaymentCommand settleCommand = outpatientChargeMapper.toSettleOutpatientPaymentCommand(request);
        this.attachDraftAccountsToPayReceiptDetails(settleCommand.getPayReceiptDetails(), details);
        Long paymentId = paymentReconciliationDomainService.settleOutpatientPayment(settleCommand);
        BatchChargeItemIdsCommand batchChargeItemIdsCommand =
            outpatientChargeMapper.toBatchChargeItemIdsCommand(request.getChargeItemIds(), operatorId);
        chargeItemDomainService.settleChargeItems(batchChargeItemIdsCommand);
        // Cmd：作废同就诊其他陈旧草稿，并同步医嘱/发药状态。
        this.cancelStaleOutpatientPaymentDrafts(request.getEncounterId(), null);
        this.syncAdviceStatesAfterChargeSettlement(chargeItems, operatorId);
        // Cmd：创建发票草稿。
        Long invoiceId = invoiceDomainService.createDraftInvoiceAfterPayment(
            outpatientChargeMapper.toCreateDraftInvoiceCommand(payment, paymentId, operatorId));
        // Asm：封装落账结果供协同单步骤回写。
        LocalCompletionResult result = new LocalCompletionResult();
        result.setPaymentId(paymentId);
        result.setInvoiceId(invoiceId);
        return result;
    }

    /**
     * 将草稿明细中的账户信息回填至结算收据行（前端未带 accountId 时）。
     *
     * @param receiptLines 结算收据明细命令行
     * @param draftDetails 支付草稿明细读模型
     * @throws BusinessException 混合账户草稿且前端未带 accountId 时抛出
     */
    private void attachDraftAccountsToPayReceiptDetails(
        List<PaymentDetailItemCommand> receiptLines,
        List<PaymentRecDetailQueryModel> draftDetails) {
        if (receiptLines == null || receiptLines.isEmpty() || draftDetails == null || draftDetails.isEmpty()) {
            return;
        }
        boolean anyMissingAccount =
            receiptLines.stream().anyMatch(line -> line != null && line.getAccountId() == null);
        if (!anyMissingAccount) {
            return;
        }
        // 单账户草稿：全部收据行回填同一账户。
        if (draftDetails.size() == 1) {
            PaymentRecDetailQueryModel draftRow = draftDetails.get(0);
            if (draftRow.getAccountId() == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
                    "支付草稿明细缺少账户标识");
            }
            for (PaymentDetailItemCommand line : receiptLines) {
                if (line == null) {
                    continue;
                }
                if (line.getAccountId() == null) {
                    line.setAccountId(draftRow.getAccountId());
                    line.setAccountCode(draftRow.getAccountCode());
                }
            }
            return;
        }
        // 多账户草稿须由前端逐行携带 accountId，否则无法安全落账。
        throw new BusinessException(
            ErrorCodeConstants.PAYMENT_RECONCILIATION_INVALID_ARGUMENT,
            "混合账户草稿须由前端携带 accountId");
    }

    /**
     * 作废同就诊下除保留项外的 DRAFT 支付草稿，防止重复结算。
     *
     * @param encounterId         门诊就诊 ID
     * @param retainDraftPaymentId 保留的草稿 ID（可为 null 表示全部作废）
     */
    private void cancelStaleOutpatientPaymentDrafts(Long encounterId, Long retainDraftPaymentId) {
        if (encounterId == null) {
            return;
        }
        List<PaymentReconciliationQueryModel> encounterPayments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        if (encounterPayments == null || encounterPayments.isEmpty()) {
            return;
        }
        for (PaymentReconciliationQueryModel row : encounterPayments) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!PaymentStatus.DRAFT.equals(row.getStatusEnum())) {
                continue;
            }
            if (retainDraftPaymentId != null && retainDraftPaymentId.equals(row.getId())) {
                continue;
            }
            AbortPreSettlementBeforePaymentCommand abortCommand = new AbortPreSettlementBeforePaymentCommand();
            abortCommand.setPaymentId(row.getId());
            paymentReconciliationDomainService.abortPreSettlementBeforePayment(abortCommand);
        }
    }

    /**
     * 结算成功后同步医嘱/发药/处置请求状态（按收费项 service_table 锚点反查）。
     *
     * @param settledChargeItems 已结算收费项列表
     * @param operatorId         操作人
     */
    private void syncAdviceStatesAfterChargeSettlement(List<ChargeItem> settledChargeItems, Long operatorId) {
        if (settledChargeItems == null || settledChargeItems.isEmpty()) {
            return;
        }
        Set<Long> medReqIds = new LinkedHashSet<>();
        List<Long> medicationDispenseAnchors = new ArrayList<>();
        Set<Long> deviceReqIds = new LinkedHashSet<>();
        List<Long> deviceDispenseAnchors = new ArrayList<>();
        Set<Long> serviceReqIds = new LinkedHashSet<>();
        // Pre：按 service_table 分类收集各域请求/发药锚点。
        for (ChargeItem ci : settledChargeItems) {
            if (ci == null || ci.getServiceId() == null || StringUtils.isBlank(ci.getServiceTable())) {
                continue;
            }
            String st = ci.getServiceTable();
            Long sid = ci.getServiceId();
            if (TableNameConstants.MED_MEDICATION_REQUEST.equals(st)) {
                medReqIds.add(sid);
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(st)) {
                medicationDispenseAnchors.add(sid);
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(st)) {
                deviceReqIds.add(sid);
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(st)) {
                deviceDispenseAnchors.add(sid);
            } else if (TableNameConstants.WKF_SERVICE_REQUEST.equals(st)) {
                serviceReqIds.add(sid);
            }
        }
        if (!medicationDispenseAnchors.isEmpty()) {
            Map<Long, Long> map =
                medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(medicationDispenseAnchors);
            medReqIds.addAll(map.values());
        }
        if (!deviceDispenseAnchors.isEmpty()) {
            Map<Long, Long> map = deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(deviceDispenseAnchors);
            deviceReqIds.addAll(map.values());
        }
        // Cmd：逐域推进完成态与发药准备态。
        for (Long medReqId : medReqIds) {
            medicationRequestDomainService.completeMedicationRequestAfterOutpatientCharge(medReqId, operatorId);
        }
        if (!medReqIds.isEmpty()) {
            PromoteDispenseToPreparationCommand promoteMedCmd = new PromoteDispenseToPreparationCommand();
            promoteMedCmd.setMedReqIds(new ArrayList<>(medReqIds));
            promoteMedCmd.setOperatorId(operatorId);
            medicationDispenseDomainService.promoteToPreparation(promoteMedCmd);
        }
        for (Long deviceReqId : deviceReqIds) {
            deviceRequestDomainService.completeDeviceRequestAfterOutpatientCharge(deviceReqId, operatorId);
        }
        deviceDispenseDomainService.promoteDraftDispensesForDeviceRequests(deviceReqIds, operatorId);
        for (Long serviceReqId : serviceReqIds) {
            serviceRequestDomainService.completeServiceRequestAfterOutpatientCharge(serviceReqId, operatorId);
        }
    }
}
