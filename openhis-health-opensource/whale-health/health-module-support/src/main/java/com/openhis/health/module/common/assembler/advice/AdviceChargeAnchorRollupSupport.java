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

package com.openhis.health.module.common.assembler.advice;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将就诊收费明细按「请求表 + 请求主键」归并（含发药/发材锚点映射），供护士站处置单等列表计费态展示。
 */
public final class AdviceChargeAnchorRollupSupport {

    private AdviceChargeAnchorRollupSupport() {
    }

    /**
     * 请求锚点下的收费聚合：归并后状态码与金额合计。
     */
    public static final class RequestAnchorChargeAggregation {
        private String chargeStatusEnum;
        private BigDecimal sumTotalPrice = BigDecimal.ZERO;

        public String getChargeStatusEnum() {
            return chargeStatusEnum;
        }

        public BigDecimal getSumTotalPrice() {
            return sumTotalPrice;
        }
    }

    /**
     * 按请求锚点构建收费聚合索引（药品/耗材含发放表映射，与医生站列表归并口径一致）。
     *
     * @param chargeItems                     就诊收费明细
     * @param medicationDispenseQueryService  发药 id → 药品申请 id
     * @param deviceDispenseQueryService      耗材发放 id → 耗材申请 id
     * @return 锚点键（serviceTable + SOH + serviceId）→ 聚合
     */
    public static Map<String, RequestAnchorChargeAggregation> buildRequestAnchorChargeIndex(
        List<ChargeItem> chargeItems,
        MedicationDispenseQueryService medicationDispenseQueryService,
        DeviceDispenseQueryService deviceDispenseQueryService
    ) {
        Map<String, RequestAnchorChargeAggregation> result = new HashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return result;
        }
        Map<Long, Long> medicationDispenseIdToRequestId =
            resolveMedicationDispenseIdToRequestId(chargeItems, medicationDispenseQueryService);
        Map<Long, Long> deviceDispenseIdToRequestId =
            resolveDeviceDispenseIdToRequestId(chargeItems, deviceDispenseQueryService);
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            String anchorTable = null;
            Long anchorId = null;
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_SERVICE_REQUEST;
                anchorId = chargeItem.getServiceId();
            } else if (TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = chargeItem.getServiceId();
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = medicationDispenseIdToRequestId.get(chargeItem.getServiceId());
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = medicationDispenseIdToRequestId.get(chargeItem.getDispenseId());
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = chargeItem.getServiceId();
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = deviceDispenseIdToRequestId.get(chargeItem.getServiceId());
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = deviceDispenseIdToRequestId.get(chargeItem.getDispenseId());
            }
            if (anchorTable == null || anchorId == null) {
                continue;
            }
            String key = buildChargeAnchorKey(anchorTable, anchorId);
            if (key == null) {
                continue;
            }
            RequestAnchorChargeAggregation agg = result.computeIfAbsent(key, k -> new RequestAnchorChargeAggregation());
            if (StringUtils.isNotBlank(chargeItem.getStatusEnum())) {
                agg.chargeStatusEnum = AdviceChargeDisplayStatusSupport.mergeChargeStatusForRollup(
                    agg.chargeStatusEnum, chargeItem.getStatusEnum());
            }
            BigDecimal piece = chargeItem.getTotalPrice();
            if (piece != null) {
                agg.sumTotalPrice = agg.sumTotalPrice.add(piece);
            }
        }
        return result;
    }

    /**
     * 构造与护士站处置单一致的收费锚点键。
     *
     * @param serviceTable 请求表持久化名
     * @param serviceId    请求主键
     * @return 锚点键；参数不完整时返回 null
     */
    public static String buildChargeAnchorKey(String serviceTable, Long serviceId) {
        if (serviceId == null || StringUtils.isBlank(serviceTable)) {
            return null;
        }
        return serviceTable.trim()
            + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR
            + serviceId;
    }

    private static Map<Long, Long> resolveMedicationDispenseIdToRequestId(
        List<ChargeItem> chargeItems,
        MedicationDispenseQueryService medicationDispenseQueryService
    ) {
        Set<Long> dispenseIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())
                && chargeItem.getServiceId() != null) {
                dispenseIds.add(chargeItem.getServiceId());
            }
            if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        if (dispenseIds.isEmpty()) {
            return new HashMap<>();
        }
        return medicationDispenseQueryService.mapMedicationDispenseIdsToMedReqIds(dispenseIds);
    }

    private static Map<Long, Long> resolveDeviceDispenseIdToRequestId(
        List<ChargeItem> chargeItems,
        DeviceDispenseQueryService deviceDispenseQueryService
    ) {
        Set<Long> dispenseIds = new HashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
                && chargeItem.getServiceId() != null) {
                dispenseIds.add(chargeItem.getServiceId());
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                dispenseIds.add(chargeItem.getDispenseId());
            }
        }
        if (dispenseIds.isEmpty()) {
            return new HashMap<>();
        }
        return deviceDispenseQueryService.mapDeviceDispenseIdsToDeviceReqIds(dispenseIds);
    }
}
