/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.warehouse.inboundreceipt;

import com.zxplat.application.inventory.warehouse.inboundreceipt.dto.InboundReceiptLineDto;
import com.zxplat.application.inventory.warehouse.inboundreceipt.request.AgreeInboundReceiptRequest;
import com.zxplat.application.inventory.warehouse.inboundreceipt.support.ChargePricingTypeCodeResolver;
import com.zxplat.application.shared.pricing.CatalogReferencePriceSyncSupport;
import com.zxplat.application.shared.pricing.LotNumberPurchasePriceConsistencyGate;
import com.zxplat.application.shared.pricing.PackageUnitPriceScaleGate;
import com.zxplat.application.shared.product.ProductDefinitionCatalogUnitBandsAssembler;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver;
import com.zxplat.application.shared.validator.CurrentPractitionerResolver;
import com.zxplat.domain.administration.chargeitemdefinition.command.DeactivateActivePriceLinesCommand;
import com.zxplat.domain.administration.chargeitemdefinition.command.SavePriceLineCommand;
import com.zxplat.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.zxplat.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionDomainService;
import com.zxplat.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.zxplat.domain.custom.priceadjustment.command.ApprovePriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.command.SavePriceAdjustmentDraftCommand;
import com.zxplat.domain.custom.priceadjustment.command.SubmitPriceAdjustmentCommand;
import com.zxplat.domain.custom.priceadjustment.service.PriceAdjustmentDomainService;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.zxplat.domain.share.constants.DailySettlementSharedConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PriceType;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.FinTypeCodeMappingSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.domain.workflow.supplyrequest.queries.SupplyByBusNoQuery;
import com.zxplat.domain.workflow.supplyrequest.queries.model.SupplyRequestModel;
import com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产品药库进货中草药自动加价规则（保存门禁 + 同意后写批号售价与调价审计）。
 *
 * <p>对应文档：中草药进货批号自动加价。分类码真源
 * {@link DailySettlementSharedConstants#MEDICATION_CATEGORY_HERBAL_CODE}（{@code "4"}）。</p>
 *
 * <p>写价只经 {@link ChargeItemDefinitionDomainService}（停用旧 {@code LOT_NUMBER_PRICE} 再新增）；
 * 加价后经 {@link CatalogReferencePriceSyncSupport} 回写实例级 {@code SALE}（不改 {@code PURCHASE}）；
 * 审计只经 {@link PriceAdjustmentDomainService} 草稿→提交→审批通过，
 * <strong>不</strong>调用正式 {@code PriceAdjustmentApprovePricingOrchestration}（避免整品/多批号误改）。</p>
 */
@Component
@RequiredArgsConstructor
public class HerbalInboundReceiptSupport {

    private final MedicationDefinitionQueryService medicationDefinitionQueryService;
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;
    private final ChargeItemDefinitionDomainService chargeItemDefinitionDomainService;
    private final CatalogReferencePriceSyncSupport catalogReferencePriceSyncSupport;
    private final PriceAdjustmentDomainService priceAdjustmentDomainService;
    private final SupplyRequestQueryService supplyRequestQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final CurrentPractitionerResolver currentPractitionerResolver;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;
    private final LotNumberPurchasePriceConsistencyGate lotNumberPurchasePriceConsistencyGate;

    /**
     * 保存前门禁（IF-WHS-IN-04/05）：中草药同批号异价走核心 {@link LotNumberPurchasePriceConsistencyGate}（BR-WHS-IN-018）。
     *
     * <p>本切面不再单独硬拒异价；零库存且无收费/发放时允许改价，与药库进货核心一致。</p>
     *
     * @param lines 进货明细；空则直接返回
     * @throws BusinessException 同批号进价变更且仍有库存/收费/发放时，错误码 {@link ErrorCodeConstants#WHS_IN_001}
     */
    public void assertHerbalLotPurchasePriceUnchanged(List<InboundReceiptLineDto> lines) {
        // Gate：无明细时无需本切面门禁
        if (lines == null || lines.isEmpty()) {
            return;
        }
        // Pre：仅中草药行参与本切面前置门禁（核心仍会对全单再跑一次门禁）
        List<InboundReceiptLineDto> herbalLines = this.collectHerbalMedicationLines(lines);
        if (herbalLines.isEmpty()) {
            return;
        }
        List<LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine> checkLines = new ArrayList<>();
        for (InboundReceiptLineDto line : herbalLines) {
            if (line == null) {
                continue;
            }
            LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine check =
                new LotNumberPurchasePriceConsistencyGate.LotPurchasePriceCheckLine();
            check.setItemTable(line.getItemTable());
            check.setItemId(line.getItemId());
            check.setLotNumber(line.getLotNumber());
            check.setPrice(line.getPrice());
            check.setUnitCode(line.getUnitCode());
            checkLines.add(check);
        }
        this.lotNumberPurchasePriceConsistencyGate.assertIncomingLotPurchasePricesConsistent(
            checkLines, ErrorCodeConstants.WHS_IN_001);
    }

    /**
     * 同意入库后（IF-WHS-IN-08 after）：本单中草药批号售价改为进价 ×1.25（第三位起舍弃），并落「中药自动调价」已通过审计。
     *
     * <p>改 {@code LOT_NUMBER_PRICE} 与实例级 {@code SALE}（对齐加价后售价，供目录默认）；
     * 不改 {@code LOT_NUMBER_COST} / 实例级 {@code PURCHASE}（核心同意已按进价回写）。
     * 批号售价已等于目标值时幂等跳过批号写价与审计；参考价仍按目标售价幂等对齐。</p>
     *
     * @param request 同意请求（取 busNo 回读供应行）
     * @throws BusinessException 批号缺失、缺 type_code、写价/审计失败，或同品存在待审批调价单（pending 互斥）时
     */
    public void applyHerbalLotSaleMarkupAfterAgree(AgreeInboundReceiptRequest request) {
        // Gate：单号缺失则无法回读进货明细
        if (request == null || StringUtils.isEmpty(request.getBusNo())) {
            return;
        }
        this.applyHerbalLotSaleMarkupAfterAgreeByBusNo(request.getBusNo().trim());
    }

    /**
     * 按业务单号对中草药行做批号售价 ×1.25（进货同意 / 期初同意共用）。
     *
     * <p>回读 {@code wkf_supply_request} 同号行后筛中草药；写价与审计规则与进货一致。</p>
     *
     * @param busNo 已同意的进货单或期初单号
     * @throws BusinessException 批号缺失、缺 type_code、写价/审计失败，或同品存在待审批调价单时
     */
    public void applyHerbalLotSaleMarkupAfterAgreeByBusNo(String busNo) {
        // Gate：单号缺失则无法回读明细
        if (StringUtils.isEmpty(busNo)) {
            return;
        }
        String trimmedBusNo = busNo.trim();

        // Pre：回读同意后供应行，筛中草药并计算目标售价
        SupplyByBusNoQuery query = new SupplyByBusNoQuery();
        query.setBusNo(trimmedBusNo);
        List<SupplyRequestModel> agreedLines = this.supplyRequestQueryService.getSupplyByBusNo(query);
        // 回读为空：核心同意后不应出现；保守返回避免空指针
        if (agreedLines == null || agreedLines.isEmpty()) {
            return;
        }

        List<SupplyRequestModel> herbalSupplyLines = this.collectHerbalSupplyLines(agreedLines);
        // 本单无中草药：不加价
        if (herbalSupplyLines.isEmpty()) {
            return;
        }

        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.loadProductDisplayMapFromSupplyLines(herbalSupplyLines);
        Long operatorId = this.currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.WHS_IN_101, "用户未登录");
        Long orgId = this.managedOrganizationIdsResolver.requireSessionOrganizationUnitId(
            ErrorCodeConstants.WHS_IN_101, "机构上下文缺失");

        // Pre：按（物品表+物品+批号）去重，与核心同意写价键一致；先收集键再批量读当前批号售价（避免 N+1）
        Map<String, MarkupCandidate> candidatesByLotKey = new LinkedHashMap<>();
        List<ChargeItemDefinitionLotBatchKey> salePriceKeys = new ArrayList<>();
        for (SupplyRequestModel line : herbalSupplyLines) {
            String lotNo = StringUtils.trimToNull(line.getLotNumber());
            // 中草药无批号：无法按批号写售价，中止同意事务
            if (lotNo == null) {
                throw new BusinessException(ErrorCodeConstants.WHS_IN_104, "中草药批号不能为空，无法自动加价");
            }
            String itemTable = line.getItemTable().trim();
            Long itemId = line.getItemId();
            String dedupeKey = itemTable + '\u0001' + itemId + '\u0001' + lotNo;
            // 同批号多行：只处理首次，避免重复停用/新增
            if (candidatesByLotKey.containsKey(dedupeKey)) {
                continue;
            }
            ProductDefinitionDisplayDto product =
                productByKey.get(ProductDefinitionKeyUtil.buildCompositeKey(itemTable, itemId));
            BigDecimal purchasePrice =
                ProductDefinitionCatalogUnitBandsAssembler.resolveLotNumberPackageUnitPriceForInboundAgree(
                    line.getPrice(), line.getUnitCode(), product);
            BigDecimal targetSale = this.computeHerbalLotSalePrice(purchasePrice);
            // Gate：加价后真实售价维护位数（≤2）；除不尽不拒绝
            PackageUnitPriceScaleGate.assertForProductLotWrite(
                product, purchasePrice, targetSale, "中草药进货加价失败");
            // 中草药优先用目录分类推 fin_type，避免逐条查同实例历史定价行
            String typeCode = null;
            if (product != null) {
                typeCode = FinTypeCodeMappingSupport.normalizeCategoryCodeToFinType(product.getCategoryCode());
            }
            if (StringUtils.isEmpty(typeCode)) {
                typeCode = ChargePricingTypeCodeResolver.resolveTypeCodeForLotPriceLine(
                    itemTable, itemId, product, this.chargeItemDefinitionQueryService);
            }
            // 新建定价行必须有 type_code（BR-CHGDEF-WR-001）
            if (StringUtils.isEmpty(typeCode)) {
                throw new BusinessException(
                    ErrorCodeConstants.CHARGE_ITEM_DEFINITION_INVALID_ARGUMENT,
                    "中草药批号售价加价缺少财务类型 type_code，itemId=" + itemId);
            }
            MarkupCandidate candidate = new MarkupCandidate();
            candidate.itemTable = itemTable;
            candidate.itemId = itemId;
            candidate.lotNo = lotNo;
            candidate.purchasePrice = purchasePrice;
            candidate.targetSalePrice = targetSale;
            candidate.typeCode = typeCode.trim();
            candidate.quantity = this.resolveAuditQuantity(line.getItemQuantity());
            candidatesByLotKey.put(dedupeKey, candidate);
            ChargeItemDefinitionLotBatchKey saleKey =
                ChargeItemDefinitionLotBatchKey.maybeOf(itemTable, itemId, lotNo);
            if (saleKey != null) {
                salePriceKeys.add(saleKey);
            }
        }

        Map<String, List<ChargeItemDefinition>> saleByKey =
            this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(salePriceKeys);
        for (MarkupCandidate candidate : candidatesByLotKey.values()) {
            ChargeItemDefinitionLotBatchKey saleKey =
                ChargeItemDefinitionLotBatchKey.maybeOf(candidate.itemTable, candidate.itemId, candidate.lotNo);
            if (saleKey == null) {
                continue;
            }
            candidate.oldSalePrice = this.selectFirstNonNullPriceValue(saleByKey.get(saleKey.compositeKey()));
        }

        List<MarkupCandidate> needWrite = new ArrayList<>();
        for (MarkupCandidate candidate : candidatesByLotKey.values()) {
            // 售价已是加价结果：幂等跳过批号写价与审计，避免同批号再次进货产生重复 PAD
            if (candidate.oldSalePrice != null
                && candidate.oldSalePrice.compareTo(candidate.targetSalePrice) == 0) {
                continue;
            }
            needWrite.add(candidate);
        }
        // Cmd：批号售价需校正时停用旧 ACTIVE 再批量新增
        if (!needWrite.isEmpty()) {
            // Cmd：仅停用已有本批号 LOT_NUMBER_PRICE（无旧售价则跳过，少一轮空查）
            List<DeactivateActivePriceLinesCommand.Item> deactivateItems = new ArrayList<>();
            for (MarkupCandidate candidate : needWrite) {
                if (candidate.oldSalePrice == null) {
                    continue;
                }
                DeactivateActivePriceLinesCommand.Item item = new DeactivateActivePriceLinesCommand.Item();
                item.setInstanceTable(candidate.itemTable);
                item.setInstanceId(candidate.itemId);
                item.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                item.setLotNo(candidate.lotNo);
                deactivateItems.add(item);
            }
            if (!deactivateItems.isEmpty()) {
                DeactivateActivePriceLinesCommand deactivateCmd = new DeactivateActivePriceLinesCommand();
                deactivateCmd.setOperatorId(operatorId);
                deactivateCmd.setItems(deactivateItems);
                this.chargeItemDefinitionDomainService.deactivateActivePriceLinesByCommand(deactivateCmd);
            }

            // Cmd：一次批量新增全部批号售价（saveAll），避免逐条落库
            SavePriceLineCommand saleCmd = new SavePriceLineCommand();
            saleCmd.setOperatorId(operatorId);
            List<SavePriceLineCommand.Item> saleItems = new ArrayList<>();
            for (MarkupCandidate candidate : needWrite) {
                SavePriceLineCommand.Item saleItem = new SavePriceLineCommand.Item();
                saleItem.setInstanceTable(candidate.itemTable);
                saleItem.setInstanceId(candidate.itemId);
                saleItem.setTypeCode(candidate.typeCode);
                saleItem.setPriceTypeCode(PriceType.LOT_NUMBER_PRICE.getCode());
                saleItem.setLotNo(candidate.lotNo);
                saleItem.setPriceValue(candidate.targetSalePrice);
                saleItems.add(saleItem);
            }
            saleCmd.setItems(saleItems);
            this.chargeItemDefinitionDomainService.addLotSalePrice(saleCmd);

            // Cmd：一张「中药自动调价」多行审计单，迁到 approved（不跑正式审批写价编排）
            this.persistAutoMarkupAuditDocument(needWrite, operatorId, orgId);
        }

        // Cmd：目录销售参考价对齐加价后批号售价（核心已写 PURCHASE=进价；此处只抬 SALE）
        for (MarkupCandidate candidate : candidatesByLotKey.values()) {
            this.catalogReferencePriceSyncSupport.syncSaleIfChanged(
                candidate.itemTable,
                candidate.itemId,
                candidate.typeCode,
                candidate.targetSalePrice,
                operatorId);
        }
    }

    /**
     * 进价 ×1.25 后保留两位小数，第三位及以后直接舍弃（非四舍五入；产品设计特例）。
     *
     * @param purchasePrice 每包装单位批号进价；空则按 0 计算
     * @return 目标批号售价
     */
    private BigDecimal computeHerbalLotSalePrice(BigDecimal purchasePrice) {
        BigDecimal base = purchasePrice;
        // 空进价按 0，避免 NPE；业务上进货行通常已有单价
        if (base == null) {
            base = BigDecimal.ZERO;
        }
        return base.multiply(HerbalInboundReceiptConstants.HERBAL_MARKUP_RATE)
            .setScale(2, RoundingMode.DOWN);
    }

    /**
     * 从进货写模型行中筛出药品表且 {@code category_code} 为中草药的行（批量预读分类，禁止按 label 分支）。
     *
     * @param lines 进货明细
     * @return 中草药行；无命中为空列表
     */
    private List<InboundReceiptLineDto> collectHerbalMedicationLines(List<InboundReceiptLineDto> lines) {
        Set<Long> medicationIds = new HashSet<>();
        for (InboundReceiptLineDto line : lines) {
            // 非药品表行：不参与中草药判定
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable().trim())) {
                continue;
            }
            medicationIds.add(line.getItemId());
        }
        Map<Long, String> categoryById = this.loadCategoryCodeByMedicationId(medicationIds);
        List<InboundReceiptLineDto> herbalLines = new ArrayList<>();
        for (InboundReceiptLineDto line : lines) {
            // 非药品或非中草药分类：完全走核心，不入本切面门禁
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable().trim())) {
                continue;
            }
            String categoryCode = categoryById.get(line.getItemId());
            if (!DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE.equals(categoryCode)) {
                continue;
            }
            herbalLines.add(line);
        }
        return herbalLines;
    }

    /**
     * 从供应请求行中筛出中草药药品行（与保存门禁同一分类口径）。
     *
     * @param lines 同意后供应行
     * @return 中草药行
     */
    private List<SupplyRequestModel> collectHerbalSupplyLines(List<SupplyRequestModel> lines) {
        Set<Long> medicationIds = new HashSet<>();
        for (SupplyRequestModel line : lines) {
            // 非药品表行：跳过分类预取
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable().trim())) {
                continue;
            }
            medicationIds.add(line.getItemId());
        }
        Map<Long, String> categoryById = this.loadCategoryCodeByMedicationId(medicationIds);
        List<SupplyRequestModel> herbalLines = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            // 非中草药：同意后不加价
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            if (!TableNameConstants.MED_MEDICATION_DEFINITION.equals(line.getItemTable().trim())) {
                continue;
            }
            String categoryCode = categoryById.get(line.getItemId());
            if (!DailySettlementSharedConstants.MEDICATION_CATEGORY_HERBAL_CODE.equals(categoryCode)) {
                continue;
            }
            herbalLines.add(line);
        }
        return herbalLines;
    }

    /**
     * 批量解析药品定义 {@code category_code}（等价于逐条
     * {@link MedicationDefinitionQueryService#resolveCategoryCodeByMedicationDefinitionId}，避免循环查库）。
     *
     * @param medicationIds 药品定义主键集合
     * @return id → category_code
     */
    private Map<Long, String> loadCategoryCodeByMedicationId(Set<Long> medicationIds) {
        Map<Long, String> categoryById = new HashMap<>();
        // 空集合：避免无意义仓储访问
        if (medicationIds == null || medicationIds.isEmpty()) {
            return categoryById;
        }
        List<MedicationDefinition> definitions = this.medicationDefinitionQueryService.findAllByIds(medicationIds);
        for (MedicationDefinition definition : definitions) {
            if (definition == null || definition.getId() == null) {
                continue;
            }
            categoryById.put(definition.getId(), definition.getCategoryCode());
        }
        return categoryById;
    }

    /**
     * 按进货写模型行批量加载产品展示读模型（单位换算用）。
     *
     * @param lines 中草药进货行
     * @return 复合键 → 产品读模型
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayMapFromInboundLines(
        List<InboundReceiptLineDto> lines
    ) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (InboundReceiptLineDto line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable().trim());
            lookups.add(lookup);
        }
        // 无有效键：返回空映射，调用方按无产品定义回落归一
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 按供应行批量加载产品展示读模型。
     *
     * @param lines 中草药供应行
     * @return 复合键 → 产品读模型
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductDisplayMapFromSupplyLines(
        List<SupplyRequestModel> lines
    ) {
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (SupplyRequestModel line : lines) {
            if (line == null || line.getItemId() == null || StringUtils.isEmpty(line.getItemTable())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductDefinitionId(line.getItemId());
            lookup.setProductTableName(line.getItemTable().trim());
            lookups.add(lookup);
        }
        // 无有效键：返回空映射
        if (lookups.isEmpty()) {
            return new HashMap<>();
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 从 ACTIVE 定价行列表取首个非空 {@code priceValue}（稳定择优）。
     *
     * @param rows 定价行
     * @return 单价；无命中为 null
     */
    private BigDecimal selectFirstNonNullPriceValue(List<ChargeItemDefinition> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition row : rows) {
            // 取首个非空价，与库房批号进价择优口径一致
            if (row != null && row.getPriceValue() != null) {
                return row.getPriceValue();
            }
        }
        return null;
    }

    /**
     * 审计子表数量：供应行数量无效时回落 1，满足领域「数量须大于零」。
     *
     * @param itemQuantity 供应行数量
     * @return 正数量
     */
    private BigDecimal resolveAuditQuantity(BigDecimal itemQuantity) {
        // 数量非法时用 1，避免审计提交被领域数量校验拦下
        if (itemQuantity == null || itemQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return itemQuantity;
    }

    /**
     * 落调价审计：一张多行单（草稿→提交→已通过）；写价效果已由进货钩子完成。
     *
     * <p>每候选一行主表 + 一批号明细，避免同品多批号目标售价不同时无法共用主行
     * {@code new_selling_amount}；合并为一张单可避免数百次草稿/提交/审批往返。
     * 若同品已有人工 pending 调价单，submit 会因互斥抛错并回滚整次同意。</p>
     *
     * @param candidates 待审计加价项
     * @param operatorId 操作人
     * @param orgId      机构
     */
    private void persistAutoMarkupAuditDocument(
        List<MarkupCandidate> candidates,
        Long operatorId,
        Long orgId
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }

        List<SavePriceAdjustmentDraftCommand.Line> lines = new ArrayList<>(candidates.size());
        for (MarkupCandidate candidate : candidates) {
            SavePriceAdjustmentDraftCommand.DetailLine detail = new SavePriceAdjustmentDraftCommand.DetailLine();
            detail.setLotNumber(candidate.lotNo);
            detail.setQuantity(candidate.quantity);
            detail.setOldPurchaseAmount(candidate.purchasePrice);
            BigDecimal oldSale = candidate.oldSalePrice;
            // 核心同意刚写入的批号售价常等于进价；无读到时用进价作调前售价
            if (oldSale == null) {
                oldSale = candidate.purchasePrice;
            }
            detail.setOldSellingAmount(oldSale);

            SavePriceAdjustmentDraftCommand.Line line = new SavePriceAdjustmentDraftCommand.Line();
            line.setItemTable(candidate.itemTable);
            line.setItemId(candidate.itemId);
            // 只记售价变化，目标进价留空（优先「只记售价变化」）
            line.setNewPurchaseAmount(null);
            line.setNewSellingAmount(candidate.targetSalePrice);
            List<SavePriceAdjustmentDraftCommand.DetailLine> details = new ArrayList<>(1);
            details.add(detail);
            line.setDetails(details);
            lines.add(line);
        }

        SavePriceAdjustmentDraftCommand draftCmd = new SavePriceAdjustmentDraftCommand();
        draftCmd.setOperatorId(operatorId);
        draftCmd.setOrgId(orgId);
        draftCmd.setAdjustmentReason(HerbalInboundReceiptConstants.AUTO_MARKUP_ADJUSTMENT_REASON);
        draftCmd.setLines(lines);

        // 持久化：草稿 → pending → approved（一次）；写价已在进货钩子完成，禁止再调 ApprovePricingOrchestration
        String padBusNo = this.priceAdjustmentDomainService.savePriceAdjustmentDraft(draftCmd);

        SubmitPriceAdjustmentCommand submitCmd = new SubmitPriceAdjustmentCommand();
        submitCmd.setBusNo(padBusNo);
        submitCmd.setSubmitterId(operatorId);
        this.priceAdjustmentDomainService.submitPriceAdjustmentForApproval(submitCmd);

        ApprovePriceAdjustmentCommand approveCmd = new ApprovePriceAdjustmentCommand();
        approveCmd.setBusNo(padBusNo);
        approveCmd.setApproverId(operatorId);
        approveCmd.setApproveComment(HerbalInboundReceiptConstants.AUTO_MARKUP_ADJUSTMENT_REASON);
        approveCmd.setOperatorId(operatorId);
        this.priceAdjustmentDomainService.approvePriceAdjustment(approveCmd);
    }

    /**
     * 同意后待写价/审计的中草药批号候选（内存装配，不落库）。
     */
    private static final class MarkupCandidate {

        /** 物品物理表（药品定义表）。 */
        private String itemTable;
        /** 药品定义主键 {@code item_id}。 */
        private Long itemId;
        /** 批号（已 trim）。 */
        private String lotNo;
        /** 归一后每包装单位批号进价。 */
        private BigDecimal purchasePrice;
        /** 目标批号售价（进价 ×1.25，{@code RoundingMode.DOWN} 两位）。 */
        private BigDecimal targetSalePrice;
        /** 写价前当前批号售价；可能为空或等于进价。 */
        private BigDecimal oldSalePrice;
        /** 定价行财务类型 {@code type_code}。 */
        private String typeCode;
        /** 审计子表数量（来自供应行数量，非法时回落 1）。 */
        private BigDecimal quantity;
    }
}
