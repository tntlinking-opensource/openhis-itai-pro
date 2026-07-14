/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.advice;

import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 住院医嘱按类型路由查询（替代 {@code OrderInfoRMQueryService#get/mustGet*}）。
 */
@Component
@RequiredArgsConstructor
public class InpatientOrderLookupLoader {

    /** 药品申请只读查询（按主键加载列表模型）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;
    /** 诊疗申请只读查询（按主键加载列表模型）。 */
    private final ServiceRequestQueryService serviceRequestQueryService;
    /** 耗材申请只读查询（按主键加载列表模型）。 */
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 按医嘱类型与主键加载列表骨架（不存在或类型未知时为空）。
     *
     * @param orderType 读模型医嘱类型常量
     * @param orderId   对应 Request 主键
     * @return 骨架行，或 empty
     */
    public Optional<InpatientOrderListSkeleton> findByOrderTypeAndId(String orderType, Long orderId) {
        if (orderId == null || StringUtils.isEmpty(orderType)) {
            return Optional.empty();
        }
        if (ReadModelOrderTypeConstants.MED_REQUEST.equals(orderType)) {
            return Optional.ofNullable(
                InpatientOrderListSkeleton.fromMedicationListModel(
                    medicationRequestQueryService.getListModelById(orderId)));
        }
        if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(orderType)) {
            return Optional.ofNullable(
                InpatientOrderListSkeleton.fromServiceListModel(
                    serviceRequestQueryService.getListModelById(orderId)));
        }
        if (ReadModelOrderTypeConstants.DEVICE_REQUEST.equals(orderType)) {
            return Optional.ofNullable(
                InpatientOrderListSkeleton.fromDeviceListModel(
                    deviceRequestQueryService.getListModelById(orderId)));
        }
        return Optional.empty();
    }

    /**
     * 按类型与主键加载骨架；不存在时抛业务异常。
     *
     * @param orderType 读模型医嘱类型常量
     * @param orderId   对应 Request 主键
     * @return 非空骨架
     */
    public InpatientOrderListSkeleton mustGetByOrderTypeAndId(String orderType, Long orderId) {
        return this.findByOrderTypeAndId(orderType, orderId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "医嘱记录不存在"));
    }

    /**
     * 解析医嘱所属就诊主键（护士站校对/计费等写路径 Pre 使用）。
     *
     * @param orderType 读模型医嘱类型常量
     * @param orderId   对应 Request 主键
     * @return 就诊主键
     */
    public Long resolveEncounterId(String orderType, Long orderId) {
        return this.mustGetByOrderTypeAndId(orderType, orderId).getEncounterId();
    }
}
