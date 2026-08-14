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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        Map<String, ProductDefinitionDisplayDto> productMap = loadProductMap(rows, definitionMap);
        // 票面单位须为中文 display；quantity_unit 存术语 code（如 6/branch/bottle）
        Map<String, String> unitLabelByCode = quantityUnitDisplayResolver.warmLabelMap();
        for (ChargeItem row : rows) {
            BossChargeLineSnapshot snapshot = new BossChargeLineSnapshot();
            snapshot.setChargeItemId(row.getId());
            snapshot.setDefinitionId(row.getDefinitionId());
            ChargeItemDefinition definition = row.getDefinitionId() != null
                    ? definitionMap.get(row.getDefinitionId()) : null;
            snapshot.setInternalCode(resolveInternalCode(registrationFee, row, definition));
            ProductDefinitionDisplayDto product = resolveProduct(row, definition, productMap);
            snapshot.setItemName(resolveItemName(definition, product));
            applyProductGrade(snapshot, product);
            snapshot.setAmount(row.getTotalPrice());
            snapshot.setQuantity(row.getQuantityValue());
            snapshot.setUnit(quantityUnitDisplayResolver.toDisplay(row.getQuantityUnit(), unitLabelByCode));
            snapshot.setUnitPrice(row.getUnitPrice());
            lines.add(snapshot);
        }
        return lines;
    }

    /**
     * 写入医保目录编码与甲/乙/丙（自费票同样需要展开，不按票头医保类型跳过）。
     */
    private static void applyProductGrade(BossChargeLineSnapshot snapshot, ProductDefinitionDisplayDto product) {
        if (product == null) {
            return;
        }
        snapshot.setItemCode(firstNonBlank(product.getYbNo(), product.getProductBusNo()));
        snapshot.setChrgitmLv(product.getChrgitmLv());
        BossChrgitmLvMapping.Grade grade = BossChrgitmLvMapping.resolve(product.getChrgitmLv());
        snapshot.setListMedicalCareType(grade.medicalCareType());
        snapshot.setListRemark(firstNonBlank(product.getChrgitmLvText(), grade.remark()));
    }

    /**
     * 具体项目名：产品目录名优先，其次价目 {@code chargeName}（对齐 areahis listDetail.name）。
     *
     * <p>价目名若与统计大类同名（如「西药费」）且产品名为空，仍返回价目名由调用方处理；
     * 正常药品价目 {@code chargeName} 为药名。</p>
     */
    private String resolveItemName(ChargeItemDefinition definition, ProductDefinitionDisplayDto product) {
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

    private Map<String, ProductDefinitionDisplayDto> loadProductMap(
            List<ChargeItem> rows, Map<Long, ChargeItemDefinition> definitionMap) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem row : rows) {
            ChargeItemDefinition definition = row.getDefinitionId() != null && definitionMap != null
                    ? definitionMap.get(row.getDefinitionId()) : null;
            for (ProductDefinitionBatchLookup lookup : buildProductLookups(row, definition)) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (key == null || !dedupeKeys.add(key)) {
                    continue;
                }
                lookups.add(lookup);
            }
        }
        if (lookups.isEmpty()) {
            return new java.util.HashMap<>();
        }
        try {
            Map<String, ProductDefinitionDisplayDto> loaded =
                    productDefinitionQueryService.batchLoadByKeys(lookups);
            return loaded != null ? new java.util.HashMap<>(loaded) : new java.util.HashMap<>();
        } catch (Exception ex) {
            log.warn("Batch load product definitions failed, size={}, fallback single lookups", lookups.size(), ex);
            Map<String, ProductDefinitionDisplayDto> fallback = new java.util.HashMap<>();
            for (ProductDefinitionBatchLookup lookup : lookups) {
                try {
                    Map<String, ProductDefinitionDisplayDto> one =
                            productDefinitionQueryService.batchLoadByKeys(List.of(lookup));
                    if (one != null) {
                        fallback.putAll(one);
                    }
                } catch (Exception singleEx) {
                    log.warn(
                            "Single product lookup failed, table={}, id={}",
                            lookup.getProductTableName(),
                            lookup.getProductDefinitionId(),
                            singleEx);
                }
            }
            return fallback;
        }
    }

    private ProductDefinitionDisplayDto resolveProduct(
            ChargeItem row,
            ChargeItemDefinition definition,
            Map<String, ProductDefinitionDisplayDto> productMap) {
        List<ProductDefinitionBatchLookup> lookups = buildProductLookups(row, definition);
        if (lookups.isEmpty()) {
            return null;
        }
        for (ProductDefinitionBatchLookup lookup : lookups) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
            if (key == null) {
                continue;
            }
            ProductDefinitionDisplayDto hit = productMap != null ? productMap.get(key) : null;
            if (hit != null) {
                return hit;
            }
        }
        // 批量 Map 漏载时按 lookup 单条回源，避免整票丢失项目名/甲乙丙
        for (ProductDefinitionBatchLookup lookup : lookups) {
            try {
                Map<String, ProductDefinitionDisplayDto> one =
                        productDefinitionQueryService.batchLoadByKeys(List.of(lookup));
                if (one == null || one.isEmpty()) {
                    continue;
                }
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                ProductDefinitionDisplayDto hit = key != null ? one.get(key) : null;
                if (hit != null) {
                    if (productMap != null) {
                        productMap.put(key, hit);
                    }
                    return hit;
                }
            } catch (Exception ex) {
                log.warn(
                        "Fallback product lookup failed, table={}, id={}",
                        lookup.getProductTableName(),
                        lookup.getProductDefinitionId(),
                        ex);
            }
        }
        log.warn(
                "Einvoice product not resolved, chargeItemId={}, definitionId={}, lookups={}",
                row != null ? row.getId() : null,
                definition != null ? definition.getId() : null,
                lookups.size());
        return null;
    }

    /**
     * 收费行 → 产品 lookup：product_* → 价目 instance_* → 挂号 healthcare_service。
     *
     * <p>自费西药价目 {@code chargeName} 常为空，必须靠目录主键展开药名/甲乙丙。</p>
     */
    private static List<ProductDefinitionBatchLookup> buildProductLookups(
            ChargeItem chargeItem, ChargeItemDefinition definition) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>(3);
        if (chargeItem != null
                && StringUtils.hasText(chargeItem.getProductTable())
                && chargeItem.getProductId() != null) {
            lookups.add(lookupOf(chargeItem.getProductTable(), chargeItem.getProductId()));
        }
        if (definition != null
                && StringUtils.hasText(definition.getInstanceTable())
                && definition.getInstanceId() != null) {
            lookups.add(lookupOf(definition.getInstanceTable(), definition.getInstanceId()));
        }
        if (chargeItem != null
                && StringUtils.hasText(chargeItem.getServiceTable())
                && chargeItem.getServiceId() != null
                && TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(chargeItem.getServiceTable().trim())) {
            lookups.add(lookupOf(TableNameConstants.ADM_HEALTHCARE_SERVICE, chargeItem.getServiceId()));
        }
        return lookups;
    }

    private static ProductDefinitionBatchLookup lookupOf(String tableName, Long definitionId) {
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(tableName.trim());
        lookup.setProductDefinitionId(definitionId);
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
