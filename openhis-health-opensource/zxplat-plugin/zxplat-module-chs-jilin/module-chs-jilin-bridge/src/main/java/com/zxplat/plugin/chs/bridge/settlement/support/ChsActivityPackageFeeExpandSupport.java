/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.settlement.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.product.service.ProductDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

/**
 * 带子项诊疗费用上传展开（2204/2301）。
 *
 * <p>从父账单查子项账单，分别上传子项冻价；不查收费定价表。
 * 目录有子项但无子项账单时拒绝。有子项时<strong>只上传子项</strong>（父项通常无 {@code yb_no}）。
 * 各子项金额之和须等于父 {@code totalPrice}。与医嘱组套（OrderTemplate）无关。</p>
 *
 * <p>本类位于 bridge（禁止依赖 health-application），口径对齐
 * {@code ActivityDefinitionPackageChildResolver#allocateChargeSlices}。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.chs.enabled", havingValue = "true", matchIfMissing = true)
public class ChsActivityPackageFeeExpandSupport {

    /** 省平台费用明细流水号上限（2204/2301；3101 rx_id 现场同限）。 */
    private static final int FEDETL_SN_MAX_LEN = 30;

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 本批带子项诊疗展开预取结果：目录子项列表（仅用于判断是否有子项，不取价）。
     */
    @Getter
    public static final class PackageExpandBatchContext {
        /** activityId → 子项定义列表。 */
        private final Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId;

        /**
         * @param childrenByActivityId 子项按父 activity 分组
         */
        public PackageExpandBatchContext(
            Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId) {
            this.childrenByActivityId = childrenByActivityId;
        }
    }

    /**
     * 为本批收费项预取诊疗子项子表（无子项时返回空上下文，开销可忽略）。
     *
     * @param chargeItems            本批收费项
     * @param parentProductByItemId  chargeItemId → 父产品投影（可缺省）
     * @return 批上下文；never null
     */
    public PackageExpandBatchContext prepare(
        List<ChargeItem> chargeItems,
        Map<Long, ProductDefinitionCatalogQueryModel> parentProductByItemId) {
        LinkedHashSet<Long> activityIds = new LinkedHashSet<>();
        if (chargeItems != null) {
            for (ChargeItem item : chargeItems) {
                if (item == null) {
                    continue;
                }
                ProductDefinitionCatalogQueryModel parent = null;
                if (parentProductByItemId != null && item.getId() != null) {
                    parent = parentProductByItemId.get(item.getId());
                }
                Long activityId = this.resolveActivityDefinitionId(item, parent);
                // 非诊疗：不参与预取
                if (activityId != null) {
                    activityIds.add(activityId);
                }
            }
        }
        // 本批无诊疗定义：空上下文，expand 仍走单条路径兼容
        if (activityIds.isEmpty()) {
            return new PackageExpandBatchContext(Map.of());
        }
        Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId =
            activityDefinitionQueryService.listChildrenByActivityIds(activityIds);
        if (childrenByActivityId == null) {
            childrenByActivityId = new HashMap<>();
        } else {
            childrenByActivityId = new HashMap<>(childrenByActivityId);
        }
        // 无子项的父 id 也写入空列表，expand 命中 containsKey 后不再单条回落
        for (Long activityId : activityIds) {
            childrenByActivityId.putIfAbsent(activityId, List.of());
        }
        return new PackageExpandBatchContext(childrenByActivityId);
    }

    /**
     * 将一条院内收费明细展开为医保上传逻辑行（无批上下文，兼容单条调用）。
     *
     * @param item          收费明细
     * @param parentProduct 父项目录投影（可空）
     * @param fallbackName  父项展示名兜底（可空）
     * @return 至少一行；有子项时不含父项行
     */
    public List<ChsFeeUploadSlice> expandToUploadSlices(
        ChargeItem item,
        ProductDefinitionCatalogQueryModel parentProduct,
        String fallbackName) {
        return this.expandToUploadSlices(item, parentProduct, fallbackName, null);
    }

    /**
     * 将一条院内收费明细展开为医保上传逻辑行；可复用本批预取上下文。
     *
     * @param item          收费明细
     * @param parentProduct 父项目录投影（可空）
     * @param fallbackName  父项展示名兜底（可空）
     * @param batchContext  本批预取；null 时按单条查库（语义不变）
     * @return 至少一行；有子项时不含父项行
     */
    public List<ChsFeeUploadSlice> expandToUploadSlices(
        ChargeItem item,
        ProductDefinitionCatalogQueryModel parentProduct,
        String fallbackName,
        PackageExpandBatchContext batchContext) {
        if (item == null) {
            return List.of();
        }
        // Pre：父账单下子账单分别上传；数量/单价/金额取子行冻价
        List<ChsFeeUploadSlice> billSlices = this.expandFromBillChildren(item);
        if (!billSlices.isEmpty()) {
            return billSlices;
        }
        Long parentActivityId = resolveActivityDefinitionId(item, parentProduct);
        if (parentActivityId == null) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        List<ActivityDefinitionChildQueryModel> children =
            this.resolveChildren(parentActivityId, batchContext);
        // Gate：有子项无子项账单直接拒绝，不查定价表
        if (children != null && !children.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "带子项诊疗缺少已落库子项账单，无法上传医保费用");
        }
        // 无子项：上传父项单行
        return List.of(buildParentSlice(item, parentProduct, fallbackName));
    }

    /**
     * 列出该父收费项已落库子账单对应的平台费用流水号（与 2301 展开顺序一致）。
     *
     * <p>历史数据把多个子流水挤在同一条上传记录里时，2302 用本列表补齐被覆盖的兄弟流水。
     * 无子账单、或父子金额对不上时返回空列表，调用方只撤本地已记下的流水。</p>
     *
     * @param parentChargeItemId 父收费项 ID
     * @return 子流水号；无有效子账单时 empty
     */
    public List<String> listPersistedChildFeedetlSns(Long parentChargeItemId) {
        if (parentChargeItemId == null) {
            return List.of();
        }
        ChargeItem parent = chargeItemQueryService.findOptionalChargeItemById(parentChargeItemId).orElse(null);
        if (parent == null) {
            return List.of();
        }
        List<ChsFeeUploadSlice> slices;
        try {
            slices = this.expandFromBillChildren(parent);
        } catch (BusinessException ex) {
            // 父子金额不一致时不强行猜流水，避免 2302 撤错号
            return List.of();
        }
        if (slices == null || slices.isEmpty()) {
            return List.of();
        }
        List<String> feedetlSns = new ArrayList<>();
        for (ChsFeeUploadSlice slice : slices) {
            if (slice == null || slice.getFeedetlSn() == null || slice.getFeedetlSn().isBlank()) {
                continue;
            }
            feedetlSns.add(slice.getFeedetlSn().trim());
        }
        return feedetlSns;
    }

    /**
     * 从已落库子账单行组装医保上传切片（冻价真源）。
     *
     * <p>金额为负的冲销行不参与上传。反结再生行 {@code refund_id} 只链回源行、金额仍为正，须计入。
     * 无有效正行时返回 empty，由调用方判定缺子项账单并拒绝。</p>
     *
     * @param parentCharge 父收费行
     * @return 子项上传切片；无有效子账单时 empty
     */
    private List<ChsFeeUploadSlice> expandFromBillChildren(ChargeItem parentCharge) {
        if (parentCharge == null || parentCharge.getId() == null) {
            return List.of();
        }
        List<ChargeItem> billChildren =
            chargeItemQueryService.listChildChargeItemsByParentId(parentCharge.getId());
        if (billChildren == null || billChildren.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> childProductIds = new LinkedHashSet<>();
        for (ChargeItem childRow : billChildren) {
            if (childRow == null || childRow.getProductId() == null) {
                continue;
            }
            // 冲销负向行不上传；反结再生子行金额为正，refund_id 仅链回源行
            if (MonetaryAmountSupport.nullToZero(childRow.getTotalPrice()).signum() < 0) {
                continue;
            }
            childProductIds.add(childRow.getProductId());
        }
        if (childProductIds.isEmpty()) {
            return List.of();
        }
        Map<String, ProductDefinitionCatalogQueryModel> childProductMap =
            this.loadChildProductsByIds(childProductIds);
        String parentFeedetlSn = resolveParentFeedetlSn(parentCharge);
        List<ChsFeeUploadSlice> slices = new ArrayList<>();
        BigDecimal childSum = BigDecimal.ZERO;
        int childSeq = 0;
        for (ChargeItem childRow : billChildren) {
            if (childRow == null || childRow.getProductId() == null) {
                continue;
            }
            // 冲销负向行不上传；反结再生子行金额为正，refund_id 仅链回源行
            if (MonetaryAmountSupport.nullToZero(childRow.getTotalPrice()).signum() < 0) {
                continue;
            }
            childSeq++;
            String childKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, childRow.getProductId());
            ProductDefinitionCatalogQueryModel childProduct = childProductMap.get(childKey);
            String childName = null;
            if (childProduct != null && isNotEmpty(childProduct.getProductName())) {
                childName = childProduct.getProductName().trim();
            }
            if (isEmpty(childName)) {
                childName = String.valueOf(childRow.getProductId());
            }
            BigDecimal quantity = MonetaryAmountSupport.nullToZero(childRow.getQuantityValue());
            BigDecimal unitPrice = MonetaryAmountSupport.scaleCalc(childRow.getUnitPrice());
            BigDecimal lineAmount = MonetaryAmountSupport.scaleCalc(childRow.getTotalPrice());
            ChsFeeUploadSlice slice = new ChsFeeUploadSlice();
            slice.setChargeItemId(parentCharge.getId());
            slice.setFeedetlSn(buildChildFeedetlSn(parentFeedetlSn, childSeq));
            slice.setProduct(childProduct);
            slice.setCnt(quantity);
            slice.setPric(unitPrice);
            slice.setDetItemFeeSumamt(lineAmount);
            slice.setDisplayName(childName);
            slices.add(slice);
            childSum = childSum.add(MonetaryAmountSupport.nullToZero(childRow.getTotalPrice()));
        }
        if (slices.isEmpty()) {
            return List.of();
        }
        BigDecimal parentTotal = MonetaryAmountSupport.scaleCalc(parentCharge.getTotalPrice());
        if (MonetaryAmountSupport.scaleCalc(childSum).compareTo(parentTotal) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "父项金额与子项金额合计不一致：父项="
                    + parentTotal
                    + "，子项合计="
                    + MonetaryAmountSupport.scaleCalc(childSum));
        }
        return slices;
    }

    /**
     * 解析诊疗子项列表：优先批上下文，否则单条查库。
     *
     * @param parentActivityId 父诊疗定义 id
     * @param batchContext     批上下文；可空
     * @return 子项列表；无则 empty
     */
    private List<ActivityDefinitionChildQueryModel> resolveChildren(
        Long parentActivityId,
        PackageExpandBatchContext batchContext) {
        // 批预取命中（含空列表）：不再单条查库
        if (batchContext != null
            && batchContext.getChildrenByActivityId() != null
            && batchContext.getChildrenByActivityId().containsKey(parentActivityId)) {
            List<ActivityDefinitionChildQueryModel> cached =
                batchContext.getChildrenByActivityId().get(parentActivityId);
            if (cached == null) {
                return Collections.emptyList();
            }
            return cached;
        }
        return activityDefinitionQueryService.listChildrenByActivityId(parentActivityId);
    }

    /**
     * 按子项 id 集合批量加载产品投影（子账单上传时补医保码/名称）。
     *
     * @param childIds 子项 activity 定义 id
     * @return 复合键 → 产品
     */
    private Map<String, ProductDefinitionCatalogQueryModel> loadChildProductsByIds(LinkedHashSet<Long> childIds) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long childId : childIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(childId);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionQueryService.batchLoadAsMap(lookups);
    }

    /**
     * 解析父诊疗定义 id；非诊疗产品返回 null。
     *
     * @param item          收费明细
     * @param parentProduct 父产品投影；可空
     * @return activity 定义 id；非诊疗父项 null
     */
    private Long resolveActivityDefinitionId(
        ChargeItem item,
        ProductDefinitionCatalogQueryModel parentProduct) {
        if (parentProduct != null
            && TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(parentProduct.getProductTableName())
            && parentProduct.getProductDefinitionId() != null) {
            return parentProduct.getProductDefinitionId();
        }
        if (item.getProductId() != null
            && isNotEmpty(item.getProductTable())
            && TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(item.getProductTable().trim())) {
            return item.getProductId();
        }
        return null;
    }

    /**
     * 组装父项单行（无子项）。
     *
     * @param item          收费明细
     * @param parentProduct 父产品
     * @param fallbackName  展示名兜底
     * @return 单行 slice
     */
    private ChsFeeUploadSlice buildParentSlice(
        ChargeItem item,
        ProductDefinitionCatalogQueryModel parentProduct,
        String fallbackName) {
        ChsFeeUploadSlice slice = new ChsFeeUploadSlice();
        slice.setChargeItemId(item.getId());
        slice.setFeedetlSn(resolveParentFeedetlSn(item));
        slice.setProduct(parentProduct);
        slice.setCnt(item.getQuantityValue());
        slice.setPric(item.getUnitPrice());
        slice.setDetItemFeeSumamt(item.getTotalPrice());
        String displayName = fallbackName;
        if (isEmpty(displayName) && parentProduct != null && isNotEmpty(parentProduct.getProductName())) {
            displayName = parentProduct.getProductName().trim();
        }
        slice.setDisplayName(displayName);
        return slice;
    }

    /**
     * 父项费用流水号：优先 busNo，否则主键。
     *
     * @param item 收费明细
     * @return 非空流水号
     */
    private static String resolveParentFeedetlSn(ChargeItem item) {
        if (isNotEmpty(item.getBusNo())) {
            return item.getBusNo().trim();
        }
        return String.valueOf(item.getId());
    }

    /**
     * 子项账单 {@code feedetl_sn}：{@code 父流水.序号}（序号从 01 起）。
     *
     * <p>须满足省平台 2204/2301（≤30）与 3101 {@code rx_id}（现场校验 1～30）；
     * 同父 charge 内按展开顺序唯一。</p>
     *
     * @param parentFeedetlSn 父费用流水号
     * @param childSeq        子项序号（从 1 起）
     * @return 子项费用流水号
     */
    private static String buildChildFeedetlSn(String parentFeedetlSn, int childSeq) {
        String suffix;
        if (childSeq < 100) {
            suffix = String.format("%02d", childSeq);
        } else {
            suffix = String.valueOf(childSeq);
        }
        String sn = parentFeedetlSn + "." + suffix;
        // Gate：超长拒绝，禁止静默截断导致平台重号/拒收
        if (sn.length() > FEDETL_SN_MAX_LEN) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "子项费用流水号超长（上限 "
                    + FEDETL_SN_MAX_LEN
                    + "）："
                    + sn
                    + "，请缩短父收费项 busNo");
        }
        return sn;
    }
}
