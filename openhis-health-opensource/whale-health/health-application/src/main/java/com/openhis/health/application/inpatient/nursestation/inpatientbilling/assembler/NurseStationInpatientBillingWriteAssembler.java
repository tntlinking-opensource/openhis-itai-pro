/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.assembler;

import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationPricingWriteItemDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.mapper.NurseStationInpatientBillingMapper;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IF-IP-PRC-02/04 写路径专项装配：权威定价重算、吸氧/心电时长 Gate（BR-IP-PRC-010）。
 *
 * <p>诊疗套餐（BR-IP-PRC-011）全局仅落父项一条 {@code ChargeItem}；子项由
 * {@link com.openhis.health.application.basic.activitydefinition.support.ActivityDefinitionPackageChildResolver} 在需要时解析。</p>
 *
 * <p>耗材 {@code performLocation} 须为 IF-IP-PRC-01 {@code defaultDispenseLocationId} 回传；
 * 可选 {@code lotNumber} 锁定批号价。权威单价先取包装锚 LOT_NUMBER_PRICE/SALE，再按提交
 * {@code unitCode} 线性换算到划价单位（禁止包装价÷ratio 再×数量）。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseStationInpatientBillingWriteAssembler {

    private final ServiceRequestQueryService serviceRequestQueryService;
    private final EncounterQueryService encounterQueryService;
    private final EncounterContextReader EncounterContextReader;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final NurseStationInpatientBillingCatalogAssembler nurseStationInpatientBillingCatalogAssembler;
    private final NurseStationInpatientBillingMapper nurseStationInpatientBillingMapper;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /**
     * 写路径入账前批量重算价目与单价（后端定权威；前端 unitPrice 仅展示参考）。
     *
     * <p>耗材须回传 IF-IP-PRC-01 已解析 {@code performLocation}；有 {@code lotNumber} 时锁定该批号价，
     * 否则 FIFO。包装锚价再按提交 {@code unitCode} 线性换算到划价单位后落账。</p>
     *
     * @param items               待提交写行（耗材须已带 performLocation）
     * @param encounterIds        本批就诊
     * @param managedWardLocationIds 护士管辖病区（就诊上下文收窄）
     */
    public void reapplyAuthoritativeWritePricing(
        List<NurseStationPricingWriteItemDto> items,
        List<Long> encounterIds,
        List<Long> managedWardLocationIds) {
        if (items == null || items.isEmpty()) {
            return;
        }
        // Pre：本批写行涉及的产品定义去重，一次 batch load 读模型
        LinkedHashMap<String, ProductDefinitionBatchLookup> lookupByKey = new LinkedHashMap<>();
        for (NurseStationPricingWriteItemDto row : items) {
            if (row == null || row.getProductDefinitionId() == null
                || StringUtils.isEmpty(row.getProductTableName())) {
                continue;
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                row.getProductTableName(), row.getProductDefinitionId());
            if (!lookupByKey.containsKey(productKey)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(row.getProductTableName());
                lookup.setProductDefinitionId(row.getProductDefinitionId());
                lookupByKey.put(productKey, lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookupByKey.values()));
        // Pre：批量解析各就诊开立机构，供 FIFO 定价与模板回落
        var encounterQuery = nurseStationInpatientBillingMapper.toInpatientEncounterPageQuery(
            encounterIds, managedWardLocationIds);
        List<InpatientEncounterContextQueryModel> encounterRows =
            EncounterContextReader.listInpatientEncounterContexts(encounterQuery);
        Map<Long, Long> organizationIdByEncounter = new HashMap<>();
        for (InpatientEncounterContextQueryModel encounterRow : encounterRows) {
            if (encounterRow != null && encounterRow.getEncounterId() != null) {
                organizationIdByEncounter.put(encounterRow.getEncounterId(), encounterRow.getOrganizationId());
            }
        }
        for (NurseStationPricingWriteItemDto row : items) {
            // Gate：缺产品锚点视为非法划价行（EX-IP-PRC-204）
            if (row == null || row.getProductDefinitionId() == null
                || StringUtils.isEmpty(row.getProductTableName())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                row.getProductTableName(), row.getProductDefinitionId());
            ProductDefinitionDisplayDto productRow = productByKey.get(productKey);
            ChargeItemDefinition selected;
            if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(row.getProductTableName())) {
                // 耗材写路径：performLocation 须为 IF-IP-PRC-01 目录 defaultDispenseLocationId 回传（EX-IP-PRC-202）
                if (row.getPerformLocation() == null) {
                    throw new BusinessException(
                        InpatientNurseStationConstants.EX_IP_PRC_202,
                        "耗材划价失败：请选择发放库房");
                }
                Long organizationId = organizationIdByEncounter.get(row.getEncounterId());
                selected = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
                    row.getProductTableName(),
                    row.getProductDefinitionId(),
                    productRow,
                    organizationId,
                    row.getEncounterId(),
                    row.getPerformLocation(),
                    null,
                    row.getLotNumber()
                );
            } else if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(row.getProductTableName())) {
                // 诊疗写路径：SALE 择优，不涉及 performLocation / 批号
                selected = clinicalCatalogPriceResolver.resolveDraftChargePriceLine(
                    row.getProductTableName(),
                    row.getProductDefinitionId(),
                    productRow,
                    organizationIdByEncounter.get(row.getEncounterId()),
                    row.getEncounterId(),
                    null,
                    null
                );
            } else {
                // Gate：划价仅允许诊疗+耗材物理表
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
            // Gate：无价目时拒绝落账，避免 0 元费用
            if (selected == null || selected.getPriceValue() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_PRC_204,
                    "划价项目无效或价格异常，请重新选品");
            }
            // 后端定权威：包装锚价 → 提交单位槽价 → 行金额（禁止包装价÷ratio 再×数量）
            BigDecimal authoritativeUnitPrice = this.resolveAuthoritativeUnitPriceForSubmittedUnit(
                productRow, selected, row.getUnitCode());
            row.setChargeItemDefinitionId(selected.getId());
            row.setUnitPrice(authoritativeUnitPrice);
            if (row.getQuantity() != null) {
                row.setTotalPrice(MonetaryAmountSupport.lineTotal(authoritativeUnitPrice, row.getQuantity()));
            }
        }
    }

    /**
     * 将包装锚定价线性换算为提交 {@code unitCode} 对应槽单价。
     *
     * @param productRow           产品读模型；空时回落包装锚价
     * @param selected             命中定价行（包装锚 {@code price_value}）
     * @param submittedUnitCode    前端提交划价单位
     * @return 与提交单位同口径的入账单价
     */
    private BigDecimal resolveAuthoritativeUnitPriceForSubmittedUnit(
        ProductDefinitionDisplayDto productRow,
        ChargeItemDefinition selected,
        String submittedUnitCode
    ) {
        BigDecimal packageAnchoredPrice = selected.getPriceValue();
        if (productRow == null || StringUtils.isEmpty(submittedUnitCode)) {
            return packageAnchoredPrice;
        }
        String bandPricingUnitCode = ProductDefinitionCatalogUnitBandsAssembler
            .resolveDoctorStationBandPricingUnitCode(productRow, selected.getPriceTypeCode());
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildDoctorStationBands(
                productRow, bandPricingUnitCode, packageAnchoredPrice);
        if (bands == null || bands.isEmpty()) {
            return packageAnchoredPrice;
        }
        String normalizedUnitCode = submittedUnitCode.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isEmpty(band.getUnitCode())) {
                continue;
            }
            // 命中提交单位且槽上已有线性单价：作入账权威
            if (normalizedUnitCode.equals(band.getUnitCode().trim()) && band.getUnitPrice() != null) {
                return band.getUnitPrice();
            }
        }
        // 提交单位未落在 bands：回落包装锚价（与历史同构）
        return packageAnchoredPrice;
    }

    /**
     * 吸氧/心电计时类：当日上限 + 在院余量 Gate（BR-IP-PRC-010 / BR-SRQ-018）。
     *
     * @param row 待写入诊疗行
     * @throws BusinessException 超限时 EX-IP-PRC-203
     */
    public void assertOxygenOrEcgDuration(NurseStationPricingWriteItemDto row) {
        if (row == null || row.getEncounterId() == null || row.getActivityId() == null) {
            return;
        }
        // 优先用选品带回的 Gate 类型，否则按活动 id 查产品名解析
        String gateKind = row.getDurationGateKind();
        if (StringUtils.isEmpty(gateKind)) {
            gateKind = this.resolveDurationGateKindByActivityId(row.getActivityId());
        }
        if (StringUtils.isEmpty(gateKind)) {
            // 非吸氧/心电计时类诊疗，跳过时长 Gate
            return;
        }
        BigDecimal add = row.getQuantity();
        if (add == null) {
            add = BigDecimal.ZERO;
        }
        // 当日累计：按就诊 + 活动 + 自然日汇总 wkf_service_request.quantity（小时）
        long accumulatedToday = this.serviceRequestQueryService.sumQuantityHoursByEncounterAndActivity(
            row.getEncounterId(),
            row.getActivityId(),
            LocalDate.now());

        // 在院累计：同口径不加日期，用于 BR-SRQ-018 在院余量
        long accumulatedStay = this.serviceRequestQueryService.sumQuantityHoursByEncounterAndActivity(
            row.getEncounterId(),
            row.getActivityId(),
            null);

        int dailyMax = InpatientNurseStationConstants.OXYGEN_DAILY_DURATION_MAX_HOURS;
        if (InpatientNurseStationConstants.DURATION_GATE_KIND_ECG.equals(gateKind)) {
            // 心电监测当日上限与吸氧不同
            dailyMax = InpatientNurseStationConstants.ECG_DAILY_DURATION_MAX_HOURS;
        }
        BigDecimal todayTotal = BigDecimal.valueOf(accumulatedToday).add(add);
        if (todayTotal.compareTo(BigDecimal.valueOf(dailyMax)) > 0) {
            // Gate：当日累计 + 本次超过 BR-IP-PRC-010 上限
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_203,
                "吸氧/心电监测时长超出当日上限，请核对数量");
        }

        // 在院上限：入院至当前小时数（至少 1h），累计+本次不得超过
        long inpatientHours = this.resolveInpatientHoursCap(row.getEncounterId());
        BigDecimal stayTotal = BigDecimal.valueOf(accumulatedStay).add(add);
        if (stayTotal.compareTo(BigDecimal.valueOf(inpatientHours)) > 0) {
            // Gate：在院累计 + 本次超过入院至当前可用小时（BR-SRQ-018）
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_PRC_203,
                "吸氧/心电监测时长超出在院可用余量，请核对数量");
        }
    }

    /**
     * 按活动定义 id 解析吸氧/心电 Gate 类型（与选品目录口径一致）。
     *
     * @param activityId 诊疗项目 id
     * @return OXYGEN / ECG 或 null
     */
    public String resolveDurationGateKindByActivityId(Long activityId) {
        if (activityId == null) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.WKF_ACTIVITY_DEFINITION, activityId);
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        lookup.setProductDefinitionId(activityId);
        Map<String, ProductDefinitionDisplayDto> products =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        ProductDefinitionDisplayDto product = products.get(key);
        if (product == null || StringUtils.isEmpty(product.getProductName())) {
            return null;
        }
        return nurseStationInpatientBillingCatalogAssembler.resolveDurationGateKind(product.getProductName());
    }

    /**
     * 在院可用小时上限：入院时间至当前（至少 1 小时）。
     *
     * @param encounterId 就诊 id
     * @return 可用于吸氧/心电 Gate 的小时数
     */
    private long resolveInpatientHoursCap(Long encounterId) {
        var context = EncounterContextReader.mustGetInpatientContextByEncounterId(encounterId);
        // 缺入院时间时用心电默认上限，避免 Gate 变为 0
        if (context == null || context.getStartTime() == null) {
            return InpatientNurseStationConstants.ECG_DAILY_DURATION_MAX_HOURS;
        }
        long hours = Duration.between(context.getStartTime(), LocalDateTime.now()).toHours();
        // 在院不足 1 小时按 1 小时计，与累计 quantity 口径一致
        if (hours < 1L) {
            hours = 1L;
        }
        return hours;
    }
}
