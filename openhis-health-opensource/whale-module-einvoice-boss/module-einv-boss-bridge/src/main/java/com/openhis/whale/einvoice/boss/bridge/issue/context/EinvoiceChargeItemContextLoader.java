/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.bridge.issue.context;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.einvoice.boss.bridge.issue.support.BossQuantityUnitDisplayResolver;
import com.openhis.whale.einvoice.boss.domain.issue.result.BossChargeLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 从 health {@code adm_charge_item} 加载开票明细快照，并解析博思内部收费编码。
 */
@Component
@RequiredArgsConstructor
public class EinvoiceChargeItemContextLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final BossQuantityUnitDisplayResolver quantityUnitDisplayResolver;

    /**
     * 按收费项主键列表加载明细行快照。
     *
     * @param chargeItemIds 来自支付的 charge_item_ids 解析结果
     * @param registrationFee 是否挂号票（影响 internalCode 映射为 REG_FEE）
     * @return 博思报文可用的明细快照列表
     */
    public List<BossChargeLineSnapshot> loadByIds(List<Long> chargeItemIds, boolean registrationFee) {
        List<BossChargeLineSnapshot> lines = new ArrayList<>();
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return lines;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(query);
        List<Long> definitionIds = rows.stream()
                .map(ChargeItem::getDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ChargeItemDefinition> definitionMap =
                chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        Map<String, ProductDefinitionDisplayDto> productMap = loadProductMap(rows);
        // 票面单位须为中文 display；quantity_unit 存术语 code（如 6/branch/bottle）
        Map<String, String> unitLabelByCode = quantityUnitDisplayResolver.warmLabelMap();
        for (ChargeItem row : rows) {
            BossChargeLineSnapshot snapshot = new BossChargeLineSnapshot();
            snapshot.setChargeItemId(row.getId());
            snapshot.setDefinitionId(row.getDefinitionId());
            ChargeItemDefinition definition = row.getDefinitionId() != null
                    ? definitionMap.get(row.getDefinitionId()) : null;
            snapshot.setInternalCode(resolveInternalCode(registrationFee, row, definition));
            snapshot.setItemName(resolveItemName(row, definition, productMap));
            snapshot.setAmount(row.getTotalPrice());
            snapshot.setQuantity(row.getQuantityValue());
            snapshot.setUnit(quantityUnitDisplayResolver.toDisplay(row.getQuantityUnit(), unitLabelByCode));
            snapshot.setUnitPrice(row.getUnitPrice());
            lines.add(snapshot);
        }
        return lines;
    }

    /**
     * 具体项目名：产品目录名优先，其次价目 {@code chargeName}（对齐 areahis listDetail.name）。
     */
    private String resolveItemName(
            ChargeItem row,
            ChargeItemDefinition definition,
            Map<String, ProductDefinitionDisplayDto> productMap) {
        ProductDefinitionDisplayDto product = resolveProduct(row, productMap);
        if (product != null) {
            String productName = firstNonBlank(product.getProductName(), product.getMerchandiseName());
            if (StringUtils.hasText(productName)) {
                return productName.trim();
            }
        }
        if (definition != null && StringUtils.hasText(definition.getChargeName())) {
            return definition.getChargeName().trim();
        }
        return null;
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first.trim();
        }
        if (StringUtils.hasText(second)) {
            return second.trim();
        }
        return null;
    }

    private Map<String, ProductDefinitionDisplayDto> loadProductMap(List<ChargeItem> rows) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem row : rows) {
            ProductDefinitionBatchLookup lookup = buildProductLookup(row);
            if (lookup == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
            if (key == null || !dedupeKeys.add(key)) {
                continue;
            }
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionQueryService.batchLoadByKeys(lookups);
    }

    private ProductDefinitionDisplayDto resolveProduct(
            ChargeItem row, Map<String, ProductDefinitionDisplayDto> productMap) {
        ProductDefinitionBatchLookup lookup = buildProductLookup(row);
        if (lookup == null || productMap == null || productMap.isEmpty()) {
            return null;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
                lookup.getProductTableName(), lookup.getProductDefinitionId());
        if (key == null) {
            return null;
        }
        return productMap.get(key);
    }

    /**
     * 收费行 → 产品 lookup：优先 product_*；挂号费回落 healthcare_service。
     */
    private static ProductDefinitionBatchLookup buildProductLookup(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return null;
        }
        if (StringUtils.hasText(chargeItem.getProductTable()) && chargeItem.getProductId() != null) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(chargeItem.getProductTable().trim());
            lookup.setProductDefinitionId(chargeItem.getProductId());
            return lookup;
        }
        if (!StringUtils.hasText(chargeItem.getServiceTable()) || chargeItem.getServiceId() == null) {
            return null;
        }
        if (!TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(chargeItem.getServiceTable().trim())) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.ADM_HEALTHCARE_SERVICE);
        lookup.setProductDefinitionId(chargeItem.getServiceId());
        return lookup;
    }

    /**
     * 解析 health 内部收费编码，供 {@code einv_charge_contrast} 对照为博思 chargeCode。
     *
     * @param registrationFee 挂号票固定 {@code REG_FEE}
     * @param row 收费项行
     * @param definition 收费项定义（可空）
     * @return internalCode，如 {@code FIN_xxx} 或 {@code DEF_{definitionId}}
     */
    private String resolveInternalCode(boolean registrationFee, ChargeItem row, ChargeItemDefinition definition) {
        if (registrationFee) {
            return "REG_FEE";
        }
        if (definition != null && StringUtils.hasText(definition.getTypeCode())) {
            return "FIN_" + definition.getTypeCode().trim();
        }
        return "DEF_" + row.getDefinitionId();
    }

    /**
     * 是否挂号票据类型。
     *
     * @param invoiceTypeCode health 票据类型码
     * @return REG 类型为 true
     */
    public boolean isRegistrationInvoice(String invoiceTypeCode) {
        return AccountTypeCode.REG.getCode().equals(invoiceTypeCode);
    }

    /**
     * 是否门诊票据类型。
     *
     * @param invoiceTypeCode health 票据类型码
     * @return 门诊类型为 true
     */
    public boolean isOutpatientInvoice(String invoiceTypeCode) {
        return OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT.equals(invoiceTypeCode);
    }
}
