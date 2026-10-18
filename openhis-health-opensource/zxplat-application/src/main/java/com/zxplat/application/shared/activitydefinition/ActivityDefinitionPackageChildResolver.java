/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.activitydefinition;

import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.zxplat.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 诊疗子项解析与父 charge 展开（BR-ACTDEF-005 / BR-IP-PRC-011）。
 *
 * <p>选品/开立仍只见父项。结算只认子项账单冻价，不回查收费定价表；
 * 目录有子项但无对应子项账单时拒绝。与医嘱组套（OrderTemplate）无关。</p>
 *
 * <p>B.6.4：经 {@link ActivityDefinitionQueryService}、{@link ChargeItemQueryService} 只读。</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityDefinitionPackageChildResolver {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 按父诊疗定义 id 解析子项（含子目录读模型批量预热）。
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
        // Gate：仅 wkf_activity_definition 父目录才可能有子项子表
        if (parentProduct == null
            || !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(parentProduct.getProductTableName())
            || parentProduct.getProductDefinitionId() == null) {
            return List.of();
        }
        return this.listResolvedChildren(parentProduct.getProductDefinitionId());
    }

    /**
     * 将单条父 charge 展开为对外逻辑行。
     *
     * <p>只读账单子行冻价，不查定价表。目录有子项但无子账单时拒绝。
     * 有子项时<strong>仅</strong>返回子项行；各行之和必须等于父 {@code totalPrice}。</p>
     *
     * @param chargeItem    院内收费明细（父项）
     * @param parentProduct 与 charge 锚定一致的父目录读模型
     * @return 至少一行；有子项时不含父项行
     */
    public List<ActivityDefinitionPackageChargeSliceDto> allocateChargeSlices(
        ChargeItem chargeItem,
        ProductDefinitionDisplayDto parentProduct) {
        // Gate：无父收费行则无切片
        if (chargeItem == null) {
            return List.of();
        }
        // Pre：结算真源只认父账单下已落库子行，不查定价表
        List<ChargeItem> billChildren =
            chargeItemQueryService.listChildChargeItemsByParentId(chargeItem.getId());
        // 已有子项账单：结算只认冻价，不回查定价表
        if (billChildren != null && !billChildren.isEmpty()) {
            return this.allocateSlicesFromBillChildren(chargeItem, billChildren);
        }
        List<ActivityDefinitionPackageChildResolvedDto> children =
            this.listResolvedChildren(parentProduct);
        // Gate：目录有子项则必须已有子项账单，禁止再按定价表展开
        if (!children.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "带子项诊疗缺少已落库子项账单，无法结算");
        }
        // 无子项：对外仍返回 1 行父项逻辑切片
        return List.of(this.buildParentOnlySlice(chargeItem, parentProduct, 0));
    }

    /**
     * 从已落库子账单行组装展开切片（有子账单时的结算真源）。
     *
     * @param parentCharge 父收费行
     * @param billChildren 子收费行
     * @return 子项切片列表
     */
    private List<ActivityDefinitionPackageChargeSliceDto> allocateSlicesFromBillChildren(
        ChargeItem parentCharge,
        List<ChargeItem> billChildren
    ) {
        List<ActivityDefinitionPackageChargeSliceDto> slices = new ArrayList<>();
        BigDecimal childSum = BigDecimal.ZERO;
        int index = 0;
        for (ChargeItem childRow : billChildren) {
            if (childRow == null || childRow.getProductId() == null) {
                continue;
            }
            // 冲销负向行不参与结算展开；反结再生子行金额为正，refund_id 仅链回源行
            if (MonetaryAmountSupport.nullToZero(childRow.getTotalPrice()).signum() < 0) {
                continue;
            }
            ActivityDefinitionPackageChargeSliceDto slice = new ActivityDefinitionPackageChargeSliceDto();
            slice.setChargeItemId(parentCharge.getId());
            slice.setSliceIndex(index);
            slice.setPackageChildLine(Boolean.TRUE);
            slice.setChildrenId(childRow.getProductId());
            ProductDefinitionDisplayDto catalog = new ProductDefinitionDisplayDto();
            catalog.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            catalog.setProductDefinitionId(childRow.getProductId());
            slice.setCatalogProduct(catalog);
            slice.setQuantity(MonetaryAmountSupport.nullToZero(childRow.getQuantityValue()));
            slice.setUnitPrice(MonetaryAmountSupport.scaleCalc(childRow.getUnitPrice()));
            slice.setLineAmount(MonetaryAmountSupport.scaleCalc(childRow.getTotalPrice()));
            slices.add(slice);
            childSum = childSum.add(MonetaryAmountSupport.nullToZero(childRow.getTotalPrice()));
            index++;
        }
        // Gate：有子账单行但无有效正行，与缺子账单同等拒绝（不回落父项/定价）
        if (slices.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "带子项诊疗缺少已落库子项账单，无法结算");
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
        this.enrichSliceCatalogFromBillChildren(slices);
        return slices;
    }

    /**
     * 账单子行切片补全目录读模型（名称/医保码）。
     *
     * @param slices 已组装子切片（mutate）
     */
    private void enrichSliceCatalogFromBillChildren(List<ActivityDefinitionPackageChargeSliceDto> slices) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ActivityDefinitionPackageChargeSliceDto slice : slices) {
            if (slice == null || slice.getChildrenId() == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            lookup.setProductDefinitionId(slice.getChildrenId());
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return;
        }
        Map<String, ProductDefinitionDisplayDto> byKey =
            productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (ActivityDefinitionPackageChargeSliceDto slice : slices) {
            if (slice == null || slice.getChildrenId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, slice.getChildrenId());
            ProductDefinitionDisplayDto catalog = byKey.get(key);
            if (catalog != null) {
                slice.setCatalogProduct(catalog);
            }
        }
    }

    /**
     * 无子项或子表为空：单行沿用父目录与父 charge 数量/金额。
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
            return Map.of();
        }
        return productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }
}
