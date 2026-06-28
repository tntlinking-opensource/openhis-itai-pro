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

package com.openhis.health.module.querysupport.product;

import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.MedicationProductUnitConversionSource;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 主表实体 → {@link ProductDefinitionDisplayDto} 映射（持久化 {@code code} 列；展示名由
 * {@link ProductDefinitionSelectionTerminologySupport#enrichDisplayDtos} 统一回填）。
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
        dto.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        dto.setTotalVolume(definition.getTotalVolume());
        dto.setManufacturer(trimToNull(definition.getManufacturerText()));
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
        dto.setRestrictedFlag(definition.getRestrictedFlag());
        dto.setRestrictedScope(definition.getRestrictedScope());
        dto.setRestrictedEnum(definition.getRestrictedEnum());
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
        dto.setActivityTypeCode(definition.getTypeEnum());
        dto.setLocationId(definition.getOrgId());
        dto.setPackageUnitRatio(BigDecimal.ONE);
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
        dto.setPurchaseUnitCode(definition.getPurchaseUnitCode());
        dto.setSplitUnitCode(definition.getSplitUnitCode());
        dto.setPackageUnitCode(definition.getPackageUnitCode());
        dto.setStockUnitCode(definition.getStockUnitCode());
        dto.setDoseUnitCode(definition.getDoseUnitCode());
        dto.setPurchaseUnitRatio(definition.getPurchaseUnitRatio());
        dto.setStockUnitRatio(definition.getStockUnitRatio());
        dto.setPackageUnitRatio(definition.getPackageUnitRatio());
        dto.setDoseUnitRatio(definition.getDoseUnitRatio());
        dto.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        dto.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        dto.setTotalVolume(definition.getTotalVolume());
        dto.setManufacturer(trimToNull(definition.getManufacturerText()));
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
        return source;
    }
}
