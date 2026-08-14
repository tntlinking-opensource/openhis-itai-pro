/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 耗材使用报表患者侧金额 enrich（BR-RPT-OCU-004 / BR-RPT-ICU-004；金额刻度 BR-MON-001；包装锚 BR-MON-PKG-001）。
 *
 * <p>患者侧：售价来自 ChargeItem.total_price；进价来自 {@link HistoricalChargeItemPurchasePriceSupport}
 *（批号 {@code LOT_NUMBER_COST}，缺则同批号 {@code LOT_NUMBER_PRICE}）；
 * 行进价合计走包装锚三参；展示进价单价 {@link ChargeItemPackageUnitRatioSupport#toSplitUnitDisplayPrice}。
 * 科室领用侧：仅 Supply 落库进价；售价字段一律置空（领用非销售，禁止目录 SALE / 批号售价）。</p>
 *
 * <p>DOC-CONFLICT→已按 DD-报表中心-进售价口径与开立科室-2026-08 收敛。</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumableUsageReportAmountEnricher {

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价读侧（禁目录参考价）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装倍数与拆零展示单价。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品展示（取 packageUnitRatio）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 患者发放行 {@code dispenseId → account_id}（与金额匹配同口径，供费用性质装配）。
     *
     * @param lines 待解析行
     * @return 发放主键 → 挂账账户；无匹配不含键
     */
    public Map<Long, Long> mapAccountIdByDispenseId(List<? extends PatientDispenseAmountLine> lines) {
        Map<Long, Long> accountByDispenseId = new HashMap<>();
        if (lines == null || lines.isEmpty()) {
            return accountByDispenseId;
        }
        List<PatientDispenseAmountLine> castLines = new ArrayList<>(lines.size());
        for (PatientDispenseAmountLine line : lines) {
            if (line != null) {
                castLines.add(line);
            }
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(castLines);
        for (PatientDispenseAmountLine line : castLines) {
            if (line.getDispenseId() == null || accountByDispenseId.containsKey(line.getDispenseId())) {
                continue;
            }
            for (ChargeItem chargeItem : chargeItems) {
                if (!this.matches(line, chargeItem)) {
                    continue;
                }
                if (chargeItem.getAccountId() == null) {
                    continue;
                }
                accountByDispenseId.put(line.getDispenseId(), chargeItem.getAccountId());
                break;
            }
        }
        return accountByDispenseId;
    }

    /**
     * 回填患者发放明细行进价/售价（6 位行刻度；amount 保持售价别名）。
     *
     * @param lines 待 enrich 行；须已填 encounterId / dispenseId / deviceReqId / quantity
     */
    public void enrichPatientDispenseAmounts(List<PatientDispenseAmountLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(lines);
        // Pre：包装进价单价一次解析
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        // Pre：产品 Map 一次 — 判操作单位 + 包装倍数
        LinkedHashSet<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeItems, productByCompositeKey);
        // Pre：按操作单位计行额（包装×数量 / 拆零满包+零头）；禁止折拆零价再乘
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        for (PatientDispenseAmountLine line : lines) {
            if (line == null) {
                continue;
            }
            BigDecimal saleAmount = MonetaryAmountSupport.scaleCalc(this.sumMatchedSaleAmount(line, chargeItems));
            line.setAmount(saleAmount);
            line.setSaleAmount(saleAmount);
            BigDecimal saleUnit = this.resolveSaleUnitPrice(line, saleAmount);
            line.setSaleUnitPrice(saleUnit);
            BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(
                this.sumMatchedPurchaseAmount(line, chargeItems, purchaseByChargeItemId));
            line.setPurchaseAmount(purchaseAmount);
            // 展示进价单价：与收费 quantity_unit 对齐（禁止行额÷数量反推）
            BigDecimal purchaseUnit = this.resolvePurchaseDisplayUnitPrice(
                line,
                chargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
            line.setPurchaseUnitPrice(purchaseUnit);
        }
    }

    /**
     * 回填科室领用明细行进价；售价一律置空。
     *
     * <p>进价取 Supply 落库价；展示单价按包装倍数折拆零。领用非销售，禁止编售价 / 目录 SALE。</p>
     *
     * @param line 已映射明细行；须含 deviceDefId / quantity / 进价字段
     */
    public void enrichDeptIssueAmounts(PatientDispenseAmountLine line) {
        if (line == null) {
            return;
        }
        BigDecimal packagePurchaseUnit = MonetaryAmountSupport.scaleCalc(line.getPurchaseUnitPrice());
        BigDecimal ratio = this.resolveDevicePackageUnitRatio(line.getDeviceDefId());
        BigDecimal purchaseUnit =
            this.chargeItemPackageUnitRatioSupport.toSplitUnitDisplayPrice(packagePurchaseUnit, ratio);
        BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(line.getPurchaseAmount());
        line.setPurchaseAmount(purchaseAmount);
        line.setPurchaseUnitPrice(purchaseUnit);
        // 领用侧不算售价
        line.setSaleUnitPrice(null);
        line.setSaleAmount(null);
        line.setAmount(null);
    }

    /**
     * 按汇总 dimensionKey 累加明细行进价/售价净额。
     *
     * @param summaryRows 汇总行
     * @param detailRows  已 enrich 明细行
     * @param dimension   汇总维
     */
    public void aggregateSummaryPurchaseSaleAmounts(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> summaryRows,
        List<? extends ConsumableUsagePurchaseSaleDetailLine> detailRows,
        String dimension) {
        this.aggregateSummaryPurchaseSaleAmounts(summaryRows, detailRows, dimension, null);
    }

    /**
     * 按汇总 dimensionKey 累加明细行进价/售价净额（CATEGORY 维须传品规→类型）。
     *
     * @param summaryRows                汇总行
     * @param detailRows                 已 enrich 明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     */
    public void aggregateSummaryPurchaseSaleAmounts(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> summaryRows,
        List<? extends ConsumableUsagePurchaseSaleDetailLine> detailRows,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (summaryRows == null || summaryRows.isEmpty() || detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<String, ConsumableUsagePurchaseSaleSummaryLine> summaryByKey = new LinkedHashMap<>();
        for (ConsumableUsagePurchaseSaleSummaryLine summaryRow : summaryRows) {
            if (summaryRow != null && summaryRow.getDimensionKey() != null) {
                summaryByKey.put(summaryRow.getDimensionKey(), summaryRow);
            }
        }
        Map<String, List<BigDecimal>> purchaseAmountsByKey = new HashMap<>();
        Map<String, List<BigDecimal>> saleAmountsByKey = new HashMap<>();
        for (ConsumableUsagePurchaseSaleDetailLine detailRow : detailRows) {
            if (detailRow == null) {
                continue;
            }
            String bucketKey =
                this.resolveDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateAmount(purchaseAmountsByKey, bucketKey, detailRow.getPurchaseAmount());
            this.accumulateAmount(saleAmountsByKey, bucketKey, detailRow.getSaleAmount());
        }
        for (Map.Entry<String, ConsumableUsagePurchaseSaleSummaryLine> entry : summaryByKey.entrySet()) {
            String key = entry.getKey();
            ConsumableUsagePurchaseSaleSummaryLine summary = entry.getValue();
            BigDecimal purchaseNet = this.sumThenSettleOrNull(purchaseAmountsByKey.get(key));
            BigDecimal saleNet = this.sumThenSettleOrNull(saleAmountsByKey.get(key));
            summary.setPurchaseNetAmount(purchaseNet);
            summary.setSaleNetAmount(saleNet);
            summary.setNetAmount(saleNet);
        }
    }

    /**
     * 按患者汇总维累加金额（先 6 位加总再结算 2 位）。
     *
     * @param amounts 行金额列表
     * @return 结算刻度合计
     */
    public BigDecimal sumThenSettle(List<BigDecimal> amounts) {
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 汇总页脚进价净额（仅累加非空行）。
     *
     * @param rows 汇总行
     * @return 结算刻度或 null
     */
    public BigDecimal sumPurchaseNetAmount(List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows) {
        return this.sumSummaryAmountColumn(rows, true);
    }

    /**
     * 汇总页脚售价净额（仅累加非空行；netAmount 别名）。
     *
     * @param rows 汇总行
     * @return 结算刻度或 null
     */
    public BigDecimal sumSaleNetAmount(List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows) {
        return this.sumSummaryAmountColumn(rows, false);
    }

    /**
     * 批量加载与患者发放行相关的 ChargeItem（仅发放/请求锚点；禁止按就诊拉全量费用）。
     *
     * @param lines 明细行
     * @return 去重收费明细
     */
    private List<ChargeItem> loadChargeItems(List<PatientDispenseAmountLine> lines) {
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceReqIds = new LinkedHashSet<>();
        for (PatientDispenseAmountLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getDispenseId() != null) {
                dispenseIds.add(line.getDispenseId());
            }
            if (line.getDeviceReqId() != null) {
                deviceReqIds.add(line.getDeviceReqId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        // Pre：dispense_table 锚点一次 IN（报表汇总主路径）
        if (!dispenseIds.isEmpty()) {
            this.mergeChargeItems(
                chargeItemById,
                this.chargeItemQueryService.listChargeItemsForDeviceDispensePricingByDispenseIds(
                    dispenseIds, true));
            // Pre：遗留仅 service 锚定发放表时仍需 service_id IN
            ChargeItemQueryCommand dispenseAsServiceCmd = new ChargeItemQueryCommand();
            dispenseAsServiceCmd.setServiceIds(dispenseIds);
            dispenseAsServiceCmd.setExcludeSupersededSettledLines(true);
            this.mergeChargeItems(
                chargeItemById,
                this.chargeItemQueryService.listChargeItemsByServiceIds(dispenseAsServiceCmd));
        }
        // Pre：请求锚点挂费（未写 dispense 列的历史行）
        if (!deviceReqIds.isEmpty()) {
            ChargeItemQueryCommand requestCmd = new ChargeItemQueryCommand();
            requestCmd.setServiceIds(deviceReqIds);
            requestCmd.setExcludeSupersededSettledLines(true);
            this.mergeChargeItems(
                chargeItemById,
                this.chargeItemQueryService.listChargeItemsByServiceIds(requestCmd));
        }
        return new ArrayList<>(this.chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 合并收费明细到 id Map。
     *
     * @param chargeItemById 去重容器
     * @param chargeItems    待合并行
     */
    private void mergeChargeItems(Map<Long, ChargeItem> chargeItemById, List<ChargeItem> chargeItems) {
        if (chargeItems == null) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            chargeItemById.put(chargeItem.getId(), chargeItem);
        }
    }

    /**
     * 汇总与发放行匹配的 ChargeItem 售价金额。
     */
    private BigDecimal sumMatchedSaleAmount(PatientDispenseAmountLine line, List<ChargeItem> chargeItems) {
        Set<Long> usedIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem)) {
                continue;
            }
            if (!usedIds.add(chargeItem.getId())) {
                continue;
            }
            total = total.add(this.salePriceOf(chargeItem));
        }
        return total;
    }

    /**
     * 汇总与发放行匹配的 ChargeItem 进价金额。
     */
    private BigDecimal sumMatchedPurchaseAmount(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseByChargeItemId) {
        Set<Long> usedIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem)) {
                continue;
            }
            if (!usedIds.add(chargeItem.getId())) {
                continue;
            }
            BigDecimal purchase = purchaseByChargeItemId.get(chargeItem.getId());
            if (purchase != null) {
                total = total.add(purchase);
            }
        }
        return total;
    }

    /**
     * 解析患者侧售价单价（行额/数量；无法除时 null）。
     */
    private BigDecimal resolveSaleUnitPrice(PatientDispenseAmountLine line, BigDecimal saleAmount) {
        if (saleAmount == null || line.getQuantity() == null) {
            return null;
        }
        if (line.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return MonetaryAmountSupport.unitPriceFromLineTotal(saleAmount, line.getQuantity());
    }

    /**
     * 解析与收费数量单位对齐的展示进价单价。
     *
     * @param line                           明细行
     * @param chargeItems                    已加载收费明细
     * @param purchaseUnitByChargeItemId     包装进价单价
     * @param packageUnitRatioByChargeItemId 包装倍数
     * @param productByCompositeKey          产品展示 Map
     * @return 展示进价单价；无法解析时 null
     */
    private BigDecimal resolvePurchaseDisplayUnitPrice(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem) || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal packageUnit = purchaseUnitByChargeItemId.get(chargeItem.getId());
            if (packageUnit == null) {
                continue;
            }
            BigDecimal ratio = packageUnitRatioByChargeItemId.get(chargeItem.getId());
            ProductDefinitionDisplayDto product = null;
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null
                && productByCompositeKey != null
                && lookup.getProductTableName() != null
                && lookup.getProductDefinitionId() != null) {
                String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (compositeKey != null) {
                    product = productByCompositeKey.get(compositeKey);
                }
            }
            return this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
                packageUnit, ratio, chargeItem, product);
        }
        return null;
    }

    /**
     * 解析耗材品规包装倍数（领用侧展示折算）。
     *
     * @param deviceDefId 耗材定义主键
     * @return 包装倍数；无法解析时 null
     */
    private BigDecimal resolveDevicePackageUnitRatio(Long deviceDefId) {
        if (deviceDefId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
        lookup.setProductDefinitionId(deviceDefId);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId);
        if (compositeKey == null) {
            return null;
        }
        ProductDefinitionDisplayDto product = productByKey.get(compositeKey);
        if (product == null || product.getPackageUnitRatio() == null) {
            return null;
        }
        if (product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return product.getPackageUnitRatio();
    }

    /**
     * ChargeItem 是否锚定本发放行。
     */
    private boolean matches(PatientDispenseAmountLine line, ChargeItem chargeItem) {
        if (line.getDispenseId() != null) {
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
                && line.getDispenseId().equals(chargeItem.getServiceId())) {
                return true;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && line.getDispenseId().equals(chargeItem.getDispenseId())) {
                return true;
            }
        }
        if (line.getDeviceReqId() != null
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())
            && line.getDeviceReqId().equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 取收费明细行售价金额。
     */
    private BigDecimal salePriceOf(ChargeItem chargeItem) {
        if (chargeItem.getTotalPrice() == null) {
            return BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 明细行 → 汇总 dimensionKey。
     *
     * @param detailRow                  明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolveDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(detailRow.getSourceCode())) {
            return this.resolveDeptDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
        }
        return this.resolvePatientDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
    }

    /**
     * 患者侧明细 → dimensionKey。
     *
     * @param detailRow                  明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolvePatientDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            Long orgId = detailRow.getOrganizationId();
            if (orgId == null) {
                return "DEPARTMENT:";
            }
            return "DEPARTMENT:" + orgId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
            Long locationId = detailRow.getLocationId();
            if (locationId == null) {
                return "LOCATION:";
            }
            return "LOCATION:" + locationId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long deviceDefId = detailRow.getDeviceDefId();
            if (deviceDefId == null) {
                return "ITEM:";
            }
            return "ITEM:" + deviceDefId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            return this.resolveCategoryDimensionKey(detailRow.getDeviceDefId(), categoryCodeByDeviceDefId);
        }
        Long patientId = detailRow.getPatientId();
        if (patientId == null) {
            return "PATIENT:";
        }
        return "PATIENT:" + patientId;
    }

    /**
     * 科室侧明细 → dimensionKey（LOCATION 维统一 {@code LOCATION:}，禁止仅认 PHARMACY）。
     *
     * @param detailRow                  明细行
     * @param dimension                  请求维（可为 LOCATION / PHARMACY）
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolveDeptDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)
            || ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long deviceDefId = detailRow.getDeviceDefId();
            if (deviceDefId == null) {
                return "ITEM:";
            }
            return "ITEM:" + deviceDefId;
        }
        // 耗材房：请求 LOCATION 或领域 PHARMACY 一律对外 LOCATION: 前缀
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)
            || DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = detailRow.getLocationId();
            if (locationId == null) {
                return "LOCATION:";
            }
            return "LOCATION:" + locationId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            return this.resolveCategoryDimensionKey(detailRow.getDeviceDefId(), categoryCodeByDeviceDefId);
        }
        Long orgId = detailRow.getOrganizationId();
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * CATEGORY 维分桶键：品规映射类型码；缺失落入空类型桶。
     *
     * @param deviceDefId                耗材定义主键
     * @param categoryCodeByDeviceDefId  品规→类型
     * @return {@code CATEGORY:} + code
     */
    private String resolveCategoryDimensionKey(
        Long deviceDefId,
        Map<Long, String> categoryCodeByDeviceDefId) {
        String categoryCode = "";
        if (deviceDefId != null && categoryCodeByDeviceDefId != null) {
            String mapped = categoryCodeByDeviceDefId.get(deviceDefId);
            if (mapped != null) {
                categoryCode = mapped;
            }
        }
        return "CATEGORY:" + categoryCode;
    }

    /**
     * 向分桶累加金额列表。
     */
    private void accumulateAmount(Map<String, List<BigDecimal>> bucket, String key, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        bucket.computeIfAbsent(key, ignored -> new ArrayList<>()).add(amount);
    }

    /**
     * 6 位加总后结算；无有效行时 null。
     */
    private BigDecimal sumThenSettleOrNull(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return null;
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 汇总列加总（purchase 或 sale）。
     */
    private BigDecimal sumSummaryAmountColumn(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows,
        boolean purchaseColumn) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (ConsumableUsagePurchaseSaleSummaryLine row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal value;
            if (purchaseColumn) {
                value = row.getPurchaseNetAmount();
            } else {
                value = row.getSaleNetAmount();
                if (value == null) {
                    value = row.getNetAmount();
                }
            }
            if (value != null) {
                amounts.add(value);
            }
        }
        if (amounts.isEmpty()) {
            return null;
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 患者发放金额 enrich 行契约（Support 与 Feature DTO 之间的适配面）。
     */
    public interface PatientDispenseAmountLine extends ConsumableUsagePurchaseSaleDetailLine {

        /** @return 就诊主键 */
        Long getEncounterId();

        /** @return 发放主键 */
        Long getDispenseId();

        /** @return 耗材请求主键；可空 */
        Long getDeviceReqId();

        /** 写入匹配后的行售价别名 amount。 */
        void setAmount(BigDecimal amount);
    }

    /**
     * 科室领用金额 enrich 行契约（与患者侧共用明细 DTO）。
     */
    public interface DeptIssueAmountLine extends PatientDispenseAmountLine {
    }

    /**
     * 耗材报表明细行进价/售价 enrich 契约。
     */
    public interface ConsumableUsagePurchaseSaleDetailLine {

        /** @return 来源 code */
        String getSourceCode();

        /** @return 患者主键 */
        Long getPatientId();

        /** @return 科室组织主键 */
        Long getOrganizationId();

        /** @return 库位主键 */
        Long getLocationId();

        /** @return 耗材定义主键 */
        Long getDeviceDefId();

        /** @return 批号；科室领用侧读 {@code LOT_NUMBER_PRICE} 必填 */
        String getLotNumber();

        /** @return 数量 */
        BigDecimal getQuantity();

        /** @return 售价别名 amount */
        BigDecimal getAmount();

        /** @return 进价单价 */
        BigDecimal getPurchaseUnitPrice();

        /** @return 进价总额 */
        BigDecimal getPurchaseAmount();

        /** @return 售价单价 */
        BigDecimal getSaleUnitPrice();

        /** @return 售价总额 */
        BigDecimal getSaleAmount();

        /** @param purchaseUnitPrice 进价单价 */
        void setPurchaseUnitPrice(BigDecimal purchaseUnitPrice);

        /** @param purchaseAmount 进价总额 */
        void setPurchaseAmount(BigDecimal purchaseAmount);

        /** @param saleUnitPrice 售价单价 */
        void setSaleUnitPrice(BigDecimal saleUnitPrice);

        /** @param saleAmount 售价总额 */
        void setSaleAmount(BigDecimal saleAmount);
    }

    /**
     * 耗材报表汇总行进价/售价 enrich 契约。
     */
    public interface ConsumableUsagePurchaseSaleSummaryLine {

        /** @return 维度键 */
        String getDimensionKey();

        /** @return 进价净额 */
        BigDecimal getPurchaseNetAmount();

        /** @return 售价净额 */
        BigDecimal getSaleNetAmount();

        /** @return 售价净额别名 */
        BigDecimal getNetAmount();

        /** @param purchaseNetAmount 进价净额 */
        void setPurchaseNetAmount(BigDecimal purchaseNetAmount);

        /** @param saleNetAmount 售价净额 */
        void setSaleNetAmount(BigDecimal saleNetAmount);

        /** @param netAmount 售价净额别名 */
        void setNetAmount(BigDecimal netAmount);
    }
}
