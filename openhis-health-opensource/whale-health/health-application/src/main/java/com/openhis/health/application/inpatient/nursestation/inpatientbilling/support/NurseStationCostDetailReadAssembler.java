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

import java.util.ArrayList;
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
            }
            line.setQuantityValue(charge.getQuantityValue());
            line.setQuantityUnit(charge.getQuantityUnit());
            line.setQuantityUnitDisplay(resolveQuantityUnitDisplay(charge.getQuantityUnit(), product));
            line.setUnitPrice(charge.getUnitPrice());
            line.setTotalPrice(charge.getTotalPrice());
            line.setBusinessDateTime(row.getBusinessDateTime());
            line.setStatusEnum(charge.getStatusEnum());
            ChargeItemStatus status = EnumUtil.fromCode(ChargeItemStatus.class, charge.getStatusEnum());
            if (status != null) {
                line.setStatusDisplay(status.getDisplay());
            }
            line.setGenerateSourceEnum(charge.getGenerateSourceEnum());
            GenerateSource source = EnumUtil.fromCode(GenerateSource.class, charge.getGenerateSourceEnum());
            if (source != null) {
                line.setGenerateSourceDisplay(source.getDisplay());
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
     * @param quantityUnit 持久化单位码
     * @param product      产品展示 DTO
     * @return 展示单位；无匹配回落原码
     */
    private String resolveQuantityUnitDisplay(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (StringUtils.isEmpty(quantityUnit)) {
            return quantityUnit;
        }
        if (product == null) {
            return quantityUnit;
        }
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
        return quantityUnit;
    }
}
