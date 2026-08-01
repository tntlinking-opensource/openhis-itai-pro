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
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
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
 * {@code ActivityDefinitionPackageChildResolver#allocateChargeSlices}。</p>
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
     * 将一条院内收费明细展开为医保上传逻辑行。
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
        if (item == null) {
            return List.of();
        }
        Long parentActivityId = resolveActivityDefinitionId(item, parentProduct);
        if (parentActivityId == null) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        List<ActivityDefinitionChildQueryModel> children =
            activityDefinitionQueryService.listChildrenByActivityId(parentActivityId);
        if (children == null || children.isEmpty()) {
            return List.of(buildParentSlice(item, parentProduct, fallbackName));
        }
        Map<String, ProductDefinitionCatalogQueryModel> childProductMap = loadChildProducts(children);
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
            BigDecimal unitPrice = chargeItemDefinitionQueryService.resolveFirstSalePriceValue(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, child.getChildrenId());
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
     * 解析父诊疗定义 id；非诊疗产品返回 null。
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
        String suffix = childSeq < 100 ? String.format("%02d", childSeq) : String.valueOf(childSeq);
        String sn = parentFeedetlSn + "." + suffix;
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
