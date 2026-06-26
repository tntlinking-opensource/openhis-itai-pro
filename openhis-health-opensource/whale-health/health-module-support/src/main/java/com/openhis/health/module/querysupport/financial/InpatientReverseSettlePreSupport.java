/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.module.querysupport.financial;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.InpatientSettleConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
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
public class InpatientReverseSettlePreSupport {

    /** 支付流水读侧。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 出院类诊疗读侧。 */
    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 支付单绑定收费项逗号串 → ID 列表。
     *
     * @param raw {@code fin_payment_reconciliation.charge_item_ids}
     * @return 解析结果；blank 时空列表
     */
    public List<Long> parseBoundChargeItemIds(String raw) {
        List<Long> ids = new ArrayList<>();
        if (StringUtils.isBlank(raw)) {
            return ids;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            if (StringUtils.isBlank(part)) {
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
     * 解析就诊下可退回草稿的医生开立出院嘱（category=34）主键（BR-IP-RVS-007）。
     *
     * <p>与 {@code ServiceRequest#assertRejectable} 口径一致：{@code ACTIVE}/{@code VERIFIED}/{@code COMPLETED}。
     * 多条并存时取主键最大者（末次开立）。</p>
     *
     * @param encounterId 就诊 ID
     * @return 可退回的出院嘱 ID；不可退回时为 null
     */
    public Long resolveRejectableDischargeServiceRequestId(Long encounterId) {
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        Long matchedId = null;
        for (ServiceRequestListModel row : serviceRequests) {
            if (!this.isDoctorDischargeServiceRequest(row)) {
                continue;
            }
            if (!this.isRejectableDischargeServiceRequestStatus(row.getStatusEnum())) {
                continue;
            }
            if (matchedId == null || (row.getId() != null && row.getId() > matchedId)) {
                matchedId = row.getId();
            }
        }
        return matchedId;
    }

    /**
     * 就诊下是否存在已为草稿的医生开立出院嘱（反结 Cmd 可幂等跳过退回）。
     *
     * @param encounterId 就诊 ID
     * @return 存在 {@code DRAFT} 出院嘱时为 true
     */
    public boolean hasDoctorDischargeRequestInDraft(Long encounterId) {
        List<ServiceRequestListModel> serviceRequests =
            serviceRequestQueryService.listServiceRequestsByEncounter(encounterId);
        for (ServiceRequestListModel row : serviceRequests) {
            if (!this.isDoctorDischargeServiceRequest(row)) {
                continue;
            }
            if (RequestStatus.DRAFT.getCode().equals(row.getStatusEnum())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 医生开立出院类诊疗（category=34）识别。
     */
    private boolean isDoctorDischargeServiceRequest(ServiceRequestListModel row) {
        if (row == null) {
            return false;
        }
        if (!ServiceRequestCategoryConstants.SERVICE_CATEGORY_DISCHARGE.equals(row.getCategoryEnum())) {
            return false;
        }
        return GenerateSource.DOCTOR_PRESCRIPTION.getCode().equals(row.getGenerateSourceEnum());
    }

    /**
     * 出院嘱反结 Pre 可退回状态（对齐 {@code ServiceRequest#assertRejectable}）。
     */
    private boolean isRejectableDischargeServiceRequestStatus(String statusEnum) {
        if (statusEnum == null) {
            return false;
        }
        return RequestStatus.ACTIVE.getCode().equals(statusEnum)
            || RequestStatus.VERIFIED.getCode().equals(statusEnum)
            || RequestStatus.COMPLETED.getCode().equals(statusEnum);
    }

    /**
     * 校验原单绑定收费项均为 SETTLED 且归属本就诊（反结 Pre，BR-IP-RVS-003）。
     *
     * @param chargeItems  已批量加载的收费项
     * @param requestedIds 原单绑定 ID 列表
     * @param encounterId  就诊 ID
     * @throws BusinessException 缺失或非 SETTLED/非本就诊时抛出 EX-IP-RVS-002
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
                // Pre：仅 SETTLED 可回退为 PENDING_SETTLEMENT。
                throw new BusinessException(
                    InpatientSettleConstants.EX_IP_RVS_002,
                    "结算单数据已变化，请刷新");
            }
        }
    }
}
