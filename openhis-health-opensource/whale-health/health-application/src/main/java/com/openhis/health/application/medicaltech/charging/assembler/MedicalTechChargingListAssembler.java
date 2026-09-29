/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.charging.assembler;

import com.openhis.health.application.medicaltech.charging.dto.MedicalTechPricedDeviceRowDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ChargeItemActivityOperatorResolver;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.devicerequest.queries.model.DeviceRequestListModel;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * IF-MT-CHG-03：已划价耗材列表装配。
 */
@Component
@RequiredArgsConstructor
public class MedicalTechChargingListAssembler {

    /** 产品定义展示读模型批量装载（品名/编码）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 收费活动操作人：计费人回落 DeviceDispense 发放/配药人。 */
    private final ChargeItemActivityOperatorResolver chargeItemActivityOperatorResolver;

    /** 医务人员只读：确费人/计费人展示名。 */
    private final PractitionerQueryService practitionerQueryService;

    /**
     * 装配已划价耗材行（含确费人/计费人展示）。
     *
     * @param deviceRequests          已过滤的 nurse-pricing + 本科房请求
     * @param primaryDispenseIdByReq  请求 → 主发放 id
     * @param dispenseStatusByReq     请求 → 主发放状态
     * @param displayTimeByReq        请求 → 展示时刻
     * @param chargesByServiceId      请求 → 关联费用（可多行，取未冲销正向）
     * @return 列表行
     */
    public List<MedicalTechPricedDeviceRowDto> assemblePricedRows(
        List<DeviceRequestListModel> deviceRequests,
        Map<Long, Long> primaryDispenseIdByReq,
        Map<Long, String> dispenseStatusByReq,
        Map<Long, LocalDateTime> displayTimeByReq,
        Map<Long, List<ChargeItem>> chargesByServiceId) {
        List<MedicalTechPricedDeviceRowDto> rows = new ArrayList<>();
        if (deviceRequests == null || deviceRequests.isEmpty()) {
            return rows;
        }
        // Pre：批量产品名
        LinkedHashMap<String, ProductDefinitionBatchLookup> lookupByKey = new LinkedHashMap<>();
        for (DeviceRequestListModel req : deviceRequests) {
            if (req == null || req.getDeviceDefId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.ADM_DEVICE_DEFINITION, req.getDeviceDefId());
            if (!lookupByKey.containsKey(key)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
                lookup.setProductDefinitionId(req.getDeviceDefId());
                lookupByKey.put(key, lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookupByKey.values()));
        // Pre：已冲销源 id 集合（refund_id 指向的正向行）
        Set<Long> alreadyOffsetSourceIds = this.collectAlreadyOffsetSourceIds(chargesByServiceId);
        // Pre：主费用行索引，供确费戳与活动计费人批量解析
        List<ChargeItem> primaryCharges = new ArrayList<>();
        Map<Long, ChargeItem> primaryChargeByRequestId = new HashMap<>();
        for (DeviceRequestListModel req : deviceRequests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            List<ChargeItem> serviceCharges = null;
            if (chargesByServiceId != null) {
                serviceCharges = chargesByServiceId.get(req.getId());
            }
            ChargeItem primaryCharge = this.pickPrimaryOffsetableCharge(
                serviceCharges,
                alreadyOffsetSourceIds);
            if (primaryCharge != null) {
                primaryCharges.add(primaryCharge);
                primaryChargeByRequestId.put(req.getId(), primaryCharge);
            }
        }
        Map<Long, Long> activityOperatorByChargeItemId =
            this.chargeItemActivityOperatorResolver.mapOperatorPractitionerIdByChargeItemId(primaryCharges);
        LinkedHashSet<Long> practitionerIds = new LinkedHashSet<>();
        for (ChargeItem primaryCharge : primaryCharges) {
            if (primaryCharge.getFeeConfirmerId() != null) {
                practitionerIds.add(primaryCharge.getFeeConfirmerId());
            }
            Long activityId = activityOperatorByChargeItemId.get(primaryCharge.getId());
            if (activityId != null) {
                practitionerIds.add(activityId);
            }
        }
        for (DeviceRequestListModel req : deviceRequests) {
            if (req != null && req.getRequesterId() != null) {
                practitionerIds.add(req.getRequesterId());
            }
        }
        Map<Long, String> practitionerNameById =
            this.practitionerQueryService.mapDisplayNameByEntererIds(practitionerIds);
        for (DeviceRequestListModel req : deviceRequests) {
            if (req == null || req.getId() == null) {
                continue;
            }
            List<ChargeItem> serviceCharges = null;
            if (chargesByServiceId != null) {
                serviceCharges = chargesByServiceId.get(req.getId());
            }
            // 净额已冲销为 0：对齐护士 BR-IP-PRC-024，不进已划价列表
            if (this.isNursePricingFullyOffset(serviceCharges)) {
                continue;
            }
            MedicalTechPricedDeviceRowDto dto = new MedicalTechPricedDeviceRowDto();
            dto.setRequestId(req.getId());
            dto.setQuantity(req.getQuantity());
            Long locationId = req.getPerformLocation();
            if (locationId == null) {
                locationId = req.getLocationId();
            }
            dto.setLocationId(locationId);
            if (primaryDispenseIdByReq != null) {
                dto.setDispenseId(primaryDispenseIdByReq.get(req.getId()));
            }
            if (dispenseStatusByReq != null) {
                dto.setDispenseStatusEnum(dispenseStatusByReq.get(req.getId()));
            }
            if (displayTimeByReq != null) {
                dto.setPricedTime(displayTimeByReq.get(req.getId()));
            }
            // 回落请求开立时间
            if (dto.getPricedTime() == null) {
                dto.setPricedTime(req.getReqOpenedTime());
            }
            if (req.getDeviceDefId() != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.ADM_DEVICE_DEFINITION, req.getDeviceDefId());
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    dto.setItemName(product.getProductName());
                    dto.setItemCode(product.getProductBusNo());
                }
            }
            ChargeItem primaryCharge = primaryChargeByRequestId.get(req.getId());
            boolean cancellable = false;
            if (primaryCharge != null) {
                dto.setChargeItemId(primaryCharge.getId());
                dto.setChargeStatusEnum(primaryCharge.getStatusEnum());
                dto.setUnitPrice(primaryCharge.getUnitPrice());
                dto.setTotalPrice(primaryCharge.getTotalPrice());
                // 确费戳：与费用清单同源 fee_confirmer_*
                dto.setFeeConfirmerId(primaryCharge.getFeeConfirmerId());
                if (primaryCharge.getFeeConfirmerId() != null) {
                    dto.setFeeConfirmerName(practitionerNameById.get(primaryCharge.getFeeConfirmerId()));
                }
                // 待结算且未冲销可冲
                if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(primaryCharge.getStatusEnum())
                    && primaryCharge.getRefundId() == null
                    && !alreadyOffsetSourceIds.contains(primaryCharge.getId())) {
                    cancellable = true;
                }
            }
            // 计费人：优先划价开立人 requester_id；否则活动操作人（发放/配药）
            Long billingId = req.getRequesterId();
            if (billingId == null && primaryCharge != null) {
                billingId = activityOperatorByChargeItemId.get(primaryCharge.getId());
            }
            if (billingId != null) {
                dto.setBillingPractitionerId(billingId);
                dto.setBillingPractitionerName(practitionerNameById.get(billingId));
            }
            dto.setCancellable(Boolean.valueOf(cancellable));
            rows.add(dto);
        }
        return rows;
    }

    /**
     * 收集已被冲销行指向的正向费用 id。
     *
     * @param chargesByServiceId 费用分组
     * @return 已冲销源 id
     */
    private Set<Long> collectAlreadyOffsetSourceIds(Map<Long, List<ChargeItem>> chargesByServiceId) {
        Set<Long> alreadyOffsetSourceIds = new HashSet<>();
        if (chargesByServiceId == null) {
            return alreadyOffsetSourceIds;
        }
        for (List<ChargeItem> charges : chargesByServiceId.values()) {
            if (charges == null) {
                continue;
            }
            for (ChargeItem charge : charges) {
                if (charge == null || charge.getRefundId() == null) {
                    continue;
                }
                if (!ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(charge.getStatusEnum())) {
                    continue;
                }
                alreadyOffsetSourceIds.add(charge.getRefundId());
            }
        }
        return alreadyOffsetSourceIds;
    }

    /**
     * 选取主费用行：优先未冲销正向待结算；否则取首条。
     *
     * @param charges                锚点费用
     * @param alreadyOffsetSourceIds 已冲销源
     * @return 主费用；无则 null
     */
    private ChargeItem pickPrimaryOffsetableCharge(
        List<ChargeItem> charges,
        Set<Long> alreadyOffsetSourceIds) {
        if (charges == null || charges.isEmpty()) {
            return null;
        }
        ChargeItem first = null;
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getId() == null) {
                continue;
            }
            if (first == null) {
                first = charge;
            }
            if (charge.getRefundId() != null) {
                // 冲销负向行不作为主费用展示候选
                continue;
            }
            if (charge.getQuantityValue() != null && charge.getQuantityValue().signum() <= 0) {
                // 非正数量行跳过（冲销残留或异常）
                continue;
            }
            if (alreadyOffsetSourceIds != null && alreadyOffsetSourceIds.contains(charge.getId())) {
                // 已被负向行对冲的正向源
                continue;
            }
            return charge;
        }
        return first;
    }

    /**
     * 判定医技划价锚点费用是否已整行冲销（待结算 medical-tech-pricing / 过渡 nurse-pricing 净额为 0）。
     * <p>对齐护士站冲销后剔除：净额为 0 的行不进已划价列表。</p>
     *
     * @param charges 同锚点费用列表
     * @return true 表示已完全冲销
     */
    private boolean isNursePricingFullyOffset(List<ChargeItem> charges) {
        if (charges == null || charges.isEmpty()) {
            return false;
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        BigDecimal netTotal = BigDecimal.ZERO;
        boolean hasPricingPending = false;
        for (ChargeItem row : charges) {
            if (row == null) {
                continue;
            }
            if (!pendingCode.equals(row.getStatusEnum())) {
                continue;
            }
            if (!this.isMedicalTechPricingChargeSource(row.getGenerateSourceEnum())) {
                continue;
            }
            hasPricingPending = true;
            if (row.getTotalPrice() != null) {
                netTotal = netTotal.add(row.getTotalPrice());
            }
        }
        // 无医技划价待结算行则不按冲销剔除
        if (!hasPricingPending) {
            return false;
        }
        return netTotal.signum() == 0;
    }

    /**
     * 是否为医技划价费用来源（含过渡 nurse-pricing）。
     *
     * @param generateSourceEnum 生成来源
     * @return true 表示计入医技已划价净额
     */
    private boolean isMedicalTechPricingChargeSource(String generateSourceEnum) {
        if (generateSourceEnum == null) {
            return false;
        }
        if (GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(generateSourceEnum)) {
            return true;
        }
        return GenerateSource.NURSE_PRICING.getCode().equals(generateSourceEnum);
    }

    /**
     * 将费用列表按 serviceId 分组。
     *
     * @param charges 费用
     * @return serviceId → 费用列表
     */
    public Map<Long, List<ChargeItem>> groupChargesByServiceId(List<ChargeItem> charges) {
        Map<Long, List<ChargeItem>> byServiceId = new HashMap<>();
        if (charges == null) {
            return byServiceId;
        }
        for (ChargeItem charge : charges) {
            if (charge == null || charge.getServiceId() == null) {
                continue;
            }
            if (!TableNameConstants.WKF_DEVICE_REQUEST.equals(charge.getServiceTable())
                && StringUtils.isNotEmpty(charge.getServiceTable())) {
                continue;
            }
            List<ChargeItem> bucket = byServiceId.get(charge.getServiceId());
            if (bucket == null) {
                bucket = new ArrayList<>();
                byServiceId.put(charge.getServiceId(), bucket);
            }
            bucket.add(charge);
        }
        return byServiceId;
    }
}
