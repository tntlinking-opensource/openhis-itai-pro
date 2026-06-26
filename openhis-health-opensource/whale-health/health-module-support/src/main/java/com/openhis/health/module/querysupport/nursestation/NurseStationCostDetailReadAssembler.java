/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.querysupport.nursestation;

import com.openhis.health.module.common.assembler.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.openhis.health.module.common.dto.product.ProductDefinitionCatalogUnitBandDto;
import com.openhis.health.module.querysupport.nursestation.dto.NurseStationCostDetailLineDto;
import com.openhis.health.module.querysupport.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.InpatientNurseStationCostDetailChargeRow;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import org.apache.commons.lang3.StringUtils;
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
            if (productId == null || StringUtils.isBlank(productTable)) {
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

    public Long resolveDisplayProductId(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        if (charge.getParentId() != null && parentChargeById != null) {
            ChargeItem parent = parentChargeById.get(charge.getParentId());
            if (parent != null && parent.getProductId() != null) {
                return parent.getProductId();
            }
        }
        return charge.getProductId();
    }

    public String resolveDisplayProductTable(ChargeItem charge, Map<Long, ChargeItem> parentChargeById) {
        if (charge.getParentId() != null && parentChargeById != null) {
            ChargeItem parent = parentChargeById.get(charge.getParentId());
            if (parent != null && StringUtils.isNotBlank(parent.getProductTable())) {
                return parent.getProductTable();
            }
        }
        return charge.getProductTable();
    }

    public String resolveExpenseBlockCode(String serviceTable) {
        if (StringUtils.isBlank(serviceTable)) {
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

    private String resolveQuantityUnitDisplay(String quantityUnit, ProductDefinitionDisplayDto product) {
        if (StringUtils.isBlank(quantityUnit)) {
            return quantityUnit;
        }
        if (product == null) {
            return quantityUnit;
        }
        List<ProductDefinitionCatalogUnitBandDto> bands =
            ProductDefinitionCatalogUnitBandsAssembler.buildInboundSelectionBands(product);
        String normalized = quantityUnit.trim();
        for (ProductDefinitionCatalogUnitBandDto band : bands) {
            if (band == null || StringUtils.isBlank(band.getUnitCode())) {
                continue;
            }
            if (normalized.equalsIgnoreCase(band.getUnitCode().trim())) {
                if (StringUtils.isNotBlank(band.getUnitName())) {
                    return band.getUnitName();
                }
            }
        }
        return quantityUnit;
    }
}
