/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.support;

import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailFinTypeSummaryQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailChargeRowQueryModel;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.share.constants.FeeDetailReportConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 费用明细报表多维净汇总装配（AS-RPT-008/009）。
 *
 * <p>入口 {@link #assembleSummaryRows}：按 SUMMARY 维度分派 FIN_TYPE / DEPARTMENT / FEE_CATEGORY / ITEM。
 * FIN_TYPE：definition → fin_type enrich + 合并（委托
 * {@link DailySettlementFinTypeAmountLoader#mapFinTypeCodeByChargeItemId}）。
 * DEPARTMENT / FEE_CATEGORY / ITEM：按需物化 {@code cachedChargeRows} 后按维分桶，
 * 禁止 FIN_TYPE 默认路径全窗 RowQueryModel。</p>
 *
 * <p>金额列：进价合计（历史定价）/ 售价合计（{@code total_price}）/ 实收合计（与售价同口径）。</p>
 */
@Component
@RequiredArgsConstructor
public class FeeDetailReportFinTypeSummaryAssembler {

    /** 收费明细批量读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 定价行 fin_type 解析（与日结口径一致）。 */
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 历史进价解析（BR-PRICE-SRC-001）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装换算比（BR-MON-PKG-001 行进价合计）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品定义批量读侧（取 packageUnitRatio / 品规展示名）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 开立科室名称批量读侧（DEPARTMENT 维）。 */
    private final OrganizationQueryService organizationQueryService;

    /**
     * 按汇总维度装配汇总行（空白维回落 FIN_TYPE）。
     *
     * @param summaryDimension   汇总维度 code；空则 {@link FeeDetailReportConstants#DEFAULT_SUMMARY_DIMENSION}
     * @param query              已预解析 scope 的查询（非 FIN_TYPE 须已缓存 chargeRows）
     * @param domainRows         域层按 definition 汇总行（仅 FIN_TYPE 使用）
     * @param finTypeLabelByCode fin_type_code → 展示名（仅 FIN_TYPE）
     * @param incompleteExCode   定价锚点无法解析时的业务异常码
     * @param incompleteMessage  异常提示
     * @return 按维合并后的汇总行（行金额 6 位）
     */
    public List<FeeDetailReportSummaryRowDto> assembleSummaryRows(
        String summaryDimension,
        OutpatientFeeDetailPageQuery query,
        List<FeeDetailFinTypeSummaryQueryModel> domainRows,
        Map<String, String> finTypeLabelByCode,
        String incompleteExCode,
        String incompleteMessage) {
        String dimension = this.normalizeSummaryDimension(summaryDimension);
        // FIN_TYPE：沿用 definition 分桶 + fin_type enrich，再回填维度列
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_FIN_TYPE.equals(dimension)) {
            List<FeeDetailReportSummaryRowDto> finTypeRows =
                this.assembleMergedFinTypeRows(
                    query, domainRows, finTypeLabelByCode, incompleteExCode, incompleteMessage);
            this.fillFinTypeDimensionMeta(finTypeRows);
            return finTypeRows;
        }
        // Pre：非 FIN_TYPE 维须物化 cachedChargeRows（FIN_TYPE 默认路径禁止全窗 RowQueryModel）
        if (query != null
            && (query.getCachedChargeRows() == null || query.getCachedChargeRows().isEmpty())) {
            this.chargeItemQueryService.listOutpatientFeeDetailChargeRows(query);
        }
        // 其它维：直接从 cachedChargeRows 分桶（summarize*ByFinType 已物化）
        CachedRowPurchasePricingContext pricingContext = null;
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows = null;
        if (query != null) {
            cachedRows = query.getCachedChargeRows();
        }
        if (cachedRows != null && !cachedRows.isEmpty()) {
            pricingContext = this.loadPurchasePricingContextFromCachedRows(cachedRows);
        }
        List<FeeDetailReportSummaryRowDto> rows =
            this.assembleCachedChargeRowDimensionRows(dimension, query, pricingContext);
        // 展示单价仅品规维需要；禁止非 ITEM 维重复批号查价
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            this.enrichSummaryUnitPricesFromCachedRows(rows, dimension, query, pricingContext);
        }
        return rows;
    }

    /**
     * 将域层 definition 汇总 enrich 为 fin_type 行并合并同码金额。
     *
     * @param query              已预解析 scope 的查询
     * @param domainRows         域层按 definition 汇总行
     * @param finTypeLabelByCode fin_type_code → 展示名
     * @param incompleteExCode   定价锚点无法解析时的业务异常码
     * @param incompleteMessage  异常提示
     * @return 按 fin_type 合并后的汇总行（行金额 6 位）
     */
    public List<FeeDetailReportSummaryRowDto> assembleMergedFinTypeRows(
        OutpatientFeeDetailPageQuery query,
        List<FeeDetailFinTypeSummaryQueryModel> domainRows,
        Map<String, String> finTypeLabelByCode,
        String incompleteExCode,
        String incompleteMessage) {
        List<FeeDetailReportSummaryRowDto> result = new ArrayList<>();
        // 无 definition 汇总行 → 空表（页脚由调用方收 0）
        if (domainRows == null || domainRows.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        // 收集待 enrich 的定价锚点，供批量解析 fin_type
        for (FeeDetailFinTypeSummaryQueryModel domainRow : domainRows) {
            // 仅纳入带 definition 的有效汇总行
            if (domainRow != null && domainRow.getDefinitionId() != null) {
                definitionIds.add(domainRow.getDefinitionId());
            }
        }
        FinTypeResolveBundle resolveBundle =
            this.resolveFinTypeAndPurchaseByDefinition(
                query, definitionIds, incompleteExCode, incompleteMessage);
        Map<String, FeeDetailReportSummaryRowDto> mergedByFinType = new LinkedHashMap<>();
        // 按 fin_type 分桶：多 definition 同码合并金额与行数
        for (FeeDetailFinTypeSummaryQueryModel domainRow : domainRows) {
            // 跳过无 definition 的脏汇总行，避免空键分桶
            if (domainRow == null || domainRow.getDefinitionId() == null) {
                continue;
            }
            String finTypeCode = resolveBundle.finTypeByDefinitionId.get(domainRow.getDefinitionId());
            // 汇总行存在 definition 但无法解析 fin_type → 整单失败，避免静默丢行
            if (StringUtils.isEmpty(finTypeCode)) {
                throw new BusinessException(incompleteExCode, incompleteMessage);
            }
            String key = finTypeCode.trim();
            FeeDetailReportSummaryRowDto row = mergedByFinType.get(key);
            // 首次命中该 fin_type：建行并回落展示名
            if (row == null) {
                row = new FeeDetailReportSummaryRowDto();
                row.setFinTypeCode(key);
                String display = null;
                // 优先取术语字典展示名
                if (finTypeLabelByCode != null) {
                    display = finTypeLabelByCode.get(key);
                }
                // 字典未命中时展示码本身，保证表格非空
                if (StringUtils.isEmpty(display)) {
                    display = key;
                }
                row.setFinTypeDisplay(display);
                this.initAmountZeros(row);
                mergedByFinType.put(key, row);
            }
            BigDecimal saleTotal = MonetaryAmountSupport.nullToZero(domainRow.getLineAmountTotal());
            BigDecimal purchaseTotal = MonetaryAmountSupport.nullToZero(
                resolveBundle.purchaseByDefinitionId.get(domainRow.getDefinitionId()));
            this.addSaleAndPurchase(row, saleTotal, purchaseTotal);
            row.setReceivableAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getReceivableAmountTotal())
                    .add(MonetaryAmountSupport.nullToZero(domainRow.getReceivableAmountTotal()))));
            row.setDiscountedAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getDiscountedAmountTotal())
                    .add(MonetaryAmountSupport.nullToZero(domainRow.getDiscountedAmountTotal()))));
            long lineCount = 0L;
            // 累加前取已有行数，空则从 0 起算
            if (row.getLineCount() != null) {
                lineCount = row.getLineCount();
            }
            row.setLineCount(lineCount + domainRow.getLineCount());
            row.setQuantityTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getQuantityTotal())
                    .add(MonetaryAmountSupport.nullToZero(domainRow.getQuantityTotal()))));
        }
        result.addAll(mergedByFinType.values());
        return result;
    }

    /**
     * 汇总行 → 页脚合计（结算刻度 2 位）。
     *
     * @param rows 汇总行
     * @return 页脚合计
     */
    public FeeDetailReportSummaryFooterDto assembleFooter(
        List<FeeDetailReportSummaryRowDto> rows) {
        FeeDetailReportSummaryFooterDto footer = new FeeDetailReportSummaryFooterDto();
        BigDecimal purchase = BigDecimal.ZERO;
        BigDecimal sale = BigDecimal.ZERO;
        BigDecimal actualReceived = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal discounted = BigDecimal.ZERO;
        BigDecimal lineAmount = BigDecimal.ZERO;
        long lineCountTotal = 0L;
        BigDecimal quantityTotal = BigDecimal.ZERO;
        // 先按行级 6 位代数加总，再统一 scaleSettle（禁止逐行先 2 位再加）
        if (rows != null) {
            for (FeeDetailReportSummaryRowDto row : rows) {
                // 跳过空行，避免 NPE 打断页脚合计
                if (row == null) {
                    continue;
                }
                purchase = purchase.add(MonetaryAmountSupport.nullToZero(row.getPurchaseAmountTotal()));
                sale = sale.add(MonetaryAmountSupport.nullToZero(row.getSaleAmountTotal()));
                quantityTotal = quantityTotal.add(MonetaryAmountSupport.nullToZero(row.getQuantityTotal()));
                actualReceived =
                    actualReceived.add(MonetaryAmountSupport.nullToZero(row.getActualReceivedAmountTotal()));
                receivable = receivable.add(MonetaryAmountSupport.nullToZero(row.getReceivableAmountTotal()));
                discounted = discounted.add(MonetaryAmountSupport.nullToZero(row.getDiscountedAmountTotal()));
                lineAmount = lineAmount.add(MonetaryAmountSupport.nullToZero(row.getLineAmountTotal()));
                // 行数仅累加有值的汇总行
                if (row.getLineCount() != null) {
                    lineCountTotal = lineCountTotal + row.getLineCount();
                }
            }
        }
        footer.setPurchaseAmountTotal(MonetaryAmountSupport.scaleSettle(purchase));
        footer.setSaleAmountTotal(MonetaryAmountSupport.scaleSettle(sale));
        footer.setQuantityTotal(MonetaryAmountSupport.scaleCalc(quantityTotal));
        footer.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleSettle(actualReceived));
        footer.setReceivableAmountTotal(MonetaryAmountSupport.scaleSettle(receivable));
        footer.setDiscountedAmountTotal(MonetaryAmountSupport.scaleSettle(discounted));
        footer.setLineAmountTotal(MonetaryAmountSupport.scaleSettle(lineAmount));
        footer.setLineCountTotal(lineCountTotal);
        return footer;
    }

    /**
     * 空白/空串汇总维回落默认 FIN_TYPE。
     *
     * @param summaryDimension 请求维度
     * @return 规范化维度 code
     */
    private String normalizeSummaryDimension(String summaryDimension) {
        // 未传汇总维时回落默认 FIN_TYPE，保证入口可分派
        if (StringUtils.isEmpty(summaryDimension)) {
            return FeeDetailReportConstants.DEFAULT_SUMMARY_DIMENSION;
        }
        return summaryDimension.trim();
    }

    /**
     * FIN_TYPE 行回填维度列（与 finType* 同值，供前端统一读 dimension*）。
     *
     * @param rows fin_type 合并行
     */
    private void fillFinTypeDimensionMeta(List<FeeDetailReportSummaryRowDto> rows) {
        // 无行可回填则直接返回
        if (rows == null || rows.isEmpty()) {
            return;
        }
        for (FeeDetailReportSummaryRowDto row : rows) {
            // 跳过空行，避免写坏维度元数据
            if (row == null) {
                continue;
            }
            row.setSummaryDimension(FeeDetailReportConstants.SUMMARY_DIMENSION_FIN_TYPE);
            row.setDimensionKey(row.getFinTypeCode());
            row.setDimensionName(row.getFinTypeDisplay());
        }
    }

    /**
     * 从 cachedChargeRows 按 DEPARTMENT / FEE_CATEGORY / ITEM 分桶净汇总。
     *
     * @param dimension 已规范化非 FIN_TYPE 维度
     * @param query     须含 cachedChargeRows
     * @return 分维汇总行
     */
    private List<FeeDetailReportSummaryRowDto> assembleCachedChargeRowDimensionRows(
        String dimension,
        OutpatientFeeDetailPageQuery query,
        CachedRowPurchasePricingContext pricingContext) {
        List<FeeDetailReportSummaryRowDto> result = new ArrayList<>();
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows = null;
        // 取 summarize 已物化的候选收费行
        if (query != null) {
            cachedRows = query.getCachedChargeRows();
        }
        // 无缓存候选行 → 空表（域层 summarize 未物化时不应静默再扫全窗）
        if (cachedRows == null || cachedRows.isEmpty()) {
            return result;
        }
        // Pre：批量进价（chargeItemId → 未带符号行进价；调用方已一次 load，禁止本方法内再查）
        Map<Long, BigDecimal> purchaseByChargeItemId = Map.of();
        if (pricingContext != null && pricingContext.purchaseAmountByChargeItemId != null) {
            purchaseByChargeItemId = pricingContext.purchaseAmountByChargeItemId;
        }
        // Pre：科室 / 品规展示名批量预取
        Map<Long, String> orgNameById = Map.of();
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey = Map.of();
        // 科室维：批量预取开立科室名
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            orgNameById = this.loadOrganizationNamesForCachedRows(cachedRows);
        // 品目维：复用进价上下文中的产品 Map，避免二次 loadAsMap
        } else if (FeeDetailReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            if (pricingContext != null && pricingContext.productByCompositeKey != null) {
                productByCompositeKey = pricingContext.productByCompositeKey;
            }
            if (productByCompositeKey.isEmpty()) {
                productByCompositeKey = this.loadProductDisplayMapForCachedRows(cachedRows);
            }
        }
        Map<String, FeeDetailReportSummaryRowDto> mergedByKey = new LinkedHashMap<>();
        // Asm：按 payment 链路行展开，售价/进价同符号口径
        for (OutpatientFeeDetailChargeRowQueryModel chargeRow : cachedRows) {
            // 缺收费事实的链路行不参与分桶
            if (chargeRow == null || chargeRow.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeRow.getChargeItem();
            String bucketKey = this.resolveDimensionBucketKey(dimension, chargeItem);
            // ITEM 无 definitionId 跳过脏行；其它维 resolve 已回落 UNKNOWN
            if (bucketKey == null) {
                continue;
            }
            FeeDetailReportSummaryRowDto row = mergedByKey.get(bucketKey);
            // 首次命中该分桶键：建行并写入维度展示名
            if (row == null) {
                row = this.createDimensionSummaryRow(
                    dimension, bucketKey, chargeItem, orgNameById, productByCompositeKey);
                mergedByKey.put(bucketKey, row);
            }
            BigDecimal saleRaw = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
            BigDecimal signedSale = this.applyPaymentSign(saleRaw, chargeRow.getPaymentContext());
            BigDecimal purchaseRaw = BigDecimal.ZERO;
            // 有 chargeItemId 时取批量进价，缺映射按 0
            if (chargeItem.getId() != null) {
                BigDecimal mappedPurchase = purchaseByChargeItemId.get(chargeItem.getId());
                // 命中历史进价则覆盖默认 0
                if (mappedPurchase != null) {
                    purchaseRaw = mappedPurchase;
                }
            }
            BigDecimal signedPurchase = this.applyPaymentSign(purchaseRaw, chargeRow.getPaymentContext());
            // 非 FIN_TYPE：应收/折后/行金额/实收与售价同口径，保持列契约完整
            this.addSaleAndPurchase(row, signedSale, signedPurchase);
            row.setReceivableAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getReceivableAmountTotal()).add(signedSale)));
            row.setDiscountedAmountTotal(MonetaryAmountSupport.scaleCalc(
                MonetaryAmountSupport.nullToZero(row.getDiscountedAmountTotal()).add(signedSale)));
            this.addSignedQuantity(row, chargeItem.getQuantityValue(), chargeRow.getPaymentContext());
            long lineCount = 0L;
            // 累加前取已有行数，空则从 0 起算
            if (row.getLineCount() != null) {
                lineCount = row.getLineCount();
            }
            row.setLineCount(lineCount + 1L);
        }
        result.addAll(mergedByKey.values());
        return result;
    }

    /**
     * 解析分桶键；ITEM 无 definition 返回 null（跳过）；科室/大类脏数据回落 UNKNOWN。
     *
     * @param dimension  汇总维
     * @param chargeItem 收费行
     * @return 分桶键；ITEM 缺锚点时 null
     */
    private String resolveDimensionBucketKey(String dimension, ChargeItem chargeItem) {
        // 科室维：按开立科室 id 分桶
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            // 无开立科室回落 UNKNOWN，避免脏行整单失败
            if (chargeItem.getRequestingOrgId() == null) {
                return FeeDetailReportConstants.UNKNOWN_DIMENSION_KEY;
            }
            return String.valueOf(chargeItem.getRequestingOrgId());
        }
        // 费用大类维：按 service_table 归类分桶
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_FEE_CATEGORY.equals(dimension)) {
            String feeCategory = this.resolveFeeCategoryCodeOrUnknown(chargeItem.getServiceTable());
            return feeCategory;
        }
        // 品目维：按 definitionId 分桶
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            // 无定价定义无法归品目，返回 null 由调用方跳过
            if (chargeItem.getDefinitionId() == null) {
                return null;
            }
            return String.valueOf(chargeItem.getDefinitionId());
        }
        return FeeDetailReportConstants.UNKNOWN_DIMENSION_KEY;
    }

    /**
     * service_table → 费用大类 code；无法归类回落 UNKNOWN（汇总不因脏行整单失败）。
     *
     * @param serviceTable ChargeItem.service_table
     * @return 大类 code 或 UNKNOWN
     */
    private String resolveFeeCategoryCodeOrUnknown(String serviceTable) {
        // 空 service_table 无法归类 → UNKNOWN
        if (StringUtils.isEmpty(serviceTable)) {
            return FeeDetailReportConstants.UNKNOWN_DIMENSION_KEY;
        }
        String normalized = serviceTable.trim();
        // 药品申请/发药 → 药品大类
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION;
        }
        // 诊疗服务申请 → 诊疗大类
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY;
        }
        // 耗材申请/发货 → 耗材大类
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE;
        }
        // 挂号医疗服务 → 挂号大类
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(normalized)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER;
        }
        return FeeDetailReportConstants.UNKNOWN_DIMENSION_KEY;
    }

    /**
     * 新建分维汇总行并写入维度展示名。
     *
     * @param dimension              汇总维
     * @param bucketKey              分桶键
     * @param chargeItem             首行收费事实（ITEM 取产品锚点）
     * @param orgNameById            科室 id → 名称
     * @param productByCompositeKey  产品复合键 → 展示
     * @return 已初始化金额为 0 的汇总行
     */
    private FeeDetailReportSummaryRowDto createDimensionSummaryRow(
        String dimension,
        String bucketKey,
        ChargeItem chargeItem,
        Map<Long, String> orgNameById,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        FeeDetailReportSummaryRowDto row = new FeeDetailReportSummaryRowDto();
        row.setSummaryDimension(dimension);
        row.setDimensionKey(bucketKey);
        row.setDimensionName(
            this.resolveDimensionDisplayName(
                dimension, bucketKey, chargeItem, orgNameById, productByCompositeKey));
        this.initAmountZeros(row);
        return row;
    }

    /**
     * 解析维度展示名。
     *
     * @param dimension             汇总维
     * @param bucketKey             分桶键
     * @param chargeItem            收费行（ITEM 取产品）
     * @param orgNameById           科室名
     * @param productByCompositeKey 产品展示
     * @return 非空展示名
     */
    private String resolveDimensionDisplayName(
        String dimension,
        String bucketKey,
        ChargeItem chargeItem,
        Map<Long, String> orgNameById,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        // 科室维：优先组织名称，缺省回落键或「未知科室」
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            // UNKNOWN 键用固定「未知科室」文案
            if (FeeDetailReportConstants.UNKNOWN_DIMENSION_KEY.equals(bucketKey)) {
                return FeeDetailReportConstants.UNKNOWN_DEPARTMENT_DISPLAY;
            }
            // 有开立科室且已预取名称时取组织名
            if (chargeItem.getRequestingOrgId() != null && orgNameById != null) {
                String orgName = orgNameById.get(chargeItem.getRequestingOrgId());
                // 名称非空则作为展示，避免空白格
                if (StringUtils.isNotEmpty(orgName)) {
                    return orgName;
                }
            }
            return bucketKey;
        }
        // 费用大类维：code → 中文标签
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_FEE_CATEGORY.equals(dimension)) {
            return this.resolveFeeCategoryLabel(bucketKey);
        }
        // 品目维：优先品规名，缺省回落 definitionId 键
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            String productName = this.resolveProductDisplayName(chargeItem, productByCompositeKey);
            // 解析到品规名则展示名，否则用分桶键
            if (StringUtils.isNotEmpty(productName)) {
                return productName;
            }
            return bucketKey;
        }
        return bucketKey;
    }

    /**
     * 费用大类 code → 展示文案（与 OutpatientFeeDetailAssembler 同口径）。
     *
     * @param feeCategoryCode 大类 code 或 UNKNOWN
     * @return 展示名
     */
    private String resolveFeeCategoryLabel(String feeCategoryCode) {
        // 药品大类标签
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_MEDICATION_LABEL;
        }
        // 诊疗大类标签
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_ACTIVITY_LABEL;
        }
        // 耗材大类标签
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_DEVICE_LABEL;
        }
        // 挂号大类标签
        if (OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER.equals(feeCategoryCode)) {
            return OutpatientFeeDetailConstants.FEE_CATEGORY_REGISTER_LABEL;
        }
        return FeeDetailReportConstants.UNKNOWN_FEE_CATEGORY_DISPLAY;
    }

    /**
     * 从产品 Map 解析品规展示名。
     *
     * @param chargeItem            收费行
     * @param productByCompositeKey 产品展示 Map
     * @return 产品名；无法解析时 null
     */
    private String resolveProductDisplayName(
        ChargeItem chargeItem,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        // 无预取产品 Map 则无法展示品规名
        if (productByCompositeKey == null || productByCompositeKey.isEmpty()) {
            return null;
        }
        ProductDefinitionBatchLookup lookup =
            ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
        // 收费行缺产品锚点 → 无法查展示名
        if (lookup == null) {
            return null;
        }
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(), lookup.getProductDefinitionId());
        // 复合键拼不出则无法命中 Map
        if (StringUtils.isEmpty(compositeKey)) {
            return null;
        }
        ProductDefinitionDisplayDto product = productByCompositeKey.get(compositeKey);
        // Map 未命中该品规
        if (product == null) {
            return null;
        }
        // 优先产品名，其次商品名
        if (StringUtils.isNotEmpty(product.getProductName())) {
            return product.getProductName();
        }
        return product.getMerchandiseName();
    }

    /**
     * 批量加载开立科室名称。
     *
     * @param cachedRows 缓存候选行
     * @return orgId → name
     */
    private Map<Long, String> loadOrganizationNamesForCachedRows(
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows) {
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargeRowQueryModel chargeRow : cachedRows) {
            // 缺收费事实的链路行不收集科室
            if (chargeRow == null || chargeRow.getChargeItem() == null) {
                continue;
            }
            Long orgId = chargeRow.getChargeItem().getRequestingOrgId();
            // 仅收集有开立科室的 id
            if (orgId != null) {
                orgIds.add(orgId);
            }
        }
        // 本窗无科室锚点 → 空 Map，展示侧回落键
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = this.organizationQueryService.findOrganizationNamesByIds(orgIds);
        // 读侧返回 null 时按空 Map 处理，避免 NPE
        if (names == null) {
            return Map.of();
        }
        return names;
    }

    /**
     * 批量加载品规展示 Map。
     *
     * @param cachedRows 缓存候选行
     * @return 复合键 → 展示 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayMapForCachedRows(
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows) {
        LinkedHashSet<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargeRowQueryModel chargeRow : cachedRows) {
            // 缺收费事实的链路行不收集产品锚点
            if (chargeRow == null || chargeRow.getChargeItem() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeRow.getChargeItem());
            // 有产品锚点才入批量查询集
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        // 本窗无品规锚点 → 空 Map
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
    }

    /**
     * 批量加载缓存收费行的批号进价与包装倍数（与明细 enrich 同路径）。
     *
     * @param cachedRows 缓存候选行
     * @return 进价行额 / 批号包装单价 / 包装倍数
     */
    private CachedRowPurchasePricingContext loadPurchasePricingContextFromCachedRows(
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows) {
        CachedRowPurchasePricingContext context = new CachedRowPurchasePricingContext();
        context.purchaseAmountByChargeItemId = Map.of();
        context.purchasePackageUnitByChargeItemId = Map.of();
        context.packageUnitRatioByChargeItemId = Map.of();
        context.alignedPurchaseDisplayUnitByChargeItemId = Map.of();
        if (cachedRows == null || cachedRows.isEmpty()) {
            return context;
        }
        Map<Long, ChargeItem> uniqueById = new LinkedHashMap<>();
        for (OutpatientFeeDetailChargeRowQueryModel chargeRow : cachedRows) {
            if (chargeRow == null || chargeRow.getChargeItem() == null
                || chargeRow.getChargeItem().getId() == null) {
                continue;
            }
            uniqueById.put(chargeRow.getChargeItem().getId(), chargeRow.getChargeItem());
        }
        if (uniqueById.isEmpty()) {
            return context;
        }
        List<ChargeItem> pricingChargeItems = new ArrayList<>(uniqueById.values());
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (ChargeItem chargeItem : pricingChargeItems) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null) {
                productLookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(productLookups));
        context.packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                pricingChargeItems, productByCompositeKey);
        context.purchasePackageUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(
                pricingChargeItems);
        context.purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                pricingChargeItems,
                context.purchasePackageUnitByChargeItemId,
                context.packageUnitRatioByChargeItemId,
                productByCompositeKey);
        context.productByCompositeKey = productByCompositeKey;
        context.alignedPurchaseDisplayUnitByChargeItemId =
            this.buildAlignedPurchaseDisplayUnitByChargeItemId(
                pricingChargeItems, context, productByCompositeKey);
        return context;
    }

    /**
     * 预计算 chargeItemId → 与操作单位对齐的展示进价单价（汇总品规维复用，禁止逐行再查批号）。
     *
     * @param pricingChargeItems    去重收费行
     * @param context               已含包装进价与倍数
     * @param productByCompositeKey 产品展示 Map
     * @return chargeItemId → 未带符号展示进价单价
     */
    private Map<Long, BigDecimal> buildAlignedPurchaseDisplayUnitByChargeItemId(
        List<ChargeItem> pricingChargeItems,
        CachedRowPurchasePricingContext context,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        if (pricingChargeItems == null || pricingChargeItems.isEmpty() || context == null) {
            return Map.of();
        }
        Map<Long, BigDecimal> alignedByChargeItemId = new LinkedHashMap<>();
        for (ChargeItem chargeItem : pricingChargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal packageUnit = context.purchasePackageUnitByChargeItemId.get(chargeItem.getId());
            if (packageUnit == null) {
                continue;
            }
            BigDecimal ratio = null;
            if (context.packageUnitRatioByChargeItemId != null) {
                ratio = context.packageUnitRatioByChargeItemId.get(chargeItem.getId());
            }
            ProductDefinitionDisplayDto product = null;
            if (productByCompositeKey != null && !productByCompositeKey.isEmpty()) {
                ProductDefinitionBatchLookup lookup =
                    ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
                if (lookup != null
                    && lookup.getProductTableName() != null
                    && lookup.getProductDefinitionId() != null) {
                    String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                        lookup.getProductTableName(), lookup.getProductDefinitionId());
                    if (compositeKey != null) {
                        product = productByCompositeKey.get(compositeKey);
                    }
                }
            }
            BigDecimal displayUnit = this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
                packageUnit, ratio, chargeItem, product);
            if (displayUnit != null) {
                alignedByChargeItemId.put(chargeItem.getId(), displayUnit);
            }
        }
        return alignedByChargeItemId;
    }

    /**
     * 初始化金额列为 0、行数为 0。
     *
     * @param row 汇总行
     */
    private void initAmountZeros(FeeDetailReportSummaryRowDto row) {
        row.setQuantityTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setPurchaseAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setSaleAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setReceivableAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setDiscountedAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setLineAmountTotal(MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
        row.setLineCount(0L);
    }

    /**
     * 累加售价/进价，并将实收、行金额与售价同口径累加。
     *
     * @param row      汇总行
     * @param sale     带符号售价增量
     * @param purchase 带符号进价增量
     */
    private void addSaleAndPurchase(
        FeeDetailReportSummaryRowDto row,
        BigDecimal sale,
        BigDecimal purchase) {
        BigDecimal saleTotal = MonetaryAmountSupport.nullToZero(sale);
        BigDecimal purchaseTotal = MonetaryAmountSupport.nullToZero(purchase);
        row.setSaleAmountTotal(MonetaryAmountSupport.scaleCalc(
            MonetaryAmountSupport.nullToZero(row.getSaleAmountTotal()).add(saleTotal)));
        row.setPurchaseAmountTotal(MonetaryAmountSupport.scaleCalc(
            MonetaryAmountSupport.nullToZero(row.getPurchaseAmountTotal()).add(purchaseTotal)));
        // 实收与售价同口径（对齐明细 Assembler：actualReceived = total_price 带符号）
        row.setActualReceivedAmountTotal(MonetaryAmountSupport.scaleCalc(
            MonetaryAmountSupport.nullToZero(row.getActualReceivedAmountTotal()).add(saleTotal)));
        row.setLineAmountTotal(MonetaryAmountSupport.scaleCalc(
            MonetaryAmountSupport.nullToZero(row.getLineAmountTotal()).add(saleTotal)));
    }

    /**
     * 累加带符号数量（与售价/进价同 payment 符号口径）。
     *
     * @param row            汇总行
     * @param quantity       收费行数量
     * @param paymentContext payment 上下文
     */
    private void addSignedQuantity(
        FeeDetailReportSummaryRowDto row,
        BigDecimal quantity,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        BigDecimal signedQuantity = this.applyPaymentSign(
            MonetaryAmountSupport.nullToZero(quantity), paymentContext);
        row.setQuantityTotal(MonetaryAmountSupport.scaleCalc(
            MonetaryAmountSupport.nullToZero(row.getQuantityTotal()).add(signedQuantity)));
    }

    /**
     * 汇总行展示单价：与同桶明细一致，取批号进价 / 账单单价（禁止合计÷数量反推）。
     *
     * <p>仅品规维调用；{@code pricingContext} 须由调用方一次 {@link #loadPurchasePricingContextFromCachedRows} 预载。</p>
     *
     * @param rows               汇总行
     * @param dimension          汇总维（须为 ITEM）
     * @param query              须含 cachedChargeRows
     * @param pricingContext     已预载批号进价上下文；空则跳过
     */
    private void enrichSummaryUnitPricesFromCachedRows(
        List<FeeDetailReportSummaryRowDto> rows,
        String dimension,
        OutpatientFeeDetailPageQuery query,
        CachedRowPurchasePricingContext pricingContext) {
        if (rows == null || rows.isEmpty() || query == null || pricingContext == null) {
            return;
        }
        List<OutpatientFeeDetailChargeRowQueryModel> cachedRows = query.getCachedChargeRows();
        if (cachedRows == null || cachedRows.isEmpty()) {
            return;
        }
        Map<String, Set<BigDecimal>> purchaseUnitsByBucket = new HashMap<>();
        Map<String, Set<BigDecimal>> saleUnitsByBucket = new HashMap<>();
        for (OutpatientFeeDetailChargeRowQueryModel chargeRow : cachedRows) {
            if (chargeRow == null || chargeRow.getChargeItem() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeRow.getChargeItem();
            String bucketKey = this.resolveDimensionBucketKey(dimension, chargeItem);
            if (bucketKey == null) {
                continue;
            }
            BigDecimal purchaseDisplayUnit = this.resolveChargePurchaseDisplayUnitPrice(
                chargeItem, pricingContext, chargeRow.getPaymentContext());
            if (purchaseDisplayUnit != null) {
                purchaseUnitsByBucket
                    .computeIfAbsent(bucketKey, ignored -> new LinkedHashSet<>())
                    .add(purchaseDisplayUnit);
            }
            BigDecimal saleDisplayUnit = this.resolveChargeSaleDisplayUnitPrice(
                chargeItem, chargeRow.getPaymentContext());
            if (saleDisplayUnit != null) {
                saleUnitsByBucket
                    .computeIfAbsent(bucketKey, ignored -> new LinkedHashSet<>())
                    .add(saleDisplayUnit);
            }
        }
        for (FeeDetailReportSummaryRowDto row : rows) {
            if (row == null) {
                continue;
            }
            String bucketKey = this.resolveSummaryRowBucketKey(row, dimension);
            if (StringUtils.isEmpty(bucketKey)) {
                continue;
            }
            row.setPurchaseUnitPrice(this.resolveUniqueDisplayUnitPrice(purchaseUnitsByBucket.get(bucketKey)));
            row.setSaleUnitPrice(this.resolveUniqueDisplayUnitPrice(saleUnitsByBucket.get(bucketKey)));
        }
    }

    /**
     * 解析汇总行分桶键（与 enrich 时 bucket 一致）。
     *
     * @param row       汇总行
     * @param dimension 汇总维
     * @return 分桶键
     */
    private String resolveSummaryRowBucketKey(FeeDetailReportSummaryRowDto row, String dimension) {
        if (FeeDetailReportConstants.SUMMARY_DIMENSION_FIN_TYPE.equals(dimension)) {
            if (StringUtils.isEmpty(row.getFinTypeCode())) {
                return null;
            }
            return row.getFinTypeCode().trim();
        }
        return row.getDimensionKey();
    }

    /**
     * 批号进价展示单价（与明细 Assembler 同路径：包装价 → 操作单位展示价）。
     *
     * @param chargeItem      收费行
     * @param pricingContext  批号进价上下文
     * @param paymentContext  payment 上下文
     * @return 带符号展示进价单价；无进价时 null
     */
    private BigDecimal resolveChargePurchaseDisplayUnitPrice(
        ChargeItem chargeItem,
        CachedRowPurchasePricingContext pricingContext,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        if (chargeItem == null || chargeItem.getId() == null || pricingContext == null) {
            return null;
        }
        BigDecimal displayUnit = null;
        if (pricingContext.alignedPurchaseDisplayUnitByChargeItemId != null) {
            displayUnit = pricingContext.alignedPurchaseDisplayUnitByChargeItemId.get(chargeItem.getId());
        }
        if (displayUnit == null) {
            return null;
        }
        return this.applyPaymentSign(displayUnit, paymentContext);
    }

    /**
     * 账单售价展示单价（ChargeItem.unit_price；禁止 Payment / 目录价）。
     *
     * @param chargeItem     收费行
     * @param paymentContext payment 上下文
     * @return 带符号展示售价单价；无账单价时 null
     */
    private BigDecimal resolveChargeSaleDisplayUnitPrice(
        ChargeItem chargeItem,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        if (chargeItem == null || chargeItem.getUnitPrice() == null) {
            return null;
        }
        return this.applyPaymentSign(
            MonetaryAmountSupport.scaleCalc(chargeItem.getUnitPrice()), paymentContext);
    }

    /**
     * 同桶多行批号/账单单价一致时返回该单价；不一致则 null（禁止反推加权价）。
     *
     * @param candidates 候选展示单价
     * @return 唯一单价或 null
     */
    private BigDecimal resolveUniqueDisplayUnitPrice(Set<BigDecimal> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        BigDecimal unique = null;
        for (BigDecimal candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            BigDecimal scaled = MonetaryAmountSupport.scaleCalc(candidate);
            if (unique == null) {
                unique = scaled;
            } else if (unique.compareTo(scaled) != 0) {
                return null;
            }
        }
        return unique;
    }

    /**
     * 在 resolvedChargeItemScope 内解析 definitionId → fin_type_code，并累计带符号进价。
     *
     * <p>优先复用 Support 预载 / 域层 cachedChargeRows，禁止汇总路径再次 IN 全量 ChargeItem。
     * 进价：先产品 Map → 包装倍数，再四参 {@code computePurchaseAmountFromUnitPrices}（#4M-OP）；
     * 符号按 payment 链路行展开（与售价净汇总同口径，同 chargeItem 可收退各计一次）。
     * 诊疗无进价（purchaseRaw 空）按 0 累加，供汇总页脚。</p>
     *
     * @param query              预解析后的读查询
     * @param definitionIds      待解析 definition 集合
     * @param incompleteExCode   无法解析时异常码
     * @param incompleteMessage  异常提示
     * @return fin_type 与进价分桶
     */
    private FinTypeResolveBundle resolveFinTypeAndPurchaseByDefinition(
        OutpatientFeeDetailPageQuery query,
        LinkedHashSet<Long> definitionIds,
        String incompleteExCode,
        String incompleteMessage) {
        FinTypeResolveBundle bundle = new FinTypeResolveBundle();
        // 无待解析 definition → 空分桶直接返回
        if (definitionIds == null || definitionIds.isEmpty()) {
            return bundle;
        }
        List<ChargeItem> pricingChargeItems =
            this.resolvePricingChargeItems(query, definitionIds, incompleteExCode, incompleteMessage);
        Map<Long, String> finTypeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(pricingChargeItems);
        // Pre：产品 Map → 包装倍数，供包装锚行进价（与门诊费用明细同口径）
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (ChargeItem chargeItem : pricingChargeItems) {
            // 跳过空收费行，避免产品锚点构建 NPE
            if (chargeItem == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            // 有产品锚点才入批量预取
            if (lookup != null) {
                productLookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(productLookups));
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                pricingChargeItems, productByCompositeKey);
        // Pre：先单价再行额，禁止同请求双调 computePurchaseAmount + computePurchaseUnitPrice
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(pricingChargeItems);
        // 必须传 product Map：包装操作单位走包装价×数量，禁止无 Map 时一律按拆零量折算
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                pricingChargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        // Asm：按 payment 链路行展开进价符号（与售价 summarizeFeeDetailRowsByDefinitionFromPaymentLinks 同口径）
        if (query != null && query.getCachedChargeRows() != null && !query.getCachedChargeRows().isEmpty()) {
            for (OutpatientFeeDetailChargeRowQueryModel row : query.getCachedChargeRows()) {
                this.accumulateDefinitionPurchaseFromPaymentLinkRow(
                    bundle,
                    definitionIds,
                    finTypeByChargeItemId,
                    purchaseByChargeItemId,
                    row.getChargeItem(),
                    row.getPaymentContext());
            }
        } else if (query != null && query.getResolvedPaymentChargeItemLinks() != null
            && !query.getResolvedPaymentChargeItemLinks().isEmpty()) {
            // FIN_TYPE 默认路径禁止物化 cachedChargeRows；须逐条 payment link 展开收退符号
            Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
            if (query.getPreloadedChargeItemsById() != null) {
                chargeItemById.putAll(query.getPreloadedChargeItemsById());
            }
            for (ChargeItem chargeItem : pricingChargeItems) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    chargeItemById.putIfAbsent(chargeItem.getId(), chargeItem);
                }
            }
            for (OutpatientFeeDetailChargePaymentLinkQueryModel link : query.getResolvedPaymentChargeItemLinks()) {
                if (link == null || link.getChargeItemId() == null) {
                    continue;
                }
                this.accumulateDefinitionPurchaseFromPaymentLinkRow(
                    bundle,
                    definitionIds,
                    finTypeByChargeItemId,
                    purchaseByChargeItemId,
                    chargeItemById.get(link.getChargeItemId()),
                    link.getPaymentContext());
            }
        // 无 payment 链路：回落按定价收费行 + paymentContext Map（单条上下文，兼容旧读路径）
        } else {
            Map<Long, OutpatientFeeDetailPaymentContextQueryModel> paymentContextByChargeItemId = Map.of();
            // 复用查询侧预载的 payment 上下文
            if (query != null && query.getPaymentContextByChargeItemId() != null) {
                paymentContextByChargeItemId = query.getPaymentContextByChargeItemId();
            }
            for (ChargeItem chargeItem : pricingChargeItems) {
                // 缺主键/definition 的定价行无法分桶
                if (chargeItem == null || chargeItem.getId() == null || chargeItem.getDefinitionId() == null) {
                    continue;
                }
                String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
                // 解析到 fin_type 则写入 definition 分桶
                if (StringUtils.isNotEmpty(finTypeCode)) {
                    bundle.finTypeByDefinitionId.put(chargeItem.getDefinitionId(), finTypeCode.trim());
                }
                BigDecimal purchaseRaw = purchaseByChargeItemId.get(chargeItem.getId());
                // 诊疗等无进价按 0
                if (purchaseRaw == null) {
                    purchaseRaw = BigDecimal.ZERO;
                }
                BigDecimal signedPurchase =
                    this.applyPaymentSign(purchaseRaw, paymentContextByChargeItemId.get(chargeItem.getId()));
                BigDecimal existing = bundle.purchaseByDefinitionId.get(chargeItem.getDefinitionId());
                // 同 definition 首次累加从 0 起
                if (existing == null) {
                    existing = BigDecimal.ZERO;
                }
                bundle.purchaseByDefinitionId.put(
                    chargeItem.getDefinitionId(),
                    MonetaryAmountSupport.scaleCalc(existing.add(signedPurchase)));
            }
        }
        for (Long definitionId : definitionIds) {
            // 任一 definition 未 enrich 到 fin_type → 整单失败，避免静默丢行
            if (!bundle.finTypeByDefinitionId.containsKey(definitionId)) {
                throw new BusinessException(incompleteExCode, incompleteMessage);
            }
            // 无进价累计的 definition 补 0，保证页脚进价列完整
            if (!bundle.purchaseByDefinitionId.containsKey(definitionId)) {
                bundle.purchaseByDefinitionId.put(definitionId, MonetaryAmountSupport.scaleCalc(BigDecimal.ZERO));
            }
        }
        return bundle;
    }

    /**
     * 解析进价/fin_type 所需 ChargeItem：优先预载与缓存，缺则按 resolved scope 分批加载。
     *
     * @param query              预解析查询
     * @param definitionIds      汇总涉及的 definition
     * @param incompleteExCode   无 scope 时异常码
     * @param incompleteMessage  异常提示
     * @return 待定价收费行
     */
    private List<ChargeItem> resolvePricingChargeItems(
        OutpatientFeeDetailPageQuery query,
        LinkedHashSet<Long> definitionIds,
        String incompleteExCode,
        String incompleteMessage) {
        Map<Long, ChargeItem> uniqueById = new LinkedHashMap<>();
        // 优先：从 cachedChargeRows 取本批 definition 收费行（禁止再扫全窗）
        if (query != null && query.getCachedChargeRows() != null) {
            for (OutpatientFeeDetailChargeRowQueryModel row : query.getCachedChargeRows()) {
                // 缺收费事实/主键跳过
                if (row == null || row.getChargeItem() == null || row.getChargeItem().getId() == null) {
                    continue;
                }
                ChargeItem chargeItem = row.getChargeItem();
                // 仅纳入本批 definition 的收费行
                if (chargeItem.getDefinitionId() != null
                    && definitionIds.contains(chargeItem.getDefinitionId())) {
                    uniqueById.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        // 缓存已覆盖 → 直接返回，避免二次加载
        if (!uniqueById.isEmpty()) {
            return new ArrayList<>(uniqueById.values());
        }
        // 次优：复用 Support 预载的 ChargeItem Map
        if (query != null && query.getPreloadedChargeItemsById() != null
            && !query.getPreloadedChargeItemsById().isEmpty()) {
            for (ChargeItem chargeItem : query.getPreloadedChargeItemsById().values()) {
                // 缺主键/definition 的预载行跳过
                if (chargeItem == null || chargeItem.getId() == null || chargeItem.getDefinitionId() == null) {
                    continue;
                }
                // 仅纳入本批 definition
                if (definitionIds.contains(chargeItem.getDefinitionId())) {
                    uniqueById.put(chargeItem.getId(), chargeItem);
                }
            }
            // 预载已覆盖 → 返回，不再按 scope IN
            if (!uniqueById.isEmpty()) {
                return new ArrayList<>(uniqueById.values());
            }
        }
        LinkedHashSet<Long> scopedChargeItemIds = new LinkedHashSet<>();
        // 末路：按已解析收费行 scope 分批加载
        if (query != null && query.getResolvedChargeItemScope() != null) {
            for (Long chargeItemId : query.getResolvedChargeItemScope()) {
                // 跳过空 id
                if (chargeItemId != null) {
                    scopedChargeItemIds.add(chargeItemId);
                }
            }
        }
        // 无 resolved scope → 无法定价 enrich，整单失败
        if (scopedChargeItemIds.isEmpty()) {
            throw new BusinessException(incompleteExCode, incompleteMessage);
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(scopedChargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        for (ChargeItem chargeItem : chargeItems) {
            // 缺 definition 的加载行无法映射到汇总 definition
            if (chargeItem == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            // 仅保留本批 definition 对应收费行
            if (definitionIds.contains(chargeItem.getDefinitionId())) {
                uniqueById.put(chargeItem.getId(), chargeItem);
            }
        }
        return new ArrayList<>(uniqueById.values());
    }

    /**
     * 按单条 payment 链路累加 definition 进价（与域层售价分桶同收退符号）。
     *
     * @param bundle                   分桶中间态
     * @param definitionIds            本批 definition 白名单
     * @param finTypeByChargeItemId      chargeItemId → fin_type
     * @param purchaseByChargeItemId     chargeItemId → 未带符号行进价
     * @param chargeItem                 收费行
     * @param paymentContext             payment 收退上下文
     */
    private void accumulateDefinitionPurchaseFromPaymentLinkRow(
        FinTypeResolveBundle bundle,
        LinkedHashSet<Long> definitionIds,
        Map<Long, String> finTypeByChargeItemId,
        Map<Long, BigDecimal> purchaseByChargeItemId,
        ChargeItem chargeItem,
        OutpatientFeeDetailPaymentContextQueryModel paymentContext) {
        // 缺收费事实/主键的链路行不参与进价符号展开
        if (chargeItem == null || chargeItem.getId() == null) {
            return;
        }
        // 非本批 definition 的收费行跳过
        if (chargeItem.getDefinitionId() == null || !definitionIds.contains(chargeItem.getDefinitionId())) {
            return;
        }
        String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
        // 解析到 fin_type 则写入 definition 分桶（后写覆盖同 definition）
        if (StringUtils.isNotEmpty(finTypeCode)) {
            bundle.finTypeByDefinitionId.put(chargeItem.getDefinitionId(), finTypeCode.trim());
        }
        BigDecimal purchaseRaw = purchaseByChargeItemId.get(chargeItem.getId());
        // 诊疗等无进价按 0，保证页脚进价列有值
        if (purchaseRaw == null) {
            purchaseRaw = BigDecimal.ZERO;
        }
        BigDecimal signedPurchase = this.applyPaymentSign(purchaseRaw, paymentContext);
        BigDecimal existing = bundle.purchaseByDefinitionId.get(chargeItem.getDefinitionId());
        // 同 definition 首次累加从 0 起
        if (existing == null) {
            existing = BigDecimal.ZERO;
        }
        bundle.purchaseByDefinitionId.put(
            chargeItem.getDefinitionId(),
            MonetaryAmountSupport.scaleCalc(existing.add(signedPurchase)));
    }

    /**
     * 退费 payment 链路进价取负（与域层汇总售价符号一致）。
     *
     * @param amount  未带符号进价
     * @param context payment 上下文
     * @return 带符号进价
     */
    private BigDecimal applyPaymentSign(
        BigDecimal amount,
        OutpatientFeeDetailPaymentContextQueryModel context) {
        BigDecimal raw = MonetaryAmountSupport.nullToZero(amount);
        // 退费链路进价取负，与售价净汇总同口径
        if (context != null && Boolean.TRUE.equals(context.getRefundPayment())) {
            return MonetaryAmountSupport.applySignAtCalcScale(raw.abs(), new BigDecimal("-1"));
        }
        return MonetaryAmountSupport.scaleCalc(raw);
    }

    /**
     * fin_type 与进价按 definition 分桶中间态。
     */
    private static final class FinTypeResolveBundle {

        /** definitionId → fin_type_code。 */
        private final Map<Long, String> finTypeByDefinitionId = new LinkedHashMap<>();

        /** definitionId → 带符号进价合计。 */
        private final Map<Long, BigDecimal> purchaseByDefinitionId = new LinkedHashMap<>();
    }

    /**
     * 缓存收费行批号进价批量上下文（与明细 enrich 同路径）。
     */
    private static final class CachedRowPurchasePricingContext {

        /** chargeItemId → 行进价（6 位）。 */
        private Map<Long, BigDecimal> purchaseAmountByChargeItemId = Map.of();

        /** chargeItemId → 批号包装进价单价。 */
        private Map<Long, BigDecimal> purchasePackageUnitByChargeItemId = Map.of();

        /** chargeItemId → 包装倍数。 */
        private Map<Long, BigDecimal> packageUnitRatioByChargeItemId = Map.of();

        /** 产品复合键 → 展示 DTO（进价展示单价与操作单位对齐）。 */
        private Map<String, ProductDefinitionDisplayDto> productByCompositeKey = Map.of();

        /** chargeItemId → 未带符号、与操作单位对齐的展示进价单价。 */
        private Map<Long, BigDecimal> alignedPurchaseDisplayUnitByChargeItemId = Map.of();
    }
}
