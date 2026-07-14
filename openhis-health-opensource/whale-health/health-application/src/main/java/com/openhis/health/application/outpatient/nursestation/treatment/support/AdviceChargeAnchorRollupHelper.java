/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.treatment.support;

import com.openhis.health.application.shared.advice.AdviceChargeDisplayStatusAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将就诊收费明细按「请求表 + 请求主键」归并（含发药/发材锚点映射），供护士站处置单等列表计费态展示。
 */
public final class AdviceChargeAnchorRollupHelper {

    private AdviceChargeAnchorRollupHelper() {
    }

    /**
     * 请求锚点下的收费聚合：归并后状态码与金额合计。
     */
    public static final class RequestAnchorChargeAggregation {
        /** 归并后收费状态码（多明细取展示归并规则）。 */
        private String chargeStatusEnum;
        /** 锚点下收费明细金额合计（元）。 */
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
        // Pre：批量解析发药/发材 id → 对应药品/耗材申请 id，供锚点回落使用
        Map<Long, Long> medicationDispenseIdToRequestId =
            resolveMedicationDispenseIdToRequestId(chargeItems, medicationDispenseQueryService);
        Map<Long, Long> deviceDispenseIdToRequestId =
            resolveDeviceDispenseIdToRequestId(chargeItems, deviceDispenseQueryService);
        // Loop：逐条收费明细归并到请求锚点（服务/药品/耗材申请）
        for (ChargeItem chargeItem : chargeItems) {
            if (Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            String anchorTable = null;
            Long anchorId = null;
            // 诊疗服务申请：锚点即 serviceTable + serviceId
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_SERVICE_REQUEST;
                anchorId = chargeItem.getServiceId();
            // 药品申请：锚点即药品申请主键
            } else if (TableNameConstants.MED_MEDICATION_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = chargeItem.getServiceId();
            // 发药单作 serviceTable：回落到对应药品申请
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = medicationDispenseIdToRequestId.get(chargeItem.getServiceId());
            // 发药单作 dispenseTable：以 dispenseId 映射药品申请
            } else if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                anchorTable = TableNameConstants.MED_MEDICATION_REQUEST;
                anchorId = medicationDispenseIdToRequestId.get(chargeItem.getDispenseId());
            // 耗材申请：锚点即耗材申请主键
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = chargeItem.getServiceId();
            // 耗材发放作 serviceTable：回落到对应耗材申请
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = deviceDispenseIdToRequestId.get(chargeItem.getServiceId());
            // 耗材发放作 dispenseTable：以 dispenseId 映射耗材申请
            } else if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && chargeItem.getDispenseId() != null) {
                anchorTable = TableNameConstants.WKF_DEVICE_REQUEST;
                anchorId = deviceDispenseIdToRequestId.get(chargeItem.getDispenseId());
            }
            // 无法解析锚点（未知 service/dispense 表组合）则跳过本条
            if (anchorTable == null || anchorId == null) {
                continue;
            }
            String key = buildChargeAnchorKey(anchorTable, anchorId);
            if (key == null) {
                continue;
            }
            RequestAnchorChargeAggregation agg = result.computeIfAbsent(key, k -> new RequestAnchorChargeAggregation());
            // 状态归并：同一锚点多条明细取护士站/医生站展示口径的合并态
            if (StringUtils.isNotEmpty(chargeItem.getStatusEnum())) {
                agg.chargeStatusEnum = AdviceChargeDisplayStatusAssembler.mergeChargeStatusForRollup(
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
        if (serviceId == null || StringUtils.isEmpty(serviceTable)) {
            return null;
        }
        return serviceTable.trim()
            + GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR
            + serviceId;
    }

    /**
     * 从 charge_item 行收集药品发放 id 并批量映射为 med_request id。
     *
     * @param chargeItems                    收费行
     * @param medicationDispenseQueryService 发放查询服务
     * @return dispenseId → medReqId
     */
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

    /**
     * 从 charge_item 行收集耗材发放 id 并批量映射为 device_request id。
     *
     * @param chargeItems                 收费行
     * @param deviceDispenseQueryService  发放查询服务
     * @return dispenseId → deviceReqId
     */
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
