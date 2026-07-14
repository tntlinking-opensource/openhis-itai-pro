/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.assembler;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.domain.administration.product.support.ProductDefinitionDisplaySupport;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 住院护士站汇总领药写路径装配（IF-IP-SUM-04 Pre/Cmd）。
 */
@Component
public class NurseStationDispenseSummaryWriteAssembler {

    /**
     * 按发放药房 + medicationId + chargeUnitPrice 分组合并（BR-IP-SUM-005/006/008）。
     *
     * <p>跨发放药房不得合并；同一药房内仅 {@code itemId + 账单单价} 相同才可合并为一 Supply 行。</p>
     *
     * @return 发放药房 locationId → 合并组列表
     */
    public Map<Long, List<SummaryMergeGroup>> buildMergeGroupsByLocationAndPrice(
        List<MedicationDispense> dispenses,
        Map<Long, ChargeItem> chargeByDispenseId,
        Map<String, ProductDefinitionDisplayDto> productByMedId) {
        Map<Long, List<SummaryMergeGroup>> groupsByLocation = new LinkedHashMap<>();
        if (dispenses == null) {
            return groupsByLocation;
        }
        Map<String, SummaryMergeGroup> groupByKey = new LinkedHashMap<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            ChargeItem charge = chargeByDispenseId.get(dispense.getId());
            if (charge == null || charge.getUnitPrice() == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_203,
                    "缺少执行账单，无法汇总，请确认医嘱已执行");
            }
            String productKey = ProductDefinitionKeyUtil.buildCompositeKey(
                com.openhis.health.domain.share.constants.TableNameConstants.MED_MEDICATION_DEFINITION,
                dispense.getMedicationId());
            ProductDefinitionDisplayDto product = productByMedId.get(productKey);
            if (product == null) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_SUM_202,
                    "药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员");
            }
            BigDecimal splitQty = this.computeSplitQuantityForDispense(dispense, product);
            String mergeKey = this.buildMergeGroupKey(
                dispense.getLocationId(), dispense.getMedicationId(), charge.getUnitPrice());
            SummaryMergeGroup group = groupByKey.get(mergeKey);
            if (group == null) {
                group = new SummaryMergeGroup();
                group.setLocationId(dispense.getLocationId());
                group.setMedicationId(dispense.getMedicationId());
                group.setChargeUnitPrice(charge.getUnitPrice());
                group.setSplitUnitCode(product.getSplitUnitCode());
                group.setPatientId(dispense.getPatientId());
                group.setSplitQuantitySum(BigDecimal.ZERO);
                group.setDispenseIds(new ArrayList<>());
                groupByKey.put(mergeKey, group);
                List<SummaryMergeGroup> bucket = groupsByLocation.get(dispense.getLocationId());
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    groupsByLocation.put(dispense.getLocationId(), bucket);
                }
                bucket.add(group);
            }
            group.getDispenseIds().add(dispense.getId());
            group.setSplitQuantitySum(group.getSplitQuantitySum().add(splitQty));
            if (group.getPatientId() == null && dispense.getPatientId() != null) {
                group.setPatientId(dispense.getPatientId());
            }
        }
        return groupsByLocation;
    }

    /**
     * 单行发放数量 → 拆零数量（6 位 HALF_UP，BR-IP-SUM-008）。
     */
    public BigDecimal computeSplitQuantityForDispense(
        MedicationDispense dispense,
        ProductDefinitionDisplayDto product) {
        BigDecimal businessQty = dispense.getQuantity();
        if (businessQty == null) {
            businessQty = dispense.getDispenseQuantity();
        }
        BigDecimal splitQty = MedicationProductQuantityToSplitUnitConverter.medicationQuantityToSplitUnit(
            ProductDefinitionDisplaySupport.toUnitConversionSource(product),
            dispense.getUnitCode(),
            businessQty);
        return splitQty.setScale(InpatientNurseStationConstants.SUMMARY_SPLIT_QUANTITY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 校验合并组 unitBands 完整且拆零单位可写 Supply（BR-IP-SUM-007/008）。
     */
    public void validateMergeGroupUnitBands(SummaryMergeGroup group, ProductDefinitionDisplayDto product) {
        if (group == null || product == null) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_202,
                "药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员");
        }
        if (StringUtils.isEmpty(product.getSplitUnitCode())) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_202,
                "药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员");
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        if (bands == null || bands.isEmpty()) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_202,
                "药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员");
        }
        if (group.getSplitQuantitySum() == null
            || group.getSplitQuantitySum().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                InpatientNurseStationConstants.EX_IP_SUM_202,
                "药品单位槽不完整，或发放行单位无法换算为拆零数量，请联系管理员");
        }
        group.setSplitUnitCode(product.getSplitUnitCode().trim());
    }

    /**
     * 合并组业务键（发放药房 locationId + itemId + chargeUnitPrice）。
     */
    public String buildMergeGroupKey(Long locationId, Long medicationId, BigDecimal chargeUnitPrice) {
        String separator = String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR);
        String pricePart = "";
        if (chargeUnitPrice != null) {
            pricePart = chargeUnitPrice.stripTrailingZeros().toPlainString();
        }
        return String.valueOf(locationId) + separator + String.valueOf(medicationId) + separator + pricePart;
    }

    /**
     * 应用层内部合并组（非 API 出参）。
     */
    @Getter
    @Setter
    public static class SummaryMergeGroup {

        /** 发放药房（{@code MedicationDispense.location_id}）。 */
        private Long locationId;

        /** 药品定义。 */
        private Long medicationId;

        /** 分组单价。 */
        private BigDecimal chargeUnitPrice;

        /** 组内拆零数量累加。 */
        private BigDecimal splitQuantitySum;

        /** 写 Supply 的 unit_code。 */
        private String splitUnitCode;

        /** 组内首行 patient。 */
        private Long patientId;

        /** 组内发放行 id。 */
        private List<Long> dispenseIds;

        /**
         * 返回合并组键（供 SupplyRequest 行回读映射）。
         */
        public String groupKey() {
            String separator = String.valueOf(GeneralSharedConstants.PRODUCT_DEFINITION_COMPOSITE_KEY_SEPARATOR);
            String pricePart = "";
            if (this.chargeUnitPrice != null) {
                pricePart = this.chargeUnitPrice.stripTrailingZeros().toPlainString();
            }
            return String.valueOf(this.locationId) + separator + String.valueOf(this.medicationId) + separator + pricePart;
        }
    }
}
