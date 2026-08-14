/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.settlement.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.administration.product.service.ProductDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogQueryModel;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
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

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;

/**
 * 诊疗套餐费用上传展开（2204/2301）。
 *
 * <p>院内仅落父 charge；有 {@code wkf_activity_definition_children} 时<strong>只上传子项</strong>
 *（父项通常无 {@code yb_no}）。数量 = 父数量 × {@code children_num}；金额取子项 SALE 价，
 * 不分摊父项；各子项金额之和须等于父 {@code totalPrice}。</p>
 *
 * <p>本类位于 bridge（禁止依赖 health-application），口径对齐
 * {@code ActivityDefinitionPackageChildResolver#allocateChargeSlices}。
 * 支持 {@link PackageExpandBatchContext} 预取子项与 SALE 价，消除按套餐/子项 N+1。</p>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class ChsActivityPackageFeeExpandSupport {

    /** 省平台费用明细流水号上限（2204/2301；3101 rx_id 现场同限）。 */
    private static final int FEDETL_SN_MAX_LEN = 30;

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionQueryService productDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /**
     * 本批套餐展开预取结果：子项列表、子项产品、子项 SALE 价（按 activity 子 id）。
     */
    @Getter
    public static final class PackageExpandBatchContext {
        /** activityId → 子项定义列表。 */
        private final Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId;
        /** 复合键 → 子项产品投影。 */
        private final Map<String, ProductDefinitionCatalogQueryModel> childProductByKey;
        /** 子项 activity 定义 id → SALE 单价。 */
        private final Map<Long, BigDecimal> salePriceByChildActivityId;

        /**
         * @param childrenByActivityId       子项按父 activity 分组
         * @param childProductByKey          子项产品投影
         * @param salePriceByChildActivityId 子项 SALE 价
         */
        public PackageExpandBatchContext(
            Map<Long, List<ActivityDefinitionChildQueryModel>> childrenByActivityId,
            Map<String, ProductDefinitionCatalogQueryModel> childProductByKey,
            Map<Long, BigDecimal> salePriceByChildActivityId) {
            this.childrenByActivityId = childrenByActivityId;
            this.childProductByKey = childProductByKey;
            this.salePriceByChildActivityId = salePriceByChildActivityId;
        }
    }

    /**
     * 为本批收费项预取套餐子项与 SALE 价（无套餐时返回空上下文，开销可忽略）。
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
                // 非诊疗套餐：不参与预取
                if (activityId != null) {
                    activityIds.add(activityId);
                }
            }
        }
        // 本批无诊疗定义：空上下文，expand 仍走单条路径兼容
        if (activityIds.isEmpty()) {
            return new PackageExpandBatchContext(Map.of(), Map.of(), Map.of());
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
        LinkedHashSet<Long> childIds = new LinkedHashSet<>();
        for (List<ActivityDefinitionChildQueryModel> children : childrenByActivityId.values()) {
            if (children == null) {
                continue;
            }
            for (ActivityDefinitionChildQueryModel child : children) {
                if (child != null && child.getChildrenId() != null) {
                    childIds.add(child.getChildrenId());
                }
            }
        }
        Map<String, ProductDefinitionCatalogQueryModel> childProductByKey = Map.of();
        Map<Long, BigDecimal> salePriceByChildActivityId = Map.of();
        // 有子项：一次批产品 + 一次批 SALE 价
        if (!childIds.isEmpty()) {
            childProductByKey = this.loadChildProductsByIds(childIds);
            salePriceByChildActivityId = chargeItemDefinitionQueryService.mapFirstSalePriceValueByInstanceIds(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, childIds);
            if (salePriceByChildActivityId == null) {
                salePriceByChildActivityId = Map.of();
            }
        }
        return new PackageExpandBatchContext(childrenByActivityId, childProductByKey, salePriceByChildActivityId);
    }

    /**
     * 将一条院内收费明细展开为医保上传逻辑行（无批上下文，兼容单条调用）。
     *
     * @param item          收费明细
     * @param parentProduct 父项目录投影（可空）
     * @param fallbackName  父项展示名兜底（可空）
     * @return 至少一行；套餐有子项时不含父项行
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
     * @return 至少一行；套餐有子项时不含父项行
     */
    public List<ChsFeeUploadSlice> expandToUploadSlices(
        ChargeItem item,
        ProductDefinitionCatalogQueryModel parentProduct,
        String fallbackName,
        PackageExpandBatchContext batchContext) {
        if (item == null) {
            return List.of();
        }
        Long parentActivityId = resolveActivityDefinitionId(item, parentProduct);
        if (parentActivityId == null) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        List<ActivityDefinitionChildQueryModel> children =
            this.resolveChildren(parentActivityId, batchContext);
        if (children == null || children.isEmpty()) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        Map<String, ProductDefinitionCatalogQueryModel> childProductMap =
            this.resolveChildProductMap(children, batchContext);
        String parentFeedetlSn = resolveParentFeedetlSn(item);
        BigDecimal parentQty = MonetaryAmountSupport.nullToZero(item.getQuantityValue());
        List<ChsFeeUploadSlice> slices = new ArrayList<>();
        BigDecimal childSum = BigDecimal.ZERO;
        // 子项流水用父流水+序号（≤30），禁止拼雪花 childrenId（会超 2204/3101 长度）
        int childSeq = 0;
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            childSeq++;
            String childKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, child.getChildrenId());
            ProductDefinitionCatalogQueryModel childProduct = childProductMap.get(childKey);
            String childName = resolveChildDisplayName(child, childProduct);
            BigDecimal unitPrice = this.resolveChildSalePrice(child.getChildrenId(), batchContext);
            // 批价未命中：回落单条，保证与优化前同口径
            if (unitPrice == null) {
                unitPrice = chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
                    TableNameConstants.WKF_ACTIVITY_DEFINITION, child.getChildrenId());
            }
            if (unitPrice == null) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "套餐子项缺少有效售价，无法上传医保费用：" + childName);
            }
            BigDecimal childNum = BigDecimal.ONE;
            if (child.getChildrenNum() != null && child.getChildrenNum() > 0) {
                childNum = BigDecimal.valueOf(child.getChildrenNum());
            }
            BigDecimal quantity = parentQty.multiply(childNum);
            BigDecimal scaledPrice = MonetaryAmountSupport.scaleCalc(unitPrice);
            BigDecimal lineAmount = MonetaryAmountSupport.lineTotal(scaledPrice, quantity);
            ChsFeeUploadSlice slice = new ChsFeeUploadSlice();
            slice.setChargeItemId(item.getId());
            slice.setFeedetlSn(buildChildFeedetlSn(parentFeedetlSn, childSeq));
            slice.setProduct(childProduct);
            slice.setCnt(quantity);
            slice.setPric(scaledPrice);
            slice.setDetItemFeeSumamt(lineAmount);
            slice.setDisplayName(childName);
            slices.add(slice);
            childSum = childSum.add(lineAmount);
        }
        if (slices.isEmpty()) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        BigDecimal parentTotal = MonetaryAmountSupport.scaleCalc(item.getTotalPrice());
        if (MonetaryAmountSupport.scaleCalc(childSum).compareTo(parentTotal) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "套餐父项金额与子项金额合计不一致：父项="
                    + parentTotal
                    + "，子项合计="
                    + MonetaryAmountSupport.scaleCalc(childSum));
        }
        return slices;
    }

    /**
     * 解析套餐子项列表：优先批上下文，否则单条查库。
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
     * 解析子项产品 Map：批上下文命中则复用，否则对本条 children 批载。
     *
     * @param children     子项列表
     * @param batchContext 批上下文；可空
     * @return 复合键 → 产品
     */
    private Map<String, ProductDefinitionCatalogQueryModel> resolveChildProductMap(
        List<ActivityDefinitionChildQueryModel> children,
        PackageExpandBatchContext batchContext) {
        if (batchContext != null
            && batchContext.getChildProductByKey() != null
            && !batchContext.getChildProductByKey().isEmpty()) {
            return batchContext.getChildProductByKey();
        }
        return this.loadChildProducts(children);
    }

    /**
     * 从批上下文取子项 SALE 价；无则 null（由调用方回落单条）。
     *
     * @param childActivityId 子项定义 id
     * @param batchContext    批上下文；可空
     * @return 单价；未命中 null
     */
    private BigDecimal resolveChildSalePrice(Long childActivityId, PackageExpandBatchContext batchContext) {
        if (batchContext == null || batchContext.getSalePriceByChildActivityId() == null) {
            return null;
        }
        return batchContext.getSalePriceByChildActivityId().get(childActivityId);
    }

    /**
     * 按子项 id 集合批量加载产品投影。
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
     * @return activity 定义 id；非套餐 null
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
     * 按子项模型列表加载产品（单条 expand 回落路径）。
     *
     * @param children 子项列表
     * @return 复合键 → 产品
     */
    private Map<String, ProductDefinitionCatalogQueryModel> loadChildProducts(
        List<ActivityDefinitionChildQueryModel> children) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : children) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(child.getChildrenId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return productDefinitionQueryService.batchLoadAsMap(lookups);
    }

    /**
     * 组装父项单行（无子项或非套餐）。
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
     * 套餐子项 {@code feedetl_sn}：{@code 父流水.序号}（序号从 01 起）。
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
                "套餐子项费用流水号超长（上限 "
                    + FEDETL_SN_MAX_LEN
                    + "）："
                    + sn
                    + "，请缩短父收费项 busNo");
        }
        return sn;
    }

    /**
     * 子项展示名：优先子产品名，否则子项模型名。
     *
     * @param child        子项模型
     * @param childProduct 子产品投影
     * @return 非空展示名
     */
    private static String resolveChildDisplayName(
        ActivityDefinitionChildQueryModel child,
        ProductDefinitionCatalogQueryModel childProduct) {
        if (childProduct != null && isNotEmpty(childProduct.getProductName())) {
            return childProduct.getProductName().trim();
        }
        if (isNotEmpty(child.getChildrenName())) {
            return child.getChildrenName().trim();
        }
        return String.valueOf(child.getChildrenId());
    }
}
