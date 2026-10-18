/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.product.support;

import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.zxplat.domain.administration.healthcareservice.entity.HealthcareService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.framework.core.application.util.StringUtils;

import java.math.BigDecimal;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 主表实体 → {@link ProductDefinitionDisplayDto} 映射（持久化 {@code code} 列；展示名由
 * {@link com.zxplat.domain.application.shared.product.ProductDefinitionDisplayTerminologyAssembler#enrichDisplayDtos} 统一回填）。
 */
public final class ProductDefinitionDisplaySupport {

    private ProductDefinitionDisplaySupport() {
    }

    /**
     * 药品定义实体 → 统一产品展示 DTO（术语展示列暂为 null，由应用层按需补全）。
     *
     * @param definition 药品定义；缺主键时返回 null
     * @return 展示 DTO 或 null
     */
    public static ProductDefinitionDisplayDto fromMedication(MedicationDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return null;
        }
        ProductDefinitionDisplayDto dto = new ProductDefinitionDisplayDto();
        dto.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        dto.setProductDefinitionId(definition.getId());
        dto.setProductBusNo(definition.getBusNo());
        dto.setProductName(definition.getName());
        dto.setMerchandiseName(definition.getMerchandiseName());
        dto.setCategoryCode(definition.getCategoryCode());
        dto.setPurchaseUnitCode(definition.getPurchaseUnitCode());
        dto.setSplitUnitCode(definition.getSplitUnitCode());
        dto.setPackageUnitCode(definition.getPackageUnitCode());
        dto.setStockUnitCode(definition.getStockUnitCode());
        dto.setDoseUnitCode(definition.getDoseUnitCode());
        dto.setPurchaseUnitRatio(definition.getPurchaseUnitRatio());
        dto.setStockUnitRatio(definition.getStockUnitRatio());
        dto.setPackageUnitRatio(definition.getPackageUnitRatio());
        dto.setDoseUnitRatio(definition.getDoseUnitRatio());
        dto.setTraceUnitCode(definition.getTraceUnitCode());
        dto.setTraceUnitRatio(definition.getTraceUnitRatio());
        // 默认/最小/最大单次剂量：供医生站开立默认带出与快捷切换（剂量单位）
        dto.setDose(definition.getDose());
        dto.setMinUnit(definition.getMinUnit());
        dto.setMaxUnit(definition.getMaxUnit());
        // 库存警戒量：供 AS-INV-WHS-004 库存预警按目录优先于全局设置
        dto.setItemMinQuantity(definition.getItemMinQuantity());
        dto.setItemMaxQuantity(definition.getItemMaxQuantity());
        dto.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        dto.setTotalVolume(definition.getTotalVolume());
        // 厂家展示名由应用层按 manufacturerId 解析；此处仅投影主键
        dto.setManufacturer(null);
        dto.setManufacturerId(definition.getManufacturerId());
        dto.setLocationId(definition.getLocationId());
        dto.setSupplierId(definition.getSupplyId());
        dto.setStatusEnum(definition.getStatusEnum());
        dto.setActiveFlag(definition.getActiveFlag());
        dto.setRxFlag(definition.getRxFlag());
        dto.setYbNo(definition.getYbNo());
        dto.setApprovalNumber(trimToNull(definition.getApprovalNumber()));
        dto.setPyStr(definition.getPyStr());
        dto.setWbStr(definition.getWbStr());
        dto.setChrgitmLv(definition.getChrgitmLv());
        dto.setRateCode(definition.getRateCode());
        dto.setMethodCode(definition.getMethodCode());
        dto.setDoseFormCode(definition.getDoseFormCode());
        // 中药特殊煎法：供医生站中药选品默认带出
        dto.setDosageInstruction(definition.getDosageInstruction());
        dto.setRestrictedFlag(definition.getRestrictedFlag());
        dto.setRestrictedScope(definition.getRestrictedScope());
        dto.setRestrictedEnum(definition.getRestrictedEnum());
        // 药品性质（medicine_properties）与门诊拆分属性（OrderCalcRule）分字段投影，分方只读前者
        dto.setPharmacologyCategoryCode(definition.getPharmacologyCategoryCode());
        dto.setPartAttributeEnum(definition.getPartAttributeEnum());
        dto.setThoPartAttributeEnum(definition.getThoPartAttributeEnum());
        dto.setSkinTestFlag(definition.getSkinTestFlag());
        dto.setInjectFlag(definition.getInjectFlag());
        return dto;
    }

    /**
     * 活动（诊疗）定义实体 → 统一产品展示 DTO。
     *
     * @param definition 活动定义；缺主键时返回 null
     * @return 展示 DTO 或 null
     */
    public static ProductDefinitionDisplayDto fromActivity(ActivityDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return null;
        }
        ProductDefinitionDisplayDto dto = new ProductDefinitionDisplayDto();
        dto.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        dto.setProductDefinitionId(definition.getId());
        dto.setProductBusNo(definition.getBusNo());
        dto.setProductName(definition.getName());
        dto.setMerchandiseName(definition.getMerchandiseName());
        dto.setCategoryCode(definition.getCategoryCode());
        dto.setPackageUnitCode(definition.getPackageUnitCode());
        dto.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        dto.setStatusEnum(definition.getStatusEnum());
        dto.setYbNo(definition.getYbNo());
        dto.setPyStr(definition.getPyStr());
        dto.setWbStr(definition.getWbStr());
        // 医保等级：诊疗目录维护字段，供医生站开立选品展示
        dto.setChrgitmLv(definition.getChrgitmLv());
        dto.setActivityTypeCode(definition.getTypeEnum());
        dto.setLocationId(definition.getOrgId());
        dto.setPackageUnitRatio(BigDecimal.ONE);
        // 目录排序号：麻醉等选品前端可按此优先排列
        dto.setSortOrder(definition.getSortOrder());
        return dto;
    }

    /**
     * 器材定义实体 → 统一产品展示 DTO。
     *
     * @param definition 器材定义；缺主键时返回 null
     * @return 展示 DTO 或 null
     */
    public static ProductDefinitionDisplayDto fromDevice(DeviceDefinition definition) {
        if (definition == null || definition.getId() == null) {
            return null;
        }
        ProductDefinitionDisplayDto dto = new ProductDefinitionDisplayDto();
        dto.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
        dto.setProductDefinitionId(definition.getId());
        dto.setProductBusNo(definition.getBusNo());
        dto.setProductName(definition.getName());
        dto.setMerchandiseName(definition.getMerchandiseName());
        dto.setCategoryCode(definition.getCategoryCode());
        dto.setTypeCode(definition.getTypeCode());
        dto.setPurchaseUnitCode(definition.getPurchaseUnitCode());
        dto.setSplitUnitCode(definition.getSplitUnitCode());
        dto.setPackageUnitCode(definition.getPackageUnitCode());
        dto.setStockUnitCode(definition.getStockUnitCode());
        dto.setDoseUnitCode(definition.getDoseUnitCode());
        dto.setPurchaseUnitRatio(definition.getPurchaseUnitRatio());
        dto.setStockUnitRatio(definition.getStockUnitRatio());
        dto.setPackageUnitRatio(definition.getPackageUnitRatio());
        dto.setDoseUnitRatio(definition.getDoseUnitRatio());
        dto.setTraceUnitCode(definition.getTraceUnitCode());
        dto.setTraceUnitRatio(definition.getTraceUnitRatio());
        // 库存警戒量：供 AS-INV-WHS-004 库存预警按目录优先于全局设置
        dto.setItemMinQuantity(definition.getItemMinQuantity());
        dto.setItemMaxQuantity(definition.getItemMaxQuantity());
        dto.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        dto.setTotalVolume(definition.getTotalVolume());
        // 厂家展示名由应用层按 manufacturerId 解析；此处仅投影主键
        dto.setManufacturer(null);
        dto.setManufacturerId(definition.getManufacturerId());
        dto.setLocationId(definition.getLocationId());
        dto.setSupplierId(definition.getSupplyId());
        dto.setStatusEnum(definition.getStatusEnum());
        dto.setActiveFlag(definition.getActiveFlag());
        dto.setRxFlag(definition.getRxFlag());
        dto.setYbNo(definition.getYbNo());
        dto.setPyStr(definition.getPyStr());
        dto.setWbStr(definition.getWbStr());
        dto.setChrgitmLv(definition.getChrgitmLv());
        return dto;
    }

    /**
     * 号源类型（挂号费）实体 → 统一产品展示 DTO。
     *
     * @param healthcareService 医疗服务定义；缺主键时返回 null
     * @return 展示 DTO 或 null
     */
    public static ProductDefinitionDisplayDto fromHealthcareService(HealthcareService healthcareService) {
        if (healthcareService == null || healthcareService.getId() == null) {
            return null;
        }
        ProductDefinitionDisplayDto dto = new ProductDefinitionDisplayDto();
        dto.setProductTableName(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        dto.setProductDefinitionId(healthcareService.getId());
        dto.setProductBusNo(healthcareService.getBusNo());
        dto.setProductName(healthcareService.getName());
        dto.setCategoryCode(healthcareService.getCategoryCode());
        dto.setStatusEnum(healthcareService.getStatusEnum());
        dto.setActiveFlag(healthcareService.getActiveFlag());
        dto.setYbNo(healthcareService.getYbNo());
        return dto;
    }

    /**
     * 从展示 DTO 提取发药/库存单位换算所需字段。
     *
     * @param dto 产品展示 DTO
     * @return 单位换算源；dto 为 null 时返回 null
     */
    public static MedicationProductUnitConversionSource toUnitConversionSource(ProductDefinitionDisplayDto dto) {
        if (dto == null) {
            return null;
        }
        MedicationProductUnitConversionSource source = new MedicationProductUnitConversionSource();
        source.setSplitUnitCode(dto.getSplitUnitCode());
        source.setDoseUnitCode(dto.getDoseUnitCode());
        source.setDoseUnitRatio(dto.getDoseUnitRatio());
        source.setPurchaseUnitCode(dto.getPurchaseUnitCode());
        source.setPurchaseUnitRatio(dto.getPurchaseUnitRatio());
        source.setStockUnitCode(dto.getStockUnitCode());
        source.setStockUnitRatio(dto.getStockUnitRatio());
        source.setPackageUnitCode(dto.getPackageUnitCode());
        source.setPackageUnitRatio(dto.getPackageUnitRatio());
        source.setTraceUnitCode(dto.getTraceUnitCode());
        source.setTraceUnitRatio(dto.getTraceUnitRatio());
        return source;
    }

    /**
     * 解析产品展示名（通用名优先，其次商品名），供库存不足等业务提示使用。
     *
     * @param productRow 产品读模型
     * @param fallback   名称均空时的兜底文案（如「药品」）
     * @return 非空展示名
     */
    public static String resolveProductDisplayName(ProductDefinitionDisplayDto productRow, String fallback) {
        if (productRow == null) {
            return fallback;
        }
        String name = StringUtils.firstNonBlank(productRow.getProductName(), productRow.getMerchandiseName());
        if (StringUtils.isEmpty(name)) {
            return fallback;
        }
        return name;
    }
}
