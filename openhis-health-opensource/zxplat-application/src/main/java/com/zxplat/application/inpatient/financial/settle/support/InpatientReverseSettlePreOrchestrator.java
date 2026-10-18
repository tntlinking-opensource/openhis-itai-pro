/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.zxplat.domain.share.constants.InpatientSettleConstants;
import com.zxplat.domain.share.constants.ServiceRequestCategoryConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.zxplat.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院反结 Pre 读侧与校验（AS-IP-FIN-004；ATD-07 内嵌反结 Pre 共用）。
 *
 * <p>仅封装 QueryService 读取与内存校验，不含 Gate/Cmd 写编排；收银台与护士站应用层各自编排，
 * 共用本类避免 Pre 口径漂移。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientReverseSettlePreOrchestrator {

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 出院类诊疗读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /** 收费明细读侧（反结再生 Pre 门禁）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 支付单绑定收费项逗号串 → ID 列表。
     *
     * @param raw {@code fin_payment_reconciliation.charge_item_ids}
     * @return 解析结果；blank 时空列表
     */
    public List<Long> parseBoundChargeItemIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isEmpty(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isEmpty(part)) {
                continue;
            }
            ids.add(Long.valueOf(part.trim()));
        }
        return ids;
    }

    /**
     * 解析就诊末次正向成功住院临床结算单 ID（召回内嵌反结 Pre，AS-IP-FIN-004 §3.4）。
     *
     * @param encounterId 就诊 ID
     * @return 支付单 ID；无正向 SUCCESS 单时 null
     */
    public Long resolveLastForwardSuccessInpatientClinicPaymentId(Long encounterId) {
        List<PaymentReconciliationQueryModel> payments =
            paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
        PaymentReconciliationQueryModel matched = null;
        // Pre：末次 bill_date 正向 SUCCESS 单（非逆向 relation 链）。
        for (PaymentReconciliationQueryModel payment : payments) {
            if (payment == null) {
                continue;
            }
            if (!PaymentKind.INPATIENT_CLINIC.equals(payment.getKindEnum())) {
                continue;
            }
            if (!PaymentStatus.SUCCESS.equals(payment.getStatusEnum())) {
                continue;
            }
            if (payment.getRelationId() != null) {
                continue;
            }
            if (matched == null) {
                matched = payment;
                continue;
            }
            LocalDateTime matchedBill = matched.getBillDate();
            LocalDateTime currentBill = payment.getBillDate();
            if (currentBill != null && matchedBill != null) {
                if (currentBill.isAfter(matchedBill)) {
                    matched = payment;
                } else if (currentBill.equals(matchedBill)
                    && payment.getId() != null
                    && matched.getId() != null
                    && payment.getId() > matched.getId()) {
                    matched = payment;
                }
            } else if (payment.getId() != null
                && matched.getId() != null
                && payment.getId() > matched.getId()) {
                matched = payment;
            }
        }
        if (matched == null) {
            return null;
        }
        return matched.getId();
    }

    /**
     * 解析就诊下可软删的医生开立出院嘱（category=34）主键（BR-IP-RVS-007）。
     *
     * <p>与 {@code ServiceRequest#assertReverseSettleDischargeSoftDeletable} 口径一致：
     * {@code DRAFT}/{@code ACTIVE}/{@code VERIFIED}/{@code COMPLETED}。
     * 反结须<strong>软删</strong>出院嘱（列表不再展示）并清空业务出院时间，医生重新开立；
     * 多条并存时取主键最大者（末次开立）。</p>
     *
     * @param encounterId 就诊 ID
     * @return 可软删的出院嘱 ID；无可软删时为 null
     */
    public Long resolveRemovableDischargeServiceRequestId(Long encounterId) {
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Long matchedId = null;
        for (ServiceRequestListModel row : serviceRequests) {
            if (!this.isDoctorDischargeServiceRequest(row)) {
                continue;
            }
            if (!this.isRemovableDischargeServiceRequestStatus(row.getStatusEnum())) {
                continue;
            }
            if (matchedId == null || (row.getId() != null && row.getId() > matchedId)) {
                matchedId = row.getId();
            }
        }
        return matchedId;
    }

    /**
     * 识别医生开立的出院类诊疗（{@code category=34} + 医生处方来源）。
     *
     * @param row 服务申请列表行；空则否
     * @return 是医生出院嘱时为 true
     */
    private boolean isDoctorDischargeServiceRequest(ServiceRequestListModel row) {
        if (row == null) {
            return false;
        }
        // 非出院类目不参与反结撤嘱
        if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_DISCHARGE.equals(row.getCategoryEnum())) {
            return false;
        }
        // 须医生开立，排除护士派生等非出院主嘱
        return GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum());
    }

    /**
     * 出院嘱是否处于反结可软删状态（对齐 {@code ServiceRequest#assertCancelable} 态集合）。
     *
     * @param statusEnum 请求状态 code
     * @return {@code DRAFT}/{@code ACTIVE}/{@code VERIFIED}/{@code COMPLETED} 时为 true
     */
    private boolean isRemovableDischargeServiceRequestStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        return RequestStatus.DRAFT.getCode().equals(statusEnum)
            || RequestStatus.ACTIVE.getCode().equals(statusEnum)
            || RequestStatus.VERIFIED.getCode().equals(statusEnum)
            || RequestStatus.COMPLETED.getCode().equals(statusEnum);
    }

    /**
     * 校验原单绑定收费项均为 SETTLED、归属本就诊且尚无有效反结再生行（反结 Pre，BR-IP-RVS-003 / BR-IP-RVS-011）。
     *
     * @param chargeItems  已批量加载的收费项
     * @param requestedIds 原单绑定 ID 列表
     * @param encounterId  就诊 ID
     * @throws BusinessException 缺失或非 SETTLED/非本就诊/已再生时抛出 EX-IP-RVS-002
     */
    public void assertAllChargeItemsSettledForReverse(
        List<ChargeItem> chargeItems,
        List<Long> requestedIds,
        Long encounterId) {
        Map<Long, ChargeItem> loadedById = new HashMap<>();
        if (chargeItems != null) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    loadedById.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        for (Long requestedId : requestedIds) {
            ChargeItem chargeItem = loadedById.get(requestedId);
            if (chargeItem == null) {
                // Pre：原单绑定 ID 在库中缺失，禁止部分反结。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_002,
                    "结算单数据已变化，请刷新");
            }
            if (!encounterId.equals(chargeItem.getEncounterId())) {
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_002,
                    "结算单数据已变化，请刷新");
            }
            if (!ChargeItemStatus.SETTLED.getCode().equals(chargeItem.getStatusEnum())) {
                // Pre：仅 SETTLED 源行可反结再生，历史快照须保持终态。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_002,
                    "结算单数据已变化，请刷新");
            }
        }
        if (chargeItemQueryService.existsActiveRegenerationForSourceChargeItemIds(encounterId, requestedIds)) {
            // Pre：源行已存在 PENDING_SETTLEMENT 再生行，禁止重复反结。
            throw new BusinessException(
                InpatientSettleConstants.EX_IP_RVS_002,
                "结算单数据已变化，请刷新");
        }
    }
}
