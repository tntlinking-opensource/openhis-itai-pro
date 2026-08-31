/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PhysicalExamChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 体检正式结算 Gate：校验收费项 PLANNED 与服务申请锚点仍可收费。
 *
 * <p>与门诊 {@code OutpatientChargeSettlementGate} 分离：体检登记项目可为 DRAFT/ACTIVE，
 * 不要求药耗发放批号锁定。</p>
 */
@Component
@RequiredArgsConstructor
public class PhysicalExamChargeSettlementGate {

    private final ServiceRequestQueryService serviceRequestQueryService;

    /**
     * 正式结算前校验收费明细可结算。
     *
     * @param chargeItems 本次拟结算收费明细
     */
    public void assertChargeItemsSettleable(List<ChargeItem> chargeItems) {
        this.assertPlannedChargeItems(chargeItems);
        this.assertPeServiceRequestsChargeable(chargeItems);
    }

    private void assertPlannedChargeItems(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            if (!ChargeItemStatus.PLANNED.getCode().equals(chargeItem.getStatusEnum())) {
                throw this.adviceChangedException();
            }
        }
    }

    private void assertPeServiceRequestsChargeable(List<ChargeItem> chargeItems) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return;
        }
        Set<Long> serviceReqIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getServiceId() == null
                || StringUtils.isEmpty(chargeItem.getServiceTable())) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                serviceReqIds.add(chargeItem.getServiceId());
            }
        }
        if (serviceReqIds.isEmpty()) {
            return;
        }
        Map<Long, ServiceRequestListModel> byId = serviceRequestQueryService.mapListModelsByIds(serviceReqIds);
        for (Long serviceReqId : serviceReqIds) {
            ServiceRequestListModel request = byId.get(serviceReqId);
            if (request == null) {
                throw this.adviceChangedException();
            }
            String status = request.getStatusEnum();
            if (RequestStatus.CANCELLED.getCode().equals(status)
                || RequestStatus.STOPPED.getCode().equals(status)) {
                throw this.adviceChangedException();
            }
        }
    }

    private BusinessException adviceChangedException() {
        return new BusinessException(
            ErrorCodeConstants.CHARGE_ITEM_INVALID_STATUS,
            PhysicalExamChargeConstants.MESSAGE_CODE_SETTLE_ADVICE_CHANGED + ": 医嘱或费用已变更，请重新预结算");
    }
}
