/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.activitydefinition;

import com.openhis.health.application.shared.product.ClinicalCatalogPriceResolver;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 诊疗套餐子项解析与父 charge 展开（BR-ACTDEF-005 / BR-IP-PRC-011；全局「落一条、用时解析」）。
 *
 * <p>子表 {@code wkf_activity_definition_children} 为事实源；本 Support **不落库**。
 * 有子项时 {@link #allocateChargeSlices} <strong>只返回子项行</strong>（父项通常无 {@code yb_no}，
 * 医保/LIS 等消费方只传子项），金额取各子项目录 {@code SALE} 价，
 * <strong>禁止</strong>按权重分摊父项金额；各子项金额之和须等于父 charge {@code totalPrice}。</p>
 *
 * <p>B.6.4 只读编排：经 {@link ActivityDefinitionQueryService} 读子表、
 * {@link ProductDefinitionDisplayTerminologyAssembler} 批量预热子目录，
 * {@link ChargeItemDefinitionQueryService} 批量取子项 SALE 价。</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityDefinitionPackageChildResolver {

    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

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
     * 将单条父 charge 展开为对外逻辑行。
     *
     * <p>无子项：返回 1 行父项。有子项：<strong>仅</strong>返回子项行（父项通常无医保编码，不对外上传）；
     * 数量 = 父数量 × {@code children_num}；单价/金额取子项目录 {@code SALE} 价，
     * <strong>不分摊</strong>父项金额；各行之和必须等于父 {@code totalPrice}。</p>
     *
     * @param chargeItem    院内收费明细（父项）
     * @param parentProduct 与 charge 锚定一致的父目录读模型
     * @return 至少一行；有子项时不含父项行
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
        Map<Long, ChargeItemDefinition> saleByChildId = this.loadChildSalePriceLines(children);
        List<ActivityDefinitionPackageChargeSliceDto> slices = new ArrayList<>();
        BigDecimal childSum = BigDecimal.ZERO;
        // Cmd：逐子项按目录 SALE 计价；禁止按权重拆父 totalPrice
        for (int index = 0; index < children.size(); index++) {
            ActivityDefinitionPackageChildResolvedDto child = children.get(index);
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            ChargeItemDefinition saleLine = saleByChildId.get(child.getChildrenId());
            if (saleLine == null || saleLine.getPriceValue() == null) {
                String childName = resolveChildDisplayName(child);
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "套餐子项缺少有效售价，无法展开费用：" + childName);
            }
            BigDecimal childNum = BigDecimal.ONE;
            if (child.getChildrenNum() != null && child.getChildrenNum() > 0) {
                childNum = BigDecimal.valueOf(child.getChildrenNum());
            }
            BigDecimal quantity = parentQty.multiply(childNum);
            BigDecimal unitPrice = MonetaryAmountSupport.scaleCalc(saleLine.getPriceValue());
            BigDecimal lineAmount = MonetaryAmountSupport.lineTotal(unitPrice, quantity);
            ActivityDefinitionPackageChargeSliceDto slice = new ActivityDefinitionPackageChargeSliceDto();
            slice.setChargeItemId(chargeItem.getId());
            slice.setSliceIndex(index);
            slice.setPackageChildLine(Boolean.TRUE);
            slice.setChildrenId(child.getChildrenId());
            ProductDefinitionDisplayDto catalog = child.getChildProduct();
            // 子目录未预热时占位，供消费方展示名称或自行 Gate yb_no
            if (catalog == null) {
                catalog = this.buildPlaceholderChildProduct(child);
            }
            slice.setCatalogProduct(catalog);
            slice.setQuantity(quantity);
            slice.setUnitPrice(unitPrice);
            slice.setLineAmount(lineAmount);
            slices.add(slice);
            childSum = childSum.add(lineAmount);
        }
        // 子表有行但均无效时，回落父项单行
        if (slices.isEmpty()) {
            return List.of(this.buildParentOnlySlice(chargeItem, parentProduct, 0));
        }
        // Gate：父项金额必须等于子项金额之和（目录维护口径；禁止分摊抹平）
        BigDecimal parentTotal = MonetaryAmountSupport.scaleCalc(chargeItem.getTotalPrice());
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
     * 批量加载子项诊疗 SALE 定价行（按 instanceId 择优）。
     *
     * @param children 已解析子项
     * @return childrenId → SALE 定价行
     */
    private Map<Long, ChargeItemDefinition> loadChildSalePriceLines(
        List<ActivityDefinitionPackageChildResolvedDto> children) {
        List<Long> childIds = new ArrayList<>();
        for (ActivityDefinitionPackageChildResolvedDto child : children) {
            if (child != null && child.getChildrenId() != null) {
                childIds.add(child.getChildrenId());
            }
        }
        if (childIds.isEmpty()) {
            return Collections.emptyMap();
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceQuery.setInstanceIds(childIds);
        List<ChargeItemDefinition> priceLines =
            chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        if (priceLines == null || priceLines.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, List<ChargeItemDefinition>> linesByInstanceId = new HashMap<>();
        for (ChargeItemDefinition line : priceLines) {
            if (line == null || line.getInstanceId() == null) {
                continue;
            }
            linesByInstanceId.computeIfAbsent(line.getInstanceId(), key -> new ArrayList<>()).add(line);
        }
        Map<Long, ChargeItemDefinition> saleByChildId = new HashMap<>();
        for (Long childId : childIds) {
            List<ChargeItemDefinition> lines = linesByInstanceId.get(childId);
            ChargeItemDefinition sale = clinicalCatalogPriceResolver.selectFirstPriceLineForChargeDraft(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, lines);
            if (sale != null) {
                saleByChildId.put(childId, sale);
            }
        }
        return saleByChildId;
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
     * 子项展示名（缺价报错用）。
     *
     * @param child 已解析子项
     * @return 非空展示串
     */
    private String resolveChildDisplayName(ActivityDefinitionPackageChildResolvedDto child) {
        if (child.getChildProduct() != null
            && StringUtils.isNotEmpty(child.getChildProduct().getProductName())) {
            return child.getChildProduct().getProductName().trim();
        }
        if (StringUtils.isNotEmpty(child.getChildrenName())) {
            return child.getChildrenName().trim();
        }
        return String.valueOf(child.getChildrenId());
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
}
