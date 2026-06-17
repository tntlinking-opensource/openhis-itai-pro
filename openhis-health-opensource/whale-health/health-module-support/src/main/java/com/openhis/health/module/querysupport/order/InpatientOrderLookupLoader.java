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

package com.openhis.health.module.querysupport.order;

import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.health.domain.workflow.devicerequest.service.DeviceRequestQueryService;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 住院医嘱按类型路由查询（替代 {@code OrderInfoRMQueryService#get/mustGet*}）。
 */
@Component
@RequiredArgsConstructor
public class InpatientOrderLookupLoader {

    private final MedicationRequestQueryService medicationRequestQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final DeviceRequestQueryService deviceRequestQueryService;

    /**
     * 按医嘱类型与主键加载列表骨架（不存在或类型未知时为空）。
     *
     * @param orderType 读模型医嘱类型常量
     * @param orderId   对应 Request 主键
     * @return 骨架行，或 empty
     */
    public Optional<InpatientOrderListSkeleton> findByOrderTypeAndId(String orderType, Long orderId) {
        if (orderId == null || StringUtils.isBlank(orderType)) {
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
