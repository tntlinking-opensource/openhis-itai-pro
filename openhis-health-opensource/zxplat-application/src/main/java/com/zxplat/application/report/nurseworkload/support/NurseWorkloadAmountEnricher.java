/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.nurseworkload.support;

import com.zxplat.application.report.nurseworkload.dto.NurseConsumableDetailRowDto;
import com.zxplat.application.report.nurseworkload.dto.NurseExecutionDetailRowDto;
import com.zxplat.application.shared.chargediscount.ChargeItemDiscountDisplaySupport;
import com.zxplat.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.zxplat.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.zxplat.application.shared.product.ChargeItemProductLookupHelper;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 护士工作量报表金额 enrich（**BR-RPT-NWL-021**；DD-报表中心-进售价口径）。
 *
 * <p>售价 = 匹配 {@link ChargeItem#getTotalPrice()} 加和；进价仅耗材行经
 * {@link HistoricalChargeItemPurchasePriceSupport} 批号进价包装锚计算；诊疗执行进价恒为 null。
 * 已选开立科室时仅匹配 {@code requesting_org_id} 一致的 ChargeItem。</p>
 */
@Component
@RequiredArgsConstructor
public class NurseWorkloadAmountEnricher {

    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史批号进价解析。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装换算比（行进价合计 BR-MON-PKG-001）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 收费行优惠差额。 */
    private final ChargeItemDiscountDisplaySupport chargeItemDiscountDisplaySupport;

    /** 产品展示批量加载（packageUnitRatio）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 回填诊疗执行明细行售价；进价保持 null（诊疗无进价）。
     *
     * @param rows             待 enrich 明细行
     * @param requestingOrgId  开立科室；非空时仅匹配该 requesting_org_id
     */
    public void enrichExecutionDetailAmounts(
        List<NurseExecutionDetailRowDto> rows,
        Long requestingOrgId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItemsForExecutionRows(rows);
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            List<ChargeItem> matched =
                this.listMatchedExecutionChargeItems(row, chargeItems, requestingOrgId);
            row.setSaleAmount(MonetaryAmountSupport.scaleCalc(this.sumChargeItemSaleAmount(matched)));
            row.setDiscountAmount(MonetaryAmountSupport.scaleCalc(this.sumChargeItemDiscountAmount(matched)));
            row.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(
                this.chargeItemDiscountDisplaySupport.sumSignedReceivables(matched)));
        }
    }

    /**
     * 回填耗材使用明细行售价与进价。
     *
     * @param rows                   待 enrich 明细行
     * @param deviceReqIdByDispenseId 发放 id → 耗材请求 id
     * @param requestingOrgId        开立科室；非空时仅匹配该 requesting_org_id
     */
    public void enrichConsumableDetailAmounts(
        List<NurseConsumableDetailRowDto> rows,
        Map<Long, Long> deviceReqIdByDispenseId,
        Long requestingOrgId) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItemsForConsumableRows(rows, deviceReqIdByDispenseId);
        Map<Long, BigDecimal> purchaseByChargeItemId = this.computePurchaseByChargeItemId(chargeItems);
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            Long deviceReqId = null;
            if (row.getDispenseId() != null && deviceReqIdByDispenseId != null) {
                deviceReqId = deviceReqIdByDispenseId.get(row.getDispenseId());
            }
            List<ChargeItem> matched = this.listMatchedConsumableChargeItems(
                row, deviceReqId, chargeItems, requestingOrgId);
            row.setSaleAmount(MonetaryAmountSupport.scaleCalc(this.sumChargeItemSaleAmount(matched)));
            row.setDiscountAmount(MonetaryAmountSupport.scaleCalc(this.sumChargeItemDiscountAmount(matched)));
            row.setActualReceivedAmount(MonetaryAmountSupport.scaleCalc(
                this.chargeItemDiscountDisplaySupport.sumSignedReceivables(matched)));
            BigDecimal purchaseTotal = this.sumChargeItemPurchaseAmount(matched, purchaseByChargeItemId);
            if (purchaseTotal != null) {
                row.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseTotal));
            }
        }
    }

    /**
     * 汇总明细行金额合计。
     *
     * @param amounts 明细金额列表
     * @return 结算刻度合计（先 6 位加总再 2 位）
     */
    public BigDecimal sumDetailAmounts(List<BigDecimal> amounts) {
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 按诊疗执行明细行收集 procedure/service 锚点并批量加载 ChargeItem。
     *
     * @param rows 诊疗执行明细行
     * @return 去重后的收费明细列表
     */
    private List<ChargeItem> loadChargeItemsForExecutionRows(List<NurseExecutionDetailRowDto> rows) {
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceRequestIds = new LinkedHashSet<>();
        for (NurseExecutionDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getProcedureId() != null) {
                procedureIds.add(row.getProcedureId());
            }
            if (row.getServiceRequestId() != null) {
                serviceRequestIds.add(row.getServiceRequestId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByProcedureIds(procedureIds));
        if (!serviceRequestIds.isEmpty()) {
            ChargeItemQueryCommand serviceCmd = new ChargeItemQueryCommand();
            serviceCmd.setServiceIds(serviceRequestIds);
            serviceCmd.setExcludeSupersededSettledLines(true);
            this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByServiceIds(serviceCmd));
        }
        return new ArrayList<>(chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 按耗材使用明细行收集 dispense / deviceRequest 锚点并批量加载 ChargeItem。
     *
     * @param rows                   耗材使用明细行
     * @param deviceReqIdByDispenseId 发放 → 请求
     * @return 去重后的收费明细列表
     */
    private List<ChargeItem> loadChargeItemsForConsumableRows(
        List<NurseConsumableDetailRowDto> rows,
        Map<Long, Long> deviceReqIdByDispenseId) {
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getDispenseId() != null) {
                serviceIds.add(row.getDispenseId());
            }
            if (row.getDispenseId() != null && deviceReqIdByDispenseId != null) {
                Long deviceReqId = deviceReqIdByDispenseId.get(row.getDispenseId());
                if (deviceReqId != null) {
                    serviceIds.add(deviceReqId);
                }
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        if (!serviceIds.isEmpty()) {
            ChargeItemQueryCommand serviceCmd = new ChargeItemQueryCommand();
            serviceCmd.setServiceIds(serviceIds);
            serviceCmd.setExcludeSupersededSettledLines(true);
            this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByServiceIds(serviceCmd));
        }
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        for (NurseConsumableDetailRowDto row : rows) {
            if (row != null && row.getDispenseId() != null) {
                dispenseIds.add(row.getDispenseId());
            }
        }
        this.mergeChargeItems(
            chargeItemById,
            this.chargeItemQueryService.listChargeItemsByDeviceDispenseIds(dispenseIds));
        return new ArrayList<>(chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 批量计算 chargeItem 行进价合计（包装锚；无进价则无键）。
     *
     * @param chargeItems 候选收费明细
     * @return chargeItemId → 行进价
     */
    private Map<Long, BigDecimal> computePurchaseByChargeItemId(List<ChargeItem> chargeItems) {
        Map<Long, BigDecimal> purchaseByChargeItemId = new HashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return purchaseByChargeItemId;
        }
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
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
        return this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
            chargeItems,
            purchaseUnitByChargeItemId,
            packageUnitRatioByChargeItemId,
            productByCompositeKey);
    }

    /**
     * 将收费明细合并到 id → 实体 Map（跳过已软删或无 id 行）。
     *
     * @param chargeItemById 目标 Map
     * @param chargeItems    待合并列表
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
     * 汇总与诊疗执行行匹配的 ChargeItem 售价。
     *
     * @param row             执行明细行
     * @param chargeItems     候选收费明细
     * @param requestingOrgId 开立科室；非空时过滤
     * @return 匹配行 total_price 合计（未结算刻度）
     */
    private BigDecimal sumMatchedChargeSaleAmount(
        NurseExecutionDetailRowDto row,
        List<ChargeItem> chargeItems,
        Long requestingOrgId) {
        return this.sumChargeItemSaleAmount(
            this.listMatchedExecutionChargeItems(row, chargeItems, requestingOrgId));
    }

    /**
     * 列出与诊疗执行行匹配的 ChargeItem（去重 id）。
     *
     * @param row             执行明细行
     * @param chargeItems     候选收费明细
     * @param requestingOrgId 开立科室；非空时过滤
     * @return 匹配收费明细
     */
    private List<ChargeItem> listMatchedExecutionChargeItems(
        NurseExecutionDetailRowDto row,
        List<ChargeItem> chargeItems,
        Long requestingOrgId) {
        Set<Long> usedChargeItemIds = new HashSet<>();
        List<ChargeItem> matched = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matchesExecutionRow(row, chargeItem, requestingOrgId)) {
                continue;
            }
            if (!usedChargeItemIds.add(chargeItem.getId())) {
                continue;
            }
            matched.add(chargeItem);
        }
        return matched;
    }

    /**
     * 列出与耗材使用行匹配的 ChargeItem（去重 id）。
     *
     * @param row             耗材明细行
     * @param deviceReqId     发放反查的耗材请求 id；可空
     * @param chargeItems     候选收费明细
     * @param requestingOrgId 开立科室；非空时过滤
     * @return 匹配收费明细
     */
    private List<ChargeItem> listMatchedConsumableChargeItems(
        NurseConsumableDetailRowDto row,
        Long deviceReqId,
        List<ChargeItem> chargeItems,
        Long requestingOrgId) {
        Set<Long> usedChargeItemIds = new HashSet<>();
        List<ChargeItem> matched = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matchesConsumableRow(row, deviceReqId, chargeItem, requestingOrgId)) {
                continue;
            }
            if (!usedChargeItemIds.add(chargeItem.getId())) {
                continue;
            }
            matched.add(chargeItem);
        }
        return matched;
    }

    /**
     * 汇总 chargeItem 列表售价 total_price。
     *
     * @param matched 匹配收费明细
     * @return 售价合计（未结算刻度）
     */
    private BigDecimal sumChargeItemSaleAmount(List<ChargeItem> matched) {
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : matched) {
            total = total.add(this.salePriceOf(chargeItem));
        }
        return total;
    }

    /**
     * 汇总 chargeItem 列表优惠差额（与售价同号）。
     *
     * @param matched 匹配收费明细
     * @return 优惠合计（未结算刻度）
     */
    private BigDecimal sumChargeItemDiscountAmount(List<ChargeItem> matched) {
        BigDecimal total = BigDecimal.ZERO;
        if (matched == null || matched.isEmpty()) {
            return total;
        }
        for (ChargeItem chargeItem : matched) {
            if (chargeItem == null) {
                continue;
            }
            BigDecimal original = this.salePriceOf(chargeItem);
            BigDecimal discounted = original;
            if (chargeItem.getDiscountAmount() != null) {
                discounted = chargeItem.getDiscountAmount();
            }
            total = total.add(this.chargeItemDiscountDisplaySupport.resolveSignedGap(original, discounted));
        }
        return total;
    }

    /**
     * 汇总 chargeItem 列表行进价；全无进价键时返回 null。
     *
     * @param matched                  匹配收费明细
     * @param purchaseByChargeItemId   chargeItemId → 行进价
     * @return 进价合计；无有效进价时为 null
     */
    private BigDecimal sumChargeItemPurchaseAmount(
        List<ChargeItem> matched,
        Map<Long, BigDecimal> purchaseByChargeItemId) {
        if (matched == null || matched.isEmpty() || purchaseByChargeItemId == null) {
            return null;
        }
        BigDecimal total = null;
        for (ChargeItem chargeItem : matched) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal purchaseAmount = purchaseByChargeItemId.get(chargeItem.getId());
            if (purchaseAmount == null) {
                continue;
            }
            if (total == null) {
                total = BigDecimal.ZERO;
            }
            total = total.add(purchaseAmount);
        }
        return total;
    }

    /**
     * 收费明细是否与诊疗执行行锚点匹配。
     *
     * @param row             执行明细行
     * @param chargeItem      收费明细
     * @param requestingOrgId 开立科室；非空时须一致
     * @return 匹配为 true
     */
    private boolean matchesExecutionRow(
        NurseExecutionDetailRowDto row,
        ChargeItem chargeItem,
        Long requestingOrgId) {
        if (!this.matchesRequestingOrg(chargeItem, requestingOrgId)) {
            return false;
        }
        if (row.getProcedureId() != null
            && row.getProcedureId().equals(chargeItem.getProcedureId())) {
            return true;
        }
        if (row.getServiceRequestId() != null
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && row.getServiceRequestId().equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 收费明细是否与耗材使用行锚点匹配。
     *
     * @param row             耗材明细行
     * @param deviceReqId     发放反查的耗材请求 id；可空
     * @param chargeItem      收费明细
     * @param requestingOrgId 开立科室；非空时须一致
     * @return 匹配为 true
     */
    private boolean matchesConsumableRow(
        NurseConsumableDetailRowDto row,
        Long deviceReqId,
        ChargeItem chargeItem,
        Long requestingOrgId) {
        if (!this.matchesRequestingOrg(chargeItem, requestingOrgId)) {
            return false;
        }
        if (row.getDispenseId() != null) {
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
                && row.getDispenseId().equals(chargeItem.getServiceId())) {
                return true;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && row.getDispenseId().equals(chargeItem.getDispenseId())) {
                return true;
            }
        }
        if (deviceReqId != null
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())
            && deviceReqId.equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 开立科室筛：已选 requestingOrgId 须等于账单开立科室。
     *
     * @param chargeItem      收费明细
     * @param requestingOrgId 开立科室；空则不过滤
     * @return 通过筛选为 true
     */
    private boolean matchesRequestingOrg(ChargeItem chargeItem, Long requestingOrgId) {
        if (requestingOrgId == null) {
            return true;
        }
        return requestingOrgId.equals(chargeItem.getRequestingOrgId());
    }

    /**
     * 取收费明细行售价；null 按零处理。
     *
     * @param chargeItem 收费明细
     * @return total_price 或 ZERO
     */
    private BigDecimal salePriceOf(ChargeItem chargeItem) {
        if (chargeItem.getTotalPrice() == null) {
            return BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }
}
