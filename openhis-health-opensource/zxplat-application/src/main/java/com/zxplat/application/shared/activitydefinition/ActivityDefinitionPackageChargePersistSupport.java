/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.activitydefinition;

import com.zxplat.application.shared.product.ClinicalCatalogPriceResolver;
import com.zxplat.domain.administration.chargeitem.command.CreateChargeItemCommand;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemDomainService;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带子项诊疗落账：父 Charge 已落库后，按当时子项 SALE 冻价写入子项账单（parent_id 挂父）。
 *
 * <p>口径：开立/划价/回显只见父项；子项账单供结算/医保；子项金额之和须等于父 {@code totalPrice}。
 * 子项账单状态/生成来源与父行对齐。目录无子项时静默跳过。与医嘱组套（OrderTemplate）无关。</p>
 */
@Component
@RequiredArgsConstructor
public class ActivityDefinitionPackageChargePersistSupport {

    private final ActivityDefinitionPackageChildResolver packageChildResolver;
    private final ChargeItemDomainService chargeItemDomainService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ClinicalCatalogPriceResolver clinicalCatalogPriceResolver;

    /**
     * 父收费明细落库后补写子项账单（门诊草稿、住院执行、护士划价、滚费等同构路径）。
     *
     * @param parentChargeItemId 父收费明细主键
     * @param parentActivityId   父诊疗定义主键
     * @param operatorId         操作人
     */
    public void persistPackageChildChargeItemsAfterParent(
        Long parentChargeItemId,
        Long parentActivityId,
        Long operatorId
    ) {
        // Gate：缺父 charge 或父诊疗 id 时无法展开子行
        if (parentChargeItemId == null || parentActivityId == null) {
            return;
        }
        ChargeItem parent = chargeItemQueryService.findOptionalChargeItemById(parentChargeItemId).orElse(null);
        // Gate：父行不存在则拒绝（编排时序错误）
        if (parent == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "带子项诊疗落子账单失败：父收费明细不存在，id=" + parentChargeItemId);
        }
        // 已有子行则幂等跳过（避免重复划价补写）
        List<ChargeItem> existingChildren =
            chargeItemQueryService.listChildChargeItemsByParentId(parentChargeItemId);
        // 幂等：已有子项账单则不重复冻价
        if (existingChildren != null && !existingChildren.isEmpty()) {
            return;
        }
        List<ActivityDefinitionPackageChildResolvedDto> children =
            packageChildResolver.listResolvedChildren(parentActivityId);
        // 无子项：无子表行，不写子项账单
        if (children == null || children.isEmpty()) {
            return;
        }
        Map<Long, ChargeItemDefinition> saleByChildId = this.loadChildSalePriceLines(children);
        BigDecimal parentQty = MonetaryAmountSupport.nullToZero(parent.getQuantityValue());
        String parentBusNo = "";
        if (StringUtils.isNotEmpty(parent.getBusNo())) {
            parentBusNo = parent.getBusNo().trim();
        }
        CreateChargeItemCommand childCmd = new CreateChargeItemCommand();
        childCmd.setOperatorId(operatorId);
        List<CreateChargeItemCommand.Item> items = new ArrayList<>();
        BigDecimal childSum = BigDecimal.ZERO;
        for (ActivityDefinitionPackageChildResolvedDto child : children) {
            // 子表脏行跳过
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            ChargeItemDefinition saleLine = saleByChildId.get(child.getChildrenId());
            // Gate：缺 SALE 无法冻价落账
            if (saleLine == null || saleLine.getPriceValue() == null) {
                String childName = this.resolveChildDisplayName(child);
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                    "子项缺少有效售价，无法落子项账单：" + childName);
            }
            BigDecimal childNum = BigDecimal.ONE;
            // 子项系数空或非法时按 1（与目录 children_num 空默认一致）
            if (child.getChildrenNum() != null && child.getChildrenNum() > 0) {
                childNum = BigDecimal.valueOf(child.getChildrenNum());
            }
            BigDecimal quantity = parentQty.multiply(childNum);
            BigDecimal unitPrice = MonetaryAmountSupport.scaleCalc(saleLine.getPriceValue());
            BigDecimal lineAmount = MonetaryAmountSupport.lineTotal(unitPrice, quantity);
            CreateChargeItemCommand.Item item = new CreateChargeItemCommand.Item();
            item.setPatientId(parent.getPatientId());
            item.setEncounterId(parent.getEncounterId());
            item.setAccountId(parent.getAccountId());
            item.setDefinitionId(saleLine.getId());
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(lineAmount);
            item.setQuantityValue(quantity);
            item.setQuantityUnit(parent.getQuantityUnit());
            item.setBaseAmount(lineAmount);
            item.setDiscountAmount(lineAmount);
            item.setSurchargeAmount(BigDecimal.ZERO);
            item.setOccurrenceTime(parent.getOccurrenceTime());
            item.setServiceTable(parent.getServiceTable());
            item.setServiceId(parent.getServiceId());
            item.setProductTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
            item.setProductId(child.getChildrenId());
            item.setOrgId(parent.getOrgId());
            item.setRequestingOrgId(parent.getRequestingOrgId());
            item.setGenerateSourceEnum(parent.getGenerateSourceEnum());
            item.setContextEnum(parent.getContextEnum());
            item.setProcedureId(parent.getProcedureId());
            item.setEncounterDiagnosisIds(parent.getEncounterDiagnosisIds());
            item.setParentId(parentChargeItemId);
            // 子项账单 bus_no = 父流水 + "." + 子项 id（DM-ADM-011 §9.1）
            if (StringUtils.isNotEmpty(parentBusNo)) {
                item.setBusNo(parentBusNo + "." + child.getChildrenId());
            }
            items.add(item);
            childSum = childSum.add(lineAmount);
        }
        // Gate：有效子行空则不写
        if (items.isEmpty()) {
            return;
        }
        // Gate：子合计须等于父 totalPrice，否则落账与展示不一致
        BigDecimal parentTotal = MonetaryAmountSupport.scaleCalc(parent.getTotalPrice());
        if (MonetaryAmountSupport.scaleCalc(childSum).compareTo(parentTotal) != 0) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_ITEM_INVALID_ARGUMENT,
                "带子项诊疗落子账单失败：父项金额与子项合计不一致，父项="
                    + parentTotal
                    + "，子项合计="
                    + MonetaryAmountSupport.scaleCalc(childSum));
        }
        childCmd.setItems(items);
        // Cmd：子行与父同行态落库（草稿/待收费/待结算 + 生成来源）
        this.persistChildChargeItemsMatchingParent(parent, childCmd);
    }

    /**
     * 按父行状态与生成来源选择同态创建入口，避免门诊草稿父行落出待结算子行。
     *
     * @param parent  已落库父收费行
     * @param childCmd 已组装的子项账单命令
     */
    private void persistChildChargeItemsMatchingParent(ChargeItem parent, CreateChargeItemCommand childCmd) {
        String status = parent.getStatusEnum();
        String source = parent.getGenerateSourceEnum();
        // 门诊开立草稿：子行同为 DRAFT，签发后随锚点一并升 PLANNED
        if (ChargeItemStatus.DRAFT.getCode().equals(status)) {
            chargeItemDomainService.createDraftChargeItemFromOrder(childCmd);
            return;
        }
        // 已签发待收费：先落草稿再按锚点升 PLANNED（不误升同锚点已非草稿的父行）
        if (ChargeItemStatus.PLANNED.getCode().equals(status)) {
            chargeItemDomainService.createDraftChargeItemFromOrder(childCmd);
            chargeItemDomainService.planDraftChargeItemsAfterAdviceSigned(
                parent.getEncounterId(), parent.getServiceTable(), parent.getServiceId());
            return;
        }
        // 护士划价待结算
        if (GenerateSource.NURSE_PRICING.getCode().equals(source)) {
            chargeItemDomainService.priceByNurseStation(childCmd);
            return;
        }
        // 住院滚费待结算
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(source)) {
            chargeItemDomainService.priceByAutoRoll(childCmd);
            return;
        }
        // 默认：医嘱执行待结算
        chargeItemDomainService.createPlannedChargeItemFromOrderExecution(childCmd);
    }

    /**
     * 批量加载子项现行 SALE 定价行（落账瞬间冻价用）。
     *
     * @param children 已解析子项
     * @return childrenId → SALE 行
     */
    private Map<Long, ChargeItemDefinition> loadChildSalePriceLines(
        List<ActivityDefinitionPackageChildResolvedDto> children
    ) {
        List<Long> childIds = new ArrayList<>();
        // 收集有效子项 id，脏行不进定价查询
        for (ActivityDefinitionPackageChildResolvedDto child : children) {
            if (child != null && child.getChildrenId() != null) {
                childIds.add(child.getChildrenId());
            }
        }
        Map<Long, ChargeItemDefinition> saleByChildId = new HashMap<>();
        // 无有效子项则无需查定价
        if (childIds.isEmpty()) {
            return saleByChildId;
        }
        ChargeItemDefinitionQueryCommand priceQuery = new ChargeItemDefinitionQueryCommand();
        priceQuery.setInstanceTable(TableNameConstants.WKF_ACTIVITY_DEFINITION);
        priceQuery.setInstanceIds(childIds);
        List<ChargeItemDefinition> priceLines =
            chargeItemDefinitionQueryService.findPriceLinesByProjects(priceQuery);
        // 无定价行则冻价失败由调用方缺 SALE 抛错
        if (priceLines == null || priceLines.isEmpty()) {
            return saleByChildId;
        }
        Map<Long, List<ChargeItemDefinition>> linesByInstanceId = new HashMap<>();
        for (ChargeItemDefinition line : priceLines) {
            // 脏定价行跳过
            if (line == null || line.getInstanceId() == null) {
                continue;
            }
            linesByInstanceId.computeIfAbsent(line.getInstanceId(), key -> new ArrayList<>()).add(line);
        }
        for (Long childId : childIds) {
            List<ChargeItemDefinition> lines = linesByInstanceId.get(childId);
            ChargeItemDefinition sale = clinicalCatalogPriceResolver.selectFirstPriceLineForChargeDraft(
                TableNameConstants.WKF_ACTIVITY_DEFINITION, lines);
            // 命中 SALE 才写入冻价索引
            if (sale != null) {
                saleByChildId.put(childId, sale);
            }
        }
        return saleByChildId;
    }

    /**
     * 子项缺价报错用展示名。
     *
     * @param child 已解析子项
     * @return 非空展示串
     */
    private String resolveChildDisplayName(ActivityDefinitionPackageChildResolvedDto child) {
        // 优先目录产品名
        if (child.getChildProduct() != null
            && StringUtils.isNotEmpty(child.getChildProduct().getProductName())) {
            return child.getChildProduct().getProductName().trim();
        }
        // 回落子表快照名
        if (StringUtils.isNotEmpty(child.getChildrenName())) {
            return child.getChildrenName().trim();
        }
        return String.valueOf(child.getChildrenId());
    }
}
