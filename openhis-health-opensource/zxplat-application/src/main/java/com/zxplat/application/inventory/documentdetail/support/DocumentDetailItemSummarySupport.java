/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.documentdetail.support;

import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailItemSummaryRowDto;
import com.zxplat.application.inventory.documentdetail.dto.DocumentDetailRowDto;
import com.zxplat.domain.share.constants.DocumentDetailExportConstants;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 单据详情按品规汇总：对已换算的 supply 展示行按 {@code itemTable + itemId} 分桶聚合（仅进价）。
 *
 * <p>不另走领域汇总 Query；与明细分页/合计共用同一套筛选拉取链。</p>
 */
@Component
public class DocumentDetailItemSummarySupport {

    /**
     * 将明细展示行按品规聚合为汇总行（稳定按编码、品名排序）。
     *
     * @param detailRows 已 enrich + 操作单位换算的明细行；可为 null
     * @return 汇总行；无有效物品主键时跳过该行
     */
    public List<DocumentDetailItemSummaryRowDto> aggregateByItem(List<DocumentDetailRowDto> detailRows) {
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        if (detailRows == null || detailRows.isEmpty()) {
            return new ArrayList<>();
        }
        // 按品规分桶：仅有 itemId 的行参与；金额用 scaleCalc 后再加
        for (DocumentDetailRowDto row : detailRows) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            String itemTable = StringUtils.trimToEmpty(row.getItemTable());
            String dimensionKey = itemTable + "|" + row.getItemId();
            Bucket bucket = buckets.get(dimensionKey);
            if (bucket == null) {
                bucket = new Bucket();
                bucket.dimensionKey = dimensionKey;
                bucket.itemTable = row.getItemTable();
                bucket.itemId = row.getItemId();
                buckets.put(dimensionKey, bucket);
            }
            this.mergeRowIntoBucket(bucket, row);
        }
        List<DocumentDetailItemSummaryRowDto> result = new ArrayList<>(buckets.size());
        for (Bucket bucket : buckets.values()) {
            result.add(this.toSummaryRow(bucket));
        }
        // 编码优先、品名次之，保证导出/页面顺序稳定
        result.sort(Comparator
            .comparing((DocumentDetailItemSummaryRowDto row) -> StringUtils.trimToEmpty(row.getProductBusNo()))
            .thenComparing(row -> StringUtils.trimToEmpty(row.getDimensionName())));
        return result;
    }

    /**
     * 将一行明细并入分桶（数量代数加总；单价不一致则标空）。
     *
     * @param bucket 分桶累加器
     * @param row    明细行
     */
    private void mergeRowIntoBucket(Bucket bucket, DocumentDetailRowDto row) {
        bucket.lineCount++;
        // 品名/编码/规格：取首个非空，后续不一致不覆盖（同品规应一致）
        if (StringUtils.isEmpty(bucket.dimensionName) && StringUtils.isNotEmpty(row.getProductName())) {
            bucket.dimensionName = row.getProductName();
        }
        if (StringUtils.isEmpty(bucket.productBusNo) && StringUtils.isNotEmpty(row.getProductBusNo())) {
            bucket.productBusNo = row.getProductBusNo();
        }
        if (StringUtils.isEmpty(bucket.specification) && StringUtils.isNotEmpty(row.getSpecification())) {
            bucket.specification = row.getSpecification();
        }
        if (row.getPackageQuantity() != null) {
            bucket.packageQuantity = bucket.packageQuantity.add(row.getPackageQuantity());
        }
        if (row.getMinUnitQuantity() != null) {
            bucket.minUnitQuantity = bucket.minUnitQuantity.add(row.getMinUnitQuantity());
        }
        // 包装/最小单位名：首个非空；同桶出现不同单位名则置空（避免误导）
        bucket.packageUnitName = this.mergeUnitName(bucket.packageUnitName, row.getPackageUnitName());
        bucket.minUnitName = this.mergeUnitName(bucket.minUnitName, row.getMinUnitName());
        if (row.getTotalPrice() != null) {
            bucket.totalPrice = bucket.totalPrice.add(MonetaryAmountSupport.scaleCalc(row.getTotalPrice()));
        }
        // 进价单价：同桶多行不一致则为空（与发药报表品规维同口径）
        if (bucket.priceInconsistent) {
            return;
        }
        if (row.getPrice() == null) {
            // 有空单价：与已有非空冲突时标不一致
            if (bucket.priceSeen && bucket.price != null) {
                bucket.priceInconsistent = true;
                bucket.price = null;
            }
            bucket.priceSeen = true;
            return;
        }
        BigDecimal scaledPrice = MonetaryAmountSupport.scaleCalc(row.getPrice());
        if (!bucket.priceSeen) {
            bucket.price = scaledPrice;
            bucket.priceSeen = true;
            return;
        }
        if (bucket.price == null || bucket.price.compareTo(scaledPrice) != 0) {
            bucket.priceInconsistent = true;
            bucket.price = null;
        }
    }

    /**
     * 合并单位展示名：首个非空保留；后续不同则清空。
     *
     * @param current 分桶已有单位名
     * @param next    本行单位名
     * @return 合并后单位名；不一致返回 null
     */
    private String mergeUnitName(String current, String next) {
        String nextTrimmed = StringUtils.trimToNull(next);
        if (nextTrimmed == null) {
            return current;
        }
        if (current == null) {
            return nextTrimmed;
        }
        if (current.equals(nextTrimmed)) {
            return current;
        }
        // 同品规出现不同单位文案：清空，避免「数量+错误单位」
        return null;
    }

    /**
     * 分桶 → 汇总行 DTO。
     *
     * @param bucket 分桶
     * @return 汇总行
     */
    private DocumentDetailItemSummaryRowDto toSummaryRow(Bucket bucket) {
        DocumentDetailItemSummaryRowDto row = new DocumentDetailItemSummaryRowDto();
        row.setDimensionKey(bucket.dimensionKey);
        row.setSummaryDimension(DocumentDetailExportConstants.SUMMARY_DIMENSION_ITEM);
        row.setDimensionName(bucket.dimensionName);
        row.setItemTable(bucket.itemTable);
        row.setItemId(bucket.itemId);
        row.setProductBusNo(bucket.productBusNo);
        row.setSpecification(bucket.specification);
        row.setPackageQuantity(bucket.packageQuantity);
        row.setPackageUnitName(bucket.packageUnitName);
        row.setMinUnitQuantity(bucket.minUnitQuantity);
        row.setMinUnitName(bucket.minUnitName);
        if (bucket.priceInconsistent) {
            row.setPrice(null);
        } else {
            row.setPrice(bucket.price);
        }
        row.setTotalPrice(bucket.totalPrice);
        row.setLineCount(bucket.lineCount);
        return row;
    }

    /**
     * 品规分桶累加器（包内临时结构，不对外暴露）。
     */
    private static final class Bucket {
        /** 分桶键。 */
        private String dimensionKey;
        /** 物品表。 */
        private String itemTable;
        /** 物品主键。 */
        private Long itemId;
        /** 品名。 */
        private String dimensionName;
        /** 院内编码。 */
        private String productBusNo;
        /** 规格。 */
        private String specification;
        /** 包装数量合计。 */
        private BigDecimal packageQuantity = BigDecimal.ZERO;
        /** 包装单位名。 */
        private String packageUnitName;
        /** 最小数量合计。 */
        private BigDecimal minUnitQuantity = BigDecimal.ZERO;
        /** 最小单位名。 */
        private String minUnitName;
        /** 一致时的进价单价。 */
        private BigDecimal price;
        /** 是否已见过单价（含 null）。 */
        private boolean priceSeen;
        /** 单价已不一致。 */
        private boolean priceInconsistent;
        /** 进价合计。 */
        private BigDecimal totalPrice = BigDecimal.ZERO;
        /** 命中行数。 */
        private long lineCount;
    }
}
