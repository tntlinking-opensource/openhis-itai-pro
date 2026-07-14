/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.activitydefinition.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.application.basic.activitydefinition.support.dto.ActivityDefinitionPackageChargeSliceDto;
import com.openhis.health.application.basic.activitydefinition.support.dto.ActivityDefinitionPackageChildResolvedDto;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 诊疗套餐子项解析与父 charge 分摊（BR-ACTDEF-005 / BR-IP-PRC-011；全局「落一条、用时解析」）。
 *
 * <p>子表 {@code wkf_activity_definition_children} 为事实源；本 Support **不落库**、**不校验医保编码**，
 * 供 LIS 外发、报表、医保 adapter 等消费方按需调用 {@link #allocateChargeSlices}。</p>
 *
 * <p>B.6.4 只读编排：经 {@link ActivityDefinitionQueryService} 读子表、
 * {@link ProductDefinitionDisplayTerminologyAssembler} 批量预热子目录，内存合并后返回 DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityDefinitionPackageChildResolver {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 按父诊疗定义 id 解析套餐子项（含子目录读模型批量预热）。
     *
     * @param parentActivityId 父诊疗项目主键
     * @return 子项列表；无子表行时返回空列表（非 null）
     */
    public List<ActivityDefinitionPackageChildResolvedDto> listResolvedChildren(Long parentActivityId) {
        // Gate：无父 id 时无子项可解析
        if (parentActivityId == null) {
            return List.of();
        }
        // Pre：子表事实行（BR-ACTDEF-005；不以 children_json 为唯一真源）
        List<ActivityDefinitionChildQueryModel> tableChildren =
            activityDefinitionQueryService.listChildrenByActivityId(parentActivityId);
        if (tableChildren.isEmpty()) {
            return List.of();
        }
        Map<String, ProductDefinitionDisplayDto> childProductByKey =
            this.loadChildProducts(tableChildren);
        List<ActivityDefinitionPackageChildResolvedDto> resolved = new ArrayList<>();
        // Pre：逐子表行映射为解析 DTO，并挂接子目录读模型
        for (ActivityDefinitionChildQueryModel child : tableChildren) {
            // 子表脏行（children_id 空）跳过，不影响其余子项
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            ActivityDefinitionPackageChildResolvedDto dto = new ActivityDefinitionPackageChildResolvedDto();
            dto.setChildrenId(child.getChildrenId());
            dto.setChildrenNum(child.getChildrenNum());
            dto.setChildrenName(child.getChildrenName());
            String childKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, child.getChildrenId());
            dto.setChildProduct(childProductByKey.get(childKey));
            resolved.add(dto);
        }
        return resolved;
    }

    /**
     * 按父目录读模型解析子项（非 {@code wkf_activity_definition} 时返回空列表）。
     *
     * @param parentProduct 父诊疗目录读模型
     * @return 子项列表
     */
    public List<ActivityDefinitionPackageChildResolvedDto> listResolvedChildren(
        ProductDefinitionDisplayDto parentProduct) {
        // Gate：仅 wkf_activity_definition 父目录才可能有套餐子表
        if (parentProduct == null
            || !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(parentProduct.getProductTableName())
            || parentProduct.getProductDefinitionId() == null) {
            return List.of();
        }
        return this.listResolvedChildren(parentProduct.getProductDefinitionId());
    }

    /**
     * 将单条父 charge 按套餐子项拆为逻辑行（无子项时返回 1 行父项）。
     *
     * @param chargeItem     院内收费明细（父项）
     * @param parentProduct  与 charge 锚定一致的父目录读模型
     * @return 至少一行；各行 {@code lineAmount} 之和等于父 {@code totalPrice}（6 位计算刻度）
     */
    public List<ActivityDefinitionPackageChargeSliceDto> allocateChargeSlices(
        ChargeItem chargeItem,
        ProductDefinitionDisplayDto parentProduct) {
        if (chargeItem == null) {
            return List.of();
        }
        List<ActivityDefinitionPackageChildResolvedDto> children =
            this.listResolvedChildren(parentProduct);
        // 非套餐或子表为空：对外仍返回 1 行父项逻辑切片
        if (children.isEmpty()) {
            return List.of(this.buildParentOnlySlice(chargeItem, parentProduct, 0));
        }
        BigDecimal parentQty = MonetaryAmountSupport.nullToZero(chargeItem.getQuantityValue());
        // 父数量为 0 时按 1 计，避免权重全零无法分摊
        if (parentQty.compareTo(BigDecimal.ZERO) <= 0) {
            parentQty = BigDecimal.ONE;
        }
        BigDecimal parentTotal = MonetaryAmountSupport.scaleCalc(chargeItem.getTotalPrice());
        List<WeightedChildRow> weightedRows = new ArrayList<>();
        BigDecimal totalWeight = BigDecimal.ZERO;
        // Pre：权重 = 父数量 × children_num（空倍率按 1）
        for (ActivityDefinitionPackageChildResolvedDto child : children) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            BigDecimal childNum = BigDecimal.ONE;
            if (child.getChildrenNum() != null && child.getChildrenNum() > 0) {
                childNum = BigDecimal.valueOf(child.getChildrenNum());
            }
            BigDecimal weight = parentQty.multiply(childNum);
            weightedRows.add(new WeightedChildRow(child, weight));
            totalWeight = totalWeight.add(weight);
        }
        // 子表有行但均无效时，回落父项单行
        if (weightedRows.isEmpty()) {
            return List.of(this.buildParentOnlySlice(chargeItem, parentProduct, 0));
        }
        // 权重和为 0 时均分，防止除零
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            totalWeight = BigDecimal.valueOf(weightedRows.size());
            for (WeightedChildRow row : weightedRows) {
                row.weight = BigDecimal.ONE;
            }
        }
        List<ActivityDefinitionPackageChargeSliceDto> slices = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        // Cmd：按权重分摊父 totalPrice；最后一行吃尾差，保证各行之和等于父金额（6 位刻度）
        for (int index = 0; index < weightedRows.size(); index++) {
            WeightedChildRow row = weightedRows.get(index);
            BigDecimal sliceAmount;
            if (index == weightedRows.size() - 1) {
                sliceAmount = parentTotal.subtract(allocated);
            } else {
                sliceAmount = parentTotal.multiply(row.weight).divide(
                    totalWeight,
                    ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                    MonetaryAmountSupport.MONETARY_ROUNDING);
                sliceAmount = MonetaryAmountSupport.scaleCalc(sliceAmount);
                allocated = allocated.add(sliceAmount);
            }
            BigDecimal quantity = row.weight;
            BigDecimal unitPrice = sliceAmount;
            if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                unitPrice = sliceAmount.divide(
                    quantity,
                    ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
                    MonetaryAmountSupport.MONETARY_ROUNDING);
                unitPrice = MonetaryAmountSupport.scaleCalc(unitPrice);
            }
            ActivityDefinitionPackageChargeSliceDto slice = new ActivityDefinitionPackageChargeSliceDto();
            slice.setChargeItemId(chargeItem.getId());
            slice.setSliceIndex(index);
            slice.setPackageChildLine(Boolean.TRUE);
            slice.setChildrenId(row.child.getChildrenId());
            ProductDefinitionDisplayDto catalog = row.child.getChildProduct();
            // 子目录未预热时占位，供消费方展示名称或自行 Gate yb_no
            if (catalog == null) {
                catalog = this.buildPlaceholderChildProduct(row.child);
            }
            slice.setCatalogProduct(catalog);
            slice.setQuantity(quantity);
            slice.setUnitPrice(unitPrice);
            slice.setLineAmount(sliceAmount);
            slices.add(slice);
        }
        return slices;
    }

    /**
     * 非套餐或子表为空：单行沿用父目录与父 charge 数量/金额。
     *
     * @param chargeItem    收费明细
     * @param parentProduct 父目录
     * @param sliceIndex    切片序号
     * @return 单行切片
     */
    private ActivityDefinitionPackageChargeSliceDto buildParentOnlySlice(
        ChargeItem chargeItem,
        ProductDefinitionDisplayDto parentProduct,
        int sliceIndex) {
        ActivityDefinitionPackageChargeSliceDto slice = new ActivityDefinitionPackageChargeSliceDto();
        slice.setChargeItemId(chargeItem.getId());
        slice.setSliceIndex(sliceIndex);
        slice.setPackageChildLine(Boolean.FALSE);
        slice.setChildrenId(null);
        slice.setCatalogProduct(parentProduct);
        slice.setQuantity(MonetaryAmountSupport.nullToZero(chargeItem.getQuantityValue()));
        slice.setUnitPrice(MonetaryAmountSupport.scaleCalc(chargeItem.getUnitPrice()));
        slice.setLineAmount(MonetaryAmountSupport.scaleCalc(chargeItem.getTotalPrice()));
        return slice;
    }

    /**
     * 子目录未命中批量加载时，用子表快照构造最小读模型占位（供消费方展示/报错）。
     *
     * @param child 已解析子项
     * @return 占位目录 DTO
     */
    private ProductDefinitionDisplayDto buildPlaceholderChildProduct(
        ActivityDefinitionPackageChildResolvedDto child) {
        ProductDefinitionDisplayDto dto = new ProductDefinitionDisplayDto();
        dto.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        dto.setProductDefinitionId(child.getChildrenId());
        if (StringUtils.isNotEmpty(child.getChildrenName())) {
            dto.setProductName(child.getChildrenName());
        }
        return dto;
    }

    /**
     * 批量加载子诊疗目录读模型。
     *
     * @param tableChildren 子表读模型
     * @return 复合键 → 目录 DTO
     */
    private Map<String, ProductDefinitionDisplayDto> loadChildProducts(
        List<ActivityDefinitionChildQueryModel> tableChildren) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ActivityDefinitionChildQueryModel child : tableChildren) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(child.getChildrenId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Collections.emptyMap();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /** 分摊中间态：子项解析结果 + 按 children_num 折算后的权重。 */
    private static final class WeightedChildRow {
        /** 已解析子项（含 children_id / 子目录读模型）。 */
        private final ActivityDefinitionPackageChildResolvedDto child;
        /** 分摊权重（父数量 × children_num；均分回落时为 1）。 */
        private BigDecimal weight;

        private WeightedChildRow(ActivityDefinitionPackageChildResolvedDto child, BigDecimal weight) {
            this.child = child;
            this.weight = weight;
        }
    }
}
