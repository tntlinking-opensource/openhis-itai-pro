/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.support;

import com.openhis.health.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.NurseStationCostDetailLineDto;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站费用明细读路径 DTO 装配（IF-IP-CDT-02 读侧；供 report-bridge 与 health-application 复用）。
 */
@Component
public class NurseStationCostDetailReadAssembler {

    /**
     * 费用明细行 → 产品定义批量 lookup（子项 charge 回落父项 product 锚点）。
     *
     * @param rows       护士站费用明细读行
     * @param parentById 父 chargeItem 映射
     * @return 去重后的批量 lookup 列表
     */
    public List<ProductDefinitionBatchLookup> toProductBatchLookups(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<Long, ChargeItem> parentById) {
        Set<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return lookups;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long productId = resolveDisplayProductId(charge, parentById);
            String productTable = resolveDisplayProductTable(charge, parentById);
            if (productId == null || StringUtils.isEmpty(productTable)) {
                continue;
            }
            String dedupeKey = ProductDefinitionKeyUtil.buildCompositeKey(productTable.trim(), productId);
            if (dedupeKey == null) {
                continue;
            }
            if (dedupeKeys.add(dedupeKey)) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(productTable.trim());
                lookup.setProductDefinitionId(productId);
                lookups.add(lookup);
            }
        }
        return lookups;
    }

    /**
     * 装配护士站费用明细 DTO 列表（挂接产品读模型、退药高亮、费用块 code）。
     *
     * @param rows              读侧 charge 行
     * @param productByKey      产品读模型 Map
     * @param parentChargeById  父 charge 映射
     * @return 明细 DTO 列表
     */
    public List<NurseStationCostDetailLineDto> buildDetailLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById) {
        return this.buildDetailLines(rows, productByKey, parentChargeById, Collections.emptySet());
    }

    /**
     * 装配护士站费用明细 DTO 列表（含自动计费 / 护士冲销来源展示覆盖）。
     *
     * @param rows                      读侧 charge 行
     * @param productByKey              产品读模型 Map
     * @param parentChargeById          父 charge 映射
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合
     * @return 明细 DTO 列表
     */
    public List<NurseStationCostDetailLineDto> buildDetailLines(
        List<InpatientNurseStationCostDetailChargeRow> rows,
        Map<String, ProductDefinitionDisplayDto> productByKey,
        Map<Long, ChargeItem> parentChargeById,
        Set<Long> autoRollServiceRequestIds) {
        List<NurseStationCostDetailLineDto> lines = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return lines;
        }
        for (InpatientNurseStationCostDetailChargeRow row : rows) {
            if (row == null || row.getChargeItem() == null) {
                continue;
            }
            ChargeItem charge = row.getChargeItem();
            Long displayProductId = resolveDisplayProductId(charge, parentChargeById);
            String displayProductTable = resolveDisplayProductTable(charge, parentChargeById);
            String productKey =
                ProductDefinitionKeyUtil.buildCompositeKey(displayProductTable, displayProductId);
            ProductDefinitionDisplayDto product = productByKey.get(productKey);
            NurseStationCostDetailLineDto line = new NurseStationCostDetailLineDto();
            line.setChargeItemId(charge.getId());
            line.setProductId(displayProductId);
            line.setProductTableName(displayProductTable);
            if (product != null) {
                line.setItemName(product.getProductName());
                line.setItemSpec(product.getTotalVolume());
                // 与汇总行同源：明细单打印按产品实际分类（中药/西药/检验等）分组
                line.setCategoryCode(product.getCategoryCode());
                line.setCategoryDisplay(product.getCategoryCodeDictText());
                // 医保等级：目录对照回填，费用明细/打印「医保等级」列同源
                line.setChrgitmLv(product.getChrgitmLv());
                line.setChrgitmLvText(product.getChrgitmLvText());
            }
            line.setQuantityValue(charge.getQuantityValue());
            line.setQuantityUnit(charge.getQuantityUnit());
            line.setQuantityUnitDisplay(resolveQuantityUnitDisplay(charge.getQuantityUnit(), product));
            line.setUnitPrice(charge.getUnitPrice());
            line.setTotalPrice(charge.getTotalPrice());
            // 客户可见轴：进窗/排序/列同源（recorded / 发放 / 滚费 occurrence）
            line.setBusinessDateTime(row.getBusinessDateTime());
            line.setDisplayDateTime(row.getDisplayDateTime());
            line.setStatusEnum(charge.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, charge.getStatusEnum());
            if (status != null) {
                line.setStatusDisplay(status.getDisplay());
            }
            line.setGenerateSourceEnum(charge.getGenerateSourceEnum());
            // 床位滚费：落账源仍为 order-execute，来源列覆盖为「自动计费」
            if (this.isAutoRollOrderExecuteCharge(charge, autoRollServiceRequestIds)) {
                line.setGenerateSourceDisplay(
                    InpatientNurseStationConstants.COST_DETAIL_ACTIVITY_TYPE_AUTO_ROLL_DISPLAY);
            } else if (this.isMedicalTechPricingOffsetCharge(charge)) {
                // 医技划价冲销行：generate_source 仍为 medical-tech-pricing，来源列覆盖为「医技冲销」
                line.setGenerateSourceDisplay(
                    InpatientNurseStationConstants.COST_DETAIL_GENERATE_SOURCE_MEDICAL_TECH_OFFSET_DISPLAY);
            } else if (this.isNursePricingOffsetCharge(charge)) {
                // 护士划价冲销行：generate_source 仍为 nurse-pricing，来源列覆盖为「护士冲销」
                line.setGenerateSourceDisplay(
                    InpatientNurseStationConstants.COST_DETAIL_GENERATE_SOURCE_NURSE_OFFSET_DISPLAY);
            } else {
                GenerateSource source = EnumUtil.fromCode(GenerateSource.class, charge.getGenerateSourceEnum());
                if (source != null) {
                    line.setGenerateSourceDisplay(source.getDisplay());
                }
            }
            if (ChargeItemStatus.REFUNDING.getCode().equals(charge.getStatusEnum())
                || ChargeItemStatus.REFUNDED.getCode().equals(charge.getStatusEnum())) {
                // 拟退/已退行高亮，便于护士站费用 Tab 识别
                line.setRefundHighlight(Boolean.TRUE);
            } else {
                line.setRefundHighlight(Boolean.FALSE);
            }
            line.setServiceTable(charge.getServiceTable());
            line.setExpenseBlockCode(resolveExpenseBlockCode(charge.getServiceTable()));
            lines.add(line);
        }
        return lines;
    }

    /**
     * 判定床位自动计费落账行（order-execute + auto-roll-fees SR）。
     *
     * @param charge                    收费明细
     * @param autoRollServiceRequestIds 床位自动计费 SR 主键集合
     * @return true 时来源展示覆盖为自动计费
     */
    private boolean isAutoRollOrderExecuteCharge(ChargeItem charge, Set<Long> autoRollServiceRequestIds) {
        if (charge == null || charge.getServiceId() == null) {
            return false;
        }
        if (!GenerateSource.ORDER_EXECUTE.getCode().equals(charge.getGenerateSourceEnum())) {
            return false;
        }
        if (autoRollServiceRequestIds == null || autoRollServiceRequestIds.isEmpty()) {
            return false;
        }
        return autoRollServiceRequestIds.contains(charge.getServiceId());
    }

    /**
     * 判定医技划价冲销留痕行。
     *
     * <p>{@code generate_source} 仍为 {@code medical-tech-pricing}；以退费态、{@code refund_id}
     * 或数量/金额为负识别负向冲销行，供来源列展示「医技冲销」。</p>
     *
     * @param charge 收费明细
     * @return true 时来源展示覆盖为医技冲销
     */
    private boolean isMedicalTechPricingOffsetCharge(ChargeItem charge) {
        if (charge == null) {
            return false;
        }
        if (!GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(charge.getGenerateSourceEnum())) {
            return false;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(charge.getStatusEnum())
            || ChargeItemStatus.REFUNDED.getCode().equals(charge.getStatusEnum())) {
            return true;
        }
        if (charge.getRefundId() != null) {
            return true;
        }
        BigDecimal quantityValue = charge.getQuantityValue();
        if (quantityValue != null && quantityValue.signum() < 0) {
            return true;
        }
        BigDecimal totalPrice = charge.getTotalPrice();
        if (totalPrice != null && totalPrice.signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 判定护士划价冲销留痕行（IF-IP-PRC-06 / BR-IP-CDT-018）。
     *
     * <p>{@code generate_source} 仍为 {@code nurse-pricing}；以退费态、{@code refund_id}
     * 或数量/金额为负识别负向冲销行，供来源列展示「护士冲销」。</p>
     *
     * @param charge 收费明细
     * @return true 时来源展示覆盖为护士冲销
     */
    private boolean isNursePricingOffsetCharge(ChargeItem charge) {
        if (charge == null) {
            return false;
        }
        // 仅护士划价来源才覆盖为「护士冲销」，医嘱执行退费等走原枚举文案
        if (!GenerateSource.NURSE_PRICING.getCode().equals(charge.getGenerateSourceEnum())) {
            return false;
        }
        if (ChargeItemStatus.REFUNDING.getCode().equals(charge.getStatusEnum())
            || ChargeItemStatus.REFUNDED.getCode().equals(charge.getStatusEnum())) {
            return true;
        }
        // 冲销行 refund_id 链回原正向行（BR-IP-PRC-024）
        if (charge.getRefundId() != null) {
            return true;
        }
        BigDecimal quantityValue = charge.getQuantityValue();
        if (quantityValue != null && quantityValue.signum() < 0) {
            return true;
        }
        BigDecimal totalPrice = charge.getTotalPrice();
        if (totalPrice != null && totalPrice.signum() < 0) {
            return true;
        }
        return false;
    }

    /**
     * 解析展示用 productId（套餐/绑定子 charge 回落父项）。
     *
     * @param charge           当前 charge
     * @param parentChargeById 父 charge 映射
     * @return 产品定义主键
     */
    public Long resolveDisplayProductId(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        if (charge.getParentId() != null && parentChargeById != null) {
            ChargeItem parent = parentChargeById.get(charge.getParentId());
            if (parent != null && parent.getProductId() != null) {
                return parent.getProductId();
            }
        }
        return charge.getProductId();
    }

    /**
     * 解析展示用 productTable（套餐/绑定子 charge 回落父项）。
     *
     * @param charge           当前 charge
     * @param parentChargeById 父 charge 映射
     * @return 产品物理表名
     */
    public String resolveDisplayProductTable(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        if (charge.getParentId() != null && parentChargeById != null) {
            ChargeItem parent = parentChargeById.get(charge.getParentId());
            if (parent != null && StringUtils.isNotEmpty(parent.getProductTable())) {
                return parent.getProductTable();
            }
        }
        return charge.getProductTable();
    }

    /**
     * service_table → 护士站费用块 code（药品/诊疗/耗材/其他）。
     *
     * @param serviceTable charge.service_table
     * @return 费用块 code
     */
    public String resolveExpenseBlockCode(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            throw new IllegalArgumentException("charge missing service_table");
        }
        String normalized = serviceTable.trim().toLowerCase();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_MEDICATION;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_SERVICE;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(normalized)) {
            return InpatientNurseStationConstants.COST_DETAIL_EXPENSE_BLOCK_DEVICE;
        }
        throw new IllegalArgumentException("unknown service_table for expense block: " + serviceTable);
    }

    /**
     * 费用明细数量单位展示：在 catalog 单位带中匹配 unitCode 的展示名。
     *
     * <p>账单单位为空、或诊疗项目账单单位未命中目录槽（如皮试误落药品单位）时，
     * 回落目录主单位展示名，避免数量列只剩裸数字。</p>
     *
     * @param quantityUnit 持久化单位码
     * @param product      产品展示 DTO
     * @return 展示单位；无匹配时诊疗回落主单位，其余回落原码
     */
    public String resolveQuantityUnitDisplay(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (product == null) {
            return quantityUnit;
        }
        if (StringUtils.isNotEmpty(quantityUnit)) {
            List<ProductDefinitionCatalogUnitBandDto> bands =
                ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
            String normalized = quantityUnit.trim();
            for (ProductDefinitionCatalogUnitBandDto band : bands) {
                if (band == null || StringUtils.isEmpty(band.getUnitCode())) {
                    continue;
                }
                if (normalized.equalsIgnoreCase(band.getUnitCode().trim())) {
                    if (StringUtils.isNotEmpty(band.getUnitName())) {
                        return band.getUnitName();
                    }
                }
            }
            String fromProductSlots =
                ProductDefinitionCatalogUnitBandsAssembler.resolveUnitDisplayNameFromProduct(product, normalized);
            if (StringUtils.isNotEmpty(fromProductSlots)) {
                return fromProductSlots;
            }
        }
        // 诊疗：无匹配时回落包装单位展示名（皮试检查等单单位项目）
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(product.getProductTableName())) {
            String primaryName =
                ProductDefinitionCatalogUnitBandsAssembler.resolveCatalogPrimaryUnitName(product);
            if (StringUtils.isNotEmpty(primaryName)) {
                return primaryName;
            }
        }
        return quantityUnit;
    }
}
