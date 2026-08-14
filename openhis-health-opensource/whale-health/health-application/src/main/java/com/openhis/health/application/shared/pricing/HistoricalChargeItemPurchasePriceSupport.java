/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.pricing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.service.ChargeItemDefinitionQueryService;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentDetailQueryModel;
import com.openhis.health.domain.custom.priceadjustment.queries.model.PriceAdjustmentLineQueryModel;
import com.openhis.health.domain.custom.priceadjustment.service.PriceAdjustmentQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.GenerateSource;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.devicedispense.entity.DeviceDispense;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 报表/费用明细进价解析（BR-PRICE-SRC-001；只读；性能优先批号进价）。
 *
 * <p>药耗主路径：批量查当前 ACTIVE {@link PriceType#LOT_NUMBER_COST}（同批号
 * {@link PriceType#LOT_NUMBER_PRICE} 为缺进价时的回落；西药/耗材进货写价同值，中药院点加价后进售可不同）。
 * 批号来源优先级：发放单 {@code lot_number} → 收费锚点定价行 {@code lot_no}（{@code LOT_NUMBER_PRICE}/{@code LOT_NUMBER_COST}）。
 * <strong>品规无已审批调价时禁止触达任何调价历史/定价兄弟行</strong>，直接用批号进/售价。
 * 仅<strong>药耗</strong>有调价时才按业务时刻回放<strong>调前进/售价</strong>（批号须一致，禁止串批；
 * 售价回放见 {@link #resolveLotSaleUnitPrice}）。
 * 诊疗/挂号目录 {@link PriceType#SALE}<strong>不回放</strong>。
 * 患者侧药耗售价优先账单；账单缺失（如取消执行逻辑删）时<strong>仅</strong>允许回落批号售价的场景：
 * <strong>药房日结</strong>、<strong>住院发药报表/药品使用明细</strong>（正/退同行，与日结轧差对齐）；其它报表/费用明细/门诊发退药等仍只认账单。
 * <strong>禁止</strong>回落目录 {@link PriceType#PURCHASE}（药典参考价不参与进价统计；有批号用批号进/售，无批号药耗跳过）。
 * 诊疗/挂号<strong>无真实进价</strong>：进价单价/行额解析结果为 {@code null}（不编进价；禁止用账单价冒充进价；
 * 见 {@code DD-报表中心-进售价口径与开立科室-2026-08}）。
 * <strong>禁止</strong>{@code listHistoricalDefinitionsByInstance} 无脑全量兄弟扫描。</p>
 */
@Component
@RequiredArgsConstructor
public class HistoricalChargeItemPurchasePriceSupport {

    /** 定价行批量读侧。 */
    private final ChargeItemDefinitionQueryService chargeItemDefinitionQueryService;

    /** 调价已审批历史读侧。 */
    private final PriceAdjustmentQueryService priceAdjustmentQueryService;

    /** 药品发放批号读侧。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 耗材发放批号读侧。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 按操作单位计行进价（包装直接×数量 / 拆零满包+零头）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /**
     * 批量计算 chargeItem 行进价总额（未带收退符号；6 位 HALF_UP）。
     *
     * <p>内部先解析单价再推导行额；若调用方已持有单价 Map，改用
     * {@link #computePurchaseAmountFromUnitPrices}，避免定价/批号/调价二次扫描。</p>
     *
     * @param chargeItems 收费明细；空则返回空 map
     * @return chargeItemId → 行进价总额；无法解析时跳过该行
     */
    public Map<Long, BigDecimal> computePurchaseAmountByChargeItemId(Collection<ChargeItem> chargeItems) {
        Map<Long, BigDecimal> unitByChargeItemId = this.computePurchaseUnitPriceByChargeItemId(chargeItems);
        return this.computePurchaseAmountFromUnitPrices(chargeItems, unitByChargeItemId);
    }

    /**
     * 由已解析进价单价推导行进价总额（未带收退符号；6 位 HALF_UP）。
     *
     * <p>无包装倍数时退化为 {@code 单价×数量}（兼容药房日结等未改路径）。
     * 报表药耗请改用四参重载（含产品 Map），按操作单位分支（#4M-OP / BR-MON-PKG-001）计行额。
     * 本两参重载无倍数、无产品 Map，仅退化为 lineTotal，勿用于费用明细/汇总。</p>
     *
     * @param chargeItems                收费明细；空则返回空 map
     * @param purchaseUnitByChargeItemId chargeItemId → 进价单价（包装单位）；缺键跳过该行
     * @return chargeItemId → 行进价总额
     */
    public Map<Long, BigDecimal> computePurchaseAmountFromUnitPrices(
        Collection<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId) {
        return this.computePurchaseAmountFromUnitPrices(chargeItems, purchaseUnitByChargeItemId, null, null);
    }

    /**
     * 由已解析包装进价单价推导行进价总额（未带收退符号；6 位 HALF_UP）。
     *
     * <p><strong>费用明细 / 发药耗材 / 工作量须传产品 Map</strong>（四参重载）：先
     * {@link #computePurchaseUnitPriceByChargeItemId} 再本方法带 {@code productByCompositeKey}。
     * 本三参重载在无产品 Map 时有倍数则<strong>假定 quantity 已是拆零量</strong>；
     * ChargeItem 操作单位已是包装时会把进价压矮，禁止用于报表汇总。</p>
     *
     * @param chargeItems                    收费明细；空则返回空 map
     * @param purchaseUnitByChargeItemId     chargeItemId → 包装单位进价单价；缺键跳过
     * @param packageUnitRatioByChargeItemId chargeItemId → 1 包装含拆零数；可空
     * @return chargeItemId → 行进价总额
     */
    public Map<Long, BigDecimal> computePurchaseAmountFromUnitPrices(
        Collection<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId) {
        return this.computePurchaseAmountFromUnitPrices(
            chargeItems, purchaseUnitByChargeItemId, packageUnitRatioByChargeItemId, null);
    }

    /**
     * 由已解析包装进价单价推导行进价总额（按操作单位分支；6 位 HALF_UP）。
     *
     * <p>有 {@code productByCompositeKey} 时委托
     * {@link ChargeItemPackageUnitRatioSupport#computePurchaseLineAmount}：
     * 包装操作单位 → 包装价×包装数量；拆零操作单位 → 满包×包装价+零头单步（BR-MON-PKG-001）。
     * <strong>禁止</strong>把包装数量折成拆零量再算，也禁止包装价÷ratio 再×数量。</p>
     *
     * @param chargeItems                    收费明细；空则返回空 map
     * @param purchaseUnitByChargeItemId     chargeItemId → 包装单位进价单价；缺键跳过
     * @param packageUnitRatioByChargeItemId chargeItemId → 1 包装含拆零数；可空
     * @param productByCompositeKey          复合键 → 产品（判操作单位）；可空
     * @return chargeItemId → 行进价总额
     */
    public Map<Long, BigDecimal> computePurchaseAmountFromUnitPrices(
        Collection<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        Map<Long, BigDecimal> purchaseByChargeItemId = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty() || purchaseUnitByChargeItemId == null) {
            return purchaseByChargeItemId;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal unit = purchaseUnitByChargeItemId.get(chargeItem.getId());
            if (unit == null) {
                continue;
            }
            BigDecimal ratio = null;
            if (packageUnitRatioByChargeItemId != null) {
                ratio = packageUnitRatioByChargeItemId.get(chargeItem.getId());
            }
            BigDecimal lineAmount;
            // Pre：有产品 Map 时按操作单位分支（包装直接×数量；拆零走满包+零头）
            if (productByCompositeKey != null && !productByCompositeKey.isEmpty()) {
                ProductDefinitionDisplayDto product =
                    this.chargeItemPackageUnitRatioSupport.resolveProduct(chargeItem, productByCompositeKey);
                lineAmount = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                    unit, ratio, chargeItem, product);
            } else if (ratio != null && ratio.compareTo(BigDecimal.ZERO) > 0) {
                // 无产品 Map：假定 quantity 已是拆零量（历史兼容路径）
                BigDecimal quantity = chargeItem.getQuantityValue();
                if (quantity == null) {
                    quantity = BigDecimal.ONE;
                }
                lineAmount = MonetaryAmountSupport.computeSplitQuantityLineTotal(quantity, ratio, unit);
            } else {
                BigDecimal quantity = chargeItem.getQuantityValue();
                if (quantity == null) {
                    quantity = BigDecimal.ONE;
                }
                lineAmount = MonetaryAmountSupport.lineTotal(unit, quantity);
            }
            if (lineAmount != null) {
                purchaseByChargeItemId.put(chargeItem.getId(), lineAmount);
            }
        }
        return purchaseByChargeItemId;
    }

    /**
     * 批量计算 chargeItem 行进价单价（未带收退符号；6 位 HALF_UP）。
     *
     * @param chargeItems 收费明细
     * @return chargeItemId → 进价单价；无法解析时跳过
     */
    public Map<Long, BigDecimal> computePurchaseUnitPriceByChargeItemId(Collection<ChargeItem> chargeItems) {
        Map<Long, BigDecimal> unitByChargeItemId = new LinkedHashMap<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return unitByChargeItemId;
        }
        LinkedHashSet<Long> definitionIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getDefinitionId() != null) {
                definitionIds.add(chargeItem.getDefinitionId());
            }
        }
        // Pre：仅加载收费锚点 definition，禁止 listHistoricalDefinitionsByInstance 全量兄弟扫描
        Map<Long, ChargeItemDefinition> definitionById =
            this.chargeItemDefinitionQueryService.findAllDefinitionsByIdIn(definitionIds);
        // Pre：批号优先发放单，缺失时回落锚点定价行 lot_no（LOT_NUMBER_PRICE/COST）
        Map<Long, String> lotByChargeItemId = this.loadLotNumberByChargeItemId(chargeItems, definitionById);
        PurchaseResolveContext resolveContext =
            this.buildPurchaseResolveContext(chargeItems, definitionById, lotByChargeItemId);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal unit = this.resolvePurchaseUnitForChargeItem(
                chargeItem, definitionById, lotByChargeItemId, resolveContext);
            if (unit != null) {
                unitByChargeItemId.put(chargeItem.getId(), unit);
            }
        }
        return unitByChargeItemId;
    }

    /**
     * 开启批号进/售价解析会话：一次筛有调价品规，并仅对这些品规批量预取调价历史。
     *
     * <p>无调价品规后续解析<strong>不会</strong>再查 exists/history。</p>
     *
     * @param itemTable 品规定义表（药/耗）
     * @param itemIds   本批品规主键
     * @return 可复用会话；itemIds 空时仍返回可用空会话
     */
    public LotPriceResolveSession beginLotPriceSession(String itemTable, Collection<Long> itemIds) {
        LotPriceResolveSession session = new LotPriceResolveSession();
        if (StringUtils.isEmpty(itemTable)) {
            return session;
        }
        session.itemTable = itemTable.trim();
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (itemIds != null) {
            for (Long itemId : itemIds) {
                if (itemId != null) {
                    distinct.add(itemId);
                }
            }
        }
        if (distinct.isEmpty()) {
            return session;
        }
        // Pre：仅筛有调价品规；无调价者后续直读批号价
        session.adjustedItemIds = this.loadAdjustedItemIds(session.itemTable, distinct);
        if (!session.adjustedItemIds.isEmpty()) {
            session.historyByItemId.putAll(
                this.priceAdjustmentQueryService.findApprovedHistoryByItems(
                    session.itemTable, session.adjustedItemIds));
        }
        return session;
    }

    /**
     * 批量预热会话内当前 ACTIVE 批号进/售价缓存，避免逐键 {@code List.of(key)} 查库。
     *
     * @param session  {@link #beginLotPriceSession} 产出；空则忽略
     * @param lotKeys  本批品规+批号键；可空
     */
    public void warmCurrentLotPrices(
        LotPriceResolveSession session,
        Collection<ChargeItemDefinitionLotBatchKey> lotKeys) {
        if (session == null || lotKeys == null || lotKeys.isEmpty()) {
            return;
        }
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> distinctKeys = new LinkedHashSet<>();
        for (ChargeItemDefinitionLotBatchKey key : lotKeys) {
            if (key != null) {
                distinctKeys.add(key);
            }
        }
        if (distinctKeys.isEmpty()) {
            return;
        }
        Map<String, List<ChargeItemDefinition>> lotCostByKey =
            this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(distinctKeys);
        Map<String, List<ChargeItemDefinition>> lotSaleByKey =
            this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(distinctKeys);
        for (ChargeItemDefinitionLotBatchKey key : distinctKeys) {
            String composite = key.compositeKey();
            if (!session.lotCostByCompositeKey.containsKey(composite)) {
                session.lotCostByCompositeKey.put(composite, this.firstLotCostPrice(lotCostByKey.get(composite)));
            }
            if (!session.lotSaleByCompositeKey.containsKey(composite)) {
                session.lotSaleByCompositeKey.put(composite, this.firstLotCostPrice(lotSaleByKey.get(composite)));
            }
        }
    }

    /**
     * 按品规+批号解析进价单价（发药/耗材明细已带批号时的轻量入口）。
     *
     * <p>无调价：直接 ACTIVE 批号进价；有调价：按业务时刻取调前价，否则仍用当前批号进价。
     * 批量场景请先 {@link #beginLotPriceSession} 再带 session 调用，避免逐行 exists/history。</p>
     *
     * @param instanceTable 品规定义表
     * @param instanceId    品规定义主键
     * @param lotNumber     批号
     * @param businessTime  业务时刻；可空（空则不做调价回放，直接当前批号进价）
     * @return 进价单价；无法解析时 {@code null}
     */
    public BigDecimal resolveLotPurchaseUnitPrice(
        String instanceTable,
        Long instanceId,
        String lotNumber,
        LocalDateTime businessTime) {
        return this.resolveLotPurchaseUnitPrice(instanceTable, instanceId, lotNumber, businessTime, null);
    }

    /**
     * 按品规+批号解析进价单价（可复用会话）。
     *
     * @param instanceTable 品规定义表
     * @param instanceId    品规定义主键
     * @param lotNumber     批号
     * @param businessTime  业务时刻；可空
     * @param session       {@link #beginLotPriceSession} 产出；可空（空则单次 exists，禁止批量无脑循环）
     * @return 进价单价；无法解析时 {@code null}
     */
    public BigDecimal resolveLotPurchaseUnitPrice(
        String instanceTable,
        Long instanceId,
        String lotNumber,
        LocalDateTime businessTime,
        LotPriceResolveSession session) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null || StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        String table = instanceTable.trim();
        String lot = lotNumber.trim();
        ChargeItemDefinitionLotBatchKey key = ChargeItemDefinitionLotBatchKey.maybeOf(table, instanceId, lot);
        if (key == null) {
            return null;
        }
        BigDecimal currentLotCost = this.resolveCurrentLotCost(key, session);
        if (businessTime == null) {
            return currentLotCost;
        }
        if (!this.isAdjustedInSessionOrLookup(table, instanceId, session)) {
            // 无调价：禁止查历史，直接批号进价
            return currentLotCost;
        }
        BigDecimal adjusted = this.resolvePurchaseFromAdjustmentHistory(
            table, instanceId, lot, businessTime, session);
        if (adjusted != null) {
            return adjusted;
        }
        return currentLotCost;
    }

    /**
     * 按品规+批号解析售价单价（发药明细已带批号时的轻量入口；仅 {@link PriceType#LOT_NUMBER_PRICE}）。
     *
     * <p>无调价：直接 ACTIVE 批号售价；有调价：按业务时刻取调前批号售价，否则仍用当前批号售价
     * （与 {@link #resolveLotPurchaseUnitPrice} 同构）。仅供「账单缺失回落」白名单场景调用：
     * 药房日结、住院发药报表/药品使用明细（正/退）；有账单时仍以 {@code ChargeItem} 为准。
     * <strong>禁止</strong>回落维护态 {@link PriceType#SALE}；禁止报表/费用明细等泛化回落。</p>
     *
     * @param instanceTable 品规定义表
     * @param instanceId    品规定义主键
     * @param lotNumber     批号
     * @param businessTime  业务时刻；可空（空则不做调价回放，直接当前批号售价）
     * @return 售价单价；无法解析时 {@code null}
     */
    public BigDecimal resolveLotSaleUnitPrice(
        String instanceTable,
        Long instanceId,
        String lotNumber,
        LocalDateTime businessTime) {
        return this.resolveLotSaleUnitPrice(instanceTable, instanceId, lotNumber, businessTime, null);
    }

    /**
     * 按品规+批号解析售价单价（可复用会话；同批号调价回放与进价同构）。
     *
     * @param instanceTable 品规定义表
     * @param instanceId    品规定义主键
     * @param lotNumber     批号
     * @param businessTime  业务时刻；可空（空则不做调价回放）
     * @param session       {@link #beginLotPriceSession} 产出；可空
     * @return 售价单价；无法解析时 {@code null}
     */
    public BigDecimal resolveLotSaleUnitPrice(
        String instanceTable,
        Long instanceId,
        String lotNumber,
        LocalDateTime businessTime,
        LotPriceResolveSession session) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null || StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        String table = instanceTable.trim();
        String lot = lotNumber.trim();
        ChargeItemDefinitionLotBatchKey key = ChargeItemDefinitionLotBatchKey.maybeOf(table, instanceId, lot);
        if (key == null) {
            return null;
        }
        BigDecimal currentLotSale = this.resolveCurrentLotSale(key, session);
        if (businessTime == null) {
            return currentLotSale;
        }
        if (!this.isAdjustedInSessionOrLookup(table, instanceId, session)) {
            // 无调价：禁止查历史，直接批号售价
            return currentLotSale;
        }
        BigDecimal adjusted = this.resolveSaleFromAdjustmentHistory(
            table, instanceId, lot, businessTime, session);
        if (adjusted != null) {
            return adjusted;
        }
        return currentLotSale;
    }

    /**
     * 按品规 instance 解析售价单价（每包装；仅当前 ACTIVE {@link PriceType#SALE}）。
     *
     * <p>诊疗/目录售价<strong>不做</strong>调价回放；业务统计以账单为准时应读 {@code ChargeItem}。
     * {@code businessTime}/{@code session} 保留入参兼容，忽略。</p>
     *
     * @param instanceTable 品规定义表名
     * @param instanceId    品规定义主键
     * @return 售价单价；无 SALE 时 {@code null}
     */
    public BigDecimal resolveHistoricalSaleUnitPrice(String instanceTable, Long instanceId) {
        return this.resolveHistoricalSaleUnitPrice(instanceTable, instanceId, null);
    }

    /**
     * 按品规 instance 解析售价单价（仅当前 ACTIVE SALE；不回放）。
     *
     * @param instanceTable 品规定义表名
     * @param instanceId    品规定义主键
     * @param businessTime  保留入参兼容（忽略）
     * @return 售价单价；无命中 {@code null}
     */
    public BigDecimal resolveHistoricalSaleUnitPrice(
        String instanceTable,
        Long instanceId,
        LocalDateTime businessTime) {
        return this.resolveHistoricalSaleUnitPrice(instanceTable, instanceId, businessTime, null);
    }

    /**
     * 按品规 instance 解析售价单价（仅当前 ACTIVE SALE；不回放）。
     *
     * @param instanceTable 品规定义表名
     * @param instanceId    品规定义主键
     * @param businessTime  保留入参兼容（忽略）
     * @param session       保留入参兼容（可复用当前 SALE 缓存）
     * @return 售价单价；无命中 {@code null}
     */
    public BigDecimal resolveHistoricalSaleUnitPrice(
        String instanceTable,
        Long instanceId,
        LocalDateTime businessTime,
        LotPriceResolveSession session) {
        if (instanceId == null || StringUtils.isEmpty(instanceTable)) {
            return null;
        }
        // Pre：诊疗/目录售价不回放，只读当前 ACTIVE SALE
        return this.resolveCurrentSale(instanceTable.trim(), instanceId, session);
    }

    /**
     * 预取批号进价与「有调价」品规集合，避免逐行 Query。
     *
     * @param chargeItems        收费明细
     * @param definitionById     锚点定价
     * @param lotByChargeItemId  chargeItemId → 批号
     * @return 解析上下文
     */
    private PurchaseResolveContext buildPurchaseResolveContext(
        Collection<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionById,
        Map<Long, String> lotByChargeItemId) {
        PurchaseResolveContext context = new PurchaseResolveContext();
        List<ChargeItemDefinitionLotBatchKey> lotKeys = new ArrayList<>();
        LinkedHashSet<Long> medItemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceItemIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            // Gate：诊疗/挂号无真实进价，后续直接用账单单价，不进调价预取
            if (this.isActivityLike(chargeItem)) {
                continue;
            }
            ChargeItemDefinition definition = definitionById.get(chargeItem.getDefinitionId());
            if (definition == null || definition.getInstanceId() == null) {
                continue;
            }
            String instanceTable = definition.getInstanceTable();
            if (StringUtils.isEmpty(instanceTable)) {
                instanceTable = this.inferInstanceTableFromServiceTable(chargeItem.getServiceTable());
            }
            if (StringUtils.isEmpty(instanceTable)) {
                continue;
            }
            String table = instanceTable.trim();
            Long instanceId = definition.getInstanceId();
            if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
                medItemIds.add(instanceId);
            } else if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
                deviceItemIds.add(instanceId);
            }
            String lot = lotByChargeItemId.get(chargeItem.getId());
            ChargeItemDefinitionLotBatchKey key =
                ChargeItemDefinitionLotBatchKey.maybeOf(table, instanceId, lot);
            if (key != null) {
                lotKeys.add(key);
            }
        }
        if (!lotKeys.isEmpty()) {
            context.lotCostByCompositeKey =
                this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(lotKeys);
            // Pre：同批号售价一并预取，供缺 LOT_NUMBER_COST 时回落（禁目录 PURCHASE）
            context.lotSaleByCompositeKey =
                this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(lotKeys);
        }
        context.adjustedMedItemIds = this.loadAdjustedItemIds(
            TableNameConstants.MED_MEDICATION_DEFINITION, medItemIds);
        context.adjustedDeviceItemIds = this.loadAdjustedItemIds(
            TableNameConstants.ADM_DEVICE_DEFINITION, deviceItemIds);
        // Pre：仅药耗有调价品规批量预取历史；诊疗不回放、不预取
        this.prefetchAdjustmentHistory(
            context, TableNameConstants.MED_MEDICATION_DEFINITION, context.adjustedMedItemIds);
        this.prefetchAdjustmentHistory(
            context, TableNameConstants.ADM_DEVICE_DEFINITION, context.adjustedDeviceItemIds);
        return context;
    }

    /**
     * 仅对有调价品规批量预取调价历史到上下文（一次 IN，禁止逐行 findApprovedHistoryByItem）。
     *
     * @param context   解析上下文
     * @param itemTable 项目表
     * @param itemIds   有调价的 itemId
     */
    private void prefetchAdjustmentHistory(
        PurchaseResolveContext context,
        String itemTable,
        LinkedHashSet<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        Map<Long, List<PriceAdjustmentLineQueryModel>> historyByItem =
            this.priceAdjustmentQueryService.findApprovedHistoryByItems(itemTable, itemIds);
        for (Map.Entry<Long, List<PriceAdjustmentLineQueryModel>> entry : historyByItem.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            context.historyByItemKey.put(this.buildItemHistoryKey(itemTable, entry.getKey()), entry.getValue());
        }
    }

    /**
     * 解析单行进价单价。
     *
     * @param chargeItem        收费明细
     * @param definitionById    锚点定价
     * @param lotByChargeItemId 批号
     * @param context           批量上下文
     * @return 单价或 null
     */
    private BigDecimal resolvePurchaseUnitForChargeItem(
        ChargeItem chargeItem,
        Map<Long, ChargeItemDefinition> definitionById,
        Map<Long, String> lotByChargeItemId,
        PurchaseResolveContext context) {
        if (chargeItem.getDefinitionId() == null) {
            return null;
        }
        // Gate：诊疗/挂号无真实进价——不编进价（禁账单价冒充；禁调价回放与目录价）
        if (this.isActivityLike(chargeItem)) {
            return null;
        }
        ChargeItemDefinition definition = definitionById.get(chargeItem.getDefinitionId());
        if (definition == null) {
            return null;
        }
        // 锚点本身已是进价/批号进价时直接用（收费时落库历史价）
        String priceTypeCode = definition.getPriceTypeCode();
        if (PriceType.PURCHASE.getCode().equals(priceTypeCode)
            || PriceType.LOT_NUMBER_COST.getCode().equals(priceTypeCode)) {
            return this.scalePriceValueOrNull(definition);
        }
        String instanceTable = definition.getInstanceTable();
        if (StringUtils.isEmpty(instanceTable)) {
            instanceTable = this.inferInstanceTableFromServiceTable(chargeItem.getServiceTable());
        }
        if (StringUtils.isEmpty(instanceTable) || definition.getInstanceId() == null) {
            return null;
        }
        String table = instanceTable.trim();
        Long instanceId = definition.getInstanceId();
        String lot = lotByChargeItemId.get(chargeItem.getId());
        LocalDateTime businessTime = chargeItem.getOccurrenceTime();
        if (StringUtils.isNotEmpty(lot)) {
            ChargeItemDefinitionLotBatchKey key =
                ChargeItemDefinitionLotBatchKey.maybeOf(table, instanceId, lot);
            BigDecimal currentLotCost = null;
            BigDecimal currentLotSale = null;
            if (key != null) {
                currentLotCost = this.firstLotCostPrice(context.lotCostByCompositeKey.get(key.compositeKey()));
                currentLotSale = this.firstLotCostPrice(context.lotSaleByCompositeKey.get(key.compositeKey()));
            }
            boolean adjusted = this.isItemAdjusted(table, instanceId, context);
            if (!adjusted) {
                // 无调价：直接批号进价；缺 COST 时同批号售价回落（西药/耗材同值）；禁止目录 PURCHASE
                if (currentLotCost != null) {
                    return currentLotCost;
                }
                return currentLotSale;
            }
            BigDecimal fromAdjust =
                this.resolvePurchaseFromAdjustmentHistory(table, instanceId, lot.trim(), businessTime, context);
            if (fromAdjust != null) {
                return fromAdjust;
            }
            if (currentLotCost != null) {
                return currentLotCost;
            }
            // 有调价但未命中调前价/COST：同批号售价回落，禁止目录 PURCHASE
            return currentLotSale;
        }
        // 无批号药耗：禁止目录药典 PURCHASE；无法锚定批号进价则跳过该行
        return null;
    }

    /**
     * 从已审批调价历史回放批号（或诊疗）进价（无会话缓存时单次查库）。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号；诊疗可空
     * @param businessTime 业务时刻
     * @return 调前进价；无需回放时 {@code null}
     */
    private BigDecimal resolvePurchaseFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime) {
        return this.resolvePurchaseFromAdjustmentHistory(itemTable, itemId, lotNumber, businessTime, null, null);
    }

    /**
     * 从上下文缓存回放进价（禁止再打 findApprovedHistoryByItem）。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号
     * @param businessTime 业务时刻
     * @param context      批量上下文
     * @return 调前进价或 null
     */
    private BigDecimal resolvePurchaseFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        PurchaseResolveContext context) {
        return this.resolvePurchaseFromAdjustmentHistory(
            itemTable, itemId, lotNumber, businessTime, context, null);
    }

    /**
     * 从会话缓存回放进价。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号
     * @param businessTime 业务时刻
     * @param session      批号会话
     * @return 调前进价或 null
     */
    private BigDecimal resolvePurchaseFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        LotPriceResolveSession session) {
        return this.resolvePurchaseFromAdjustmentHistory(
            itemTable, itemId, lotNumber, businessTime, null, session);
    }

    /**
     * 从已审批调价历史回放批号（或诊疗）进价。
     *
     * <p>取「审批时刻晚于业务时刻」的最早一笔调价，其 {@code old_purchase_amount} 即业务当时进价。
     * 优先读 context/session 预取缓存；皆无时才单次查库（禁止同请求内反复查）。</p>
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号；诊疗可空
     * @param businessTime 业务时刻
     * @param context      批量上下文；可空
     * @param session      批号会话；可空
     * @return 调前进价；无需回放时 {@code null}
     */
    private BigDecimal resolvePurchaseFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        PurchaseResolveContext context,
        LotPriceResolveSession session) {
        if (businessTime == null) {
            return null;
        }
        List<PriceAdjustmentLineQueryModel> history =
            this.loadAdjustmentHistory(itemTable, itemId, context, session);
        if (history == null || history.isEmpty()) {
            return null;
        }
        PriceAdjustmentLineQueryModel earliestAfter = this.pickEarliestApproveAfter(history, businessTime);
        if (earliestAfter == null) {
            // 业务时刻不早于全部调价 → 当前 ACTIVE 价即可
            return null;
        }
        return this.pickOldPurchaseFromDetails(earliestAfter.getDetails(), lotNumber);
    }

    /**
     * 从调价历史回放售价（诊疗/无批号侧；取首条调前售价）。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param businessTime 业务时刻
     * @return 调前售价或 null
     */
    private BigDecimal resolveSaleFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        LocalDateTime businessTime) {
        return this.resolveSaleFromAdjustmentHistory(itemTable, itemId, null, businessTime, null, null);
    }

    /**
     * 从调价历史回放售价；有批号时优先匹配该批号的调前售价。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号；可空（空则取首条调前售价）
     * @param businessTime 业务时刻
     * @return 调前售价或 null
     */
    private BigDecimal resolveSaleFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime) {
        return this.resolveSaleFromAdjustmentHistory(itemTable, itemId, lotNumber, businessTime, null, null);
    }

    /**
     * 从上下文缓存回放售价。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号
     * @param businessTime 业务时刻
     * @param context      批量上下文
     * @return 调前售价或 null
     */
    private BigDecimal resolveSaleFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        PurchaseResolveContext context) {
        return this.resolveSaleFromAdjustmentHistory(
            itemTable, itemId, lotNumber, businessTime, context, null);
    }

    /**
     * 从会话缓存回放售价。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号
     * @param businessTime 业务时刻
     * @param session      批号会话
     * @return 调前售价或 null
     */
    private BigDecimal resolveSaleFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        LotPriceResolveSession session) {
        return this.resolveSaleFromAdjustmentHistory(
            itemTable, itemId, lotNumber, businessTime, null, session);
    }

    /**
     * 从调价历史回放售价；有批号时优先匹配该批号的调前售价。
     *
     * @param itemTable    项目表
     * @param itemId       项目 id
     * @param lotNumber    批号；可空
     * @param businessTime 业务时刻
     * @param context      批量上下文；可空
     * @param session      批号会话；可空
     * @return 调前售价或 null
     */
    private BigDecimal resolveSaleFromAdjustmentHistory(
        String itemTable,
        Long itemId,
        String lotNumber,
        LocalDateTime businessTime,
        PurchaseResolveContext context,
        LotPriceResolveSession session) {
        if (businessTime == null) {
            return null;
        }
        List<PriceAdjustmentLineQueryModel> history =
            this.loadAdjustmentHistory(itemTable, itemId, context, session);
        if (history == null || history.isEmpty()) {
            return null;
        }
        PriceAdjustmentLineQueryModel earliestAfter = this.pickEarliestApproveAfter(history, businessTime);
        if (earliestAfter == null || earliestAfter.getDetails() == null || earliestAfter.getDetails().isEmpty()) {
            return null;
        }
        return this.pickOldSaleFromDetails(earliestAfter.getDetails(), lotNumber);
    }

    /**
     * 加载调价历史：优先 context/session 预取；皆无时单次查库并写入 session（若有）。
     *
     * @param itemTable 项目表
     * @param itemId    项目 id
     * @param context   批量上下文；可空
     * @param session   批号会话；可空
     * @return 调价历史；无则空列表
     */
    private List<PriceAdjustmentLineQueryModel> loadAdjustmentHistory(
        String itemTable,
        Long itemId,
        PurchaseResolveContext context,
        LotPriceResolveSession session) {
        String historyKey = this.buildItemHistoryKey(itemTable, itemId);
        if (context != null) {
            List<PriceAdjustmentLineQueryModel> cached = context.historyByItemKey.get(historyKey);
            if (cached != null) {
                return cached;
            }
        }
        if (session != null && session.itemTable != null && session.itemTable.equals(itemTable.trim())) {
            List<PriceAdjustmentLineQueryModel> sessionCached = session.historyByItemId.get(itemId);
            if (sessionCached != null) {
                return sessionCached;
            }
            // 会话已 warm 且不含该品规 → 无调价历史
            if (session.adjustedItemIds != null && !session.adjustedItemIds.contains(itemId)) {
                return List.of();
            }
        }
        // 无预取：单次查库（调用方应尽量避免此路径）
        List<PriceAdjustmentLineQueryModel> history =
            this.priceAdjustmentQueryService.findApprovedHistoryByItem(itemTable, itemId);
        if (session != null && session.itemTable != null && session.itemTable.equals(itemTable.trim())) {
            session.historyByItemId.put(itemId, history);
        }
        return history;
    }

    /**
     * 取审批时刻晚于业务时刻的最早一笔调价。
     *
     * @param history      调价历史
     * @param businessTime 业务时刻
     * @return 最早晚于业务的主行；无则 null
     */
    private PriceAdjustmentLineQueryModel pickEarliestApproveAfter(
        List<PriceAdjustmentLineQueryModel> history,
        LocalDateTime businessTime) {
        PriceAdjustmentLineQueryModel earliestAfter = null;
        for (PriceAdjustmentLineQueryModel line : history) {
            if (line == null || line.getApproveTime() == null) {
                continue;
            }
            LocalDateTime approveAt = this.toLocalDateTime(line.getApproveTime());
            if (approveAt == null) {
                continue;
            }
            // 审批晚于业务：该笔调价发生时，业务已完成 → 当时价=调前价
            if (approveAt.isAfter(businessTime)) {
                if (earliestAfter == null) {
                    earliestAfter = line;
                } else if (line.getApproveTime().before(earliestAfter.getApproveTime())) {
                    earliestAfter = line;
                }
            }
        }
        return earliestAfter;
    }

    /**
     * 调价历史 Map 键：表名 + 品规 id。
     *
     * @param itemTable 项目表
     * @param itemId    项目 id
     * @return 复合键
     */
    private String buildItemHistoryKey(String itemTable, Long itemId) {
        return itemTable.trim() + ":" + itemId;
    }

    /**
     * 从调价子表挑批号匹配的调前进价。
     *
     * <p>调用方已给出批号时<strong>必须</strong>与明细 {@code lot_number} 一致才回放；对不上返回
     * {@code null}（回落当前 ACTIVE 批号进价），<strong>禁止</strong>串用其它批号的调前价。
     * 批号为空时（目录级调价无批号维）才取首条非空调前进价。</p>
     *
     * @param details   调价明细
     * @param lotNumber 批号；可空
     * @return 调前进价或 null
     */
    private BigDecimal pickOldPurchaseFromDetails(
        List<PriceAdjustmentDetailQueryModel> details,
        String lotNumber) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        // Gate：有发药/库存批号时只认同批号明细，禁止串批回放
        if (StringUtils.isNotEmpty(lotNumber)) {
            String lot = lotNumber.trim();
            for (PriceAdjustmentDetailQueryModel detail : details) {
                if (detail == null || detail.getOldPurchaseAmount() == null) {
                    continue;
                }
                if (this.lotNumberEquals(lot, detail.getLotNumber())) {
                    return MonetaryAmountSupport.scaleCalc(detail.getOldPurchaseAmount());
                }
            }
            return null;
        }
        for (PriceAdjustmentDetailQueryModel detail : details) {
            if (detail != null && detail.getOldPurchaseAmount() != null) {
                return MonetaryAmountSupport.scaleCalc(detail.getOldPurchaseAmount());
            }
        }
        return null;
    }

    /**
     * 从调价子表挑批号匹配的调前售价（目录 SALE 历史回放等；批号价售价路径已不再回放）。
     *
     * <p>有批号时必须与明细批号一致；对不上返回 {@code null}，禁止串批。批号为空时取首条非空。</p>
     *
     * @param details   调价明细
     * @param lotNumber 批号；可空
     * @return 调前售价或 null
     */
    private BigDecimal pickOldSaleFromDetails(
        List<PriceAdjustmentDetailQueryModel> details,
        String lotNumber) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        // Gate：有批号只认同批号，禁止串用其它批号调前售价
        if (StringUtils.isNotEmpty(lotNumber)) {
            String lot = lotNumber.trim();
            for (PriceAdjustmentDetailQueryModel detail : details) {
                if (detail == null || detail.getOldSellingAmount() == null) {
                    continue;
                }
                if (this.lotNumberEquals(lot, detail.getLotNumber())) {
                    return MonetaryAmountSupport.scaleCalc(detail.getOldSellingAmount());
                }
            }
            return null;
        }
        for (PriceAdjustmentDetailQueryModel detail : details) {
            if (detail != null && detail.getOldSellingAmount() != null) {
                return MonetaryAmountSupport.scaleCalc(detail.getOldSellingAmount());
            }
        }
        return null;
    }

    /**
     * 批号字面值比较（两端 trim；明细批号空则不匹配）。
     *
     * @param expectedLot 调用方批号（已 trim）
     * @param detailLot   调价明细批号
     * @return 一致时 {@code true}
     */
    private boolean lotNumberEquals(String expectedLot, String detailLot) {
        if (StringUtils.isEmpty(expectedLot) || StringUtils.isEmpty(detailLot)) {
            return false;
        }
        return expectedLot.equals(detailLot.trim());
    }

    /**
     * 批量筛出存在已审批调价的 itemId 集合。
     *
     * @param itemTable 项目表
     * @param itemIds   候选 id
     * @return 有调价的 id 集
     */
    private LinkedHashSet<Long> loadAdjustedItemIds(String itemTable, LinkedHashSet<Long> itemIds) {
        return this.priceAdjustmentQueryService.listApprovedItemIds(itemTable, itemIds);
    }

    /**
     * 单品是否存在已审批调价（无批量上下文时的回落；批量路径禁止依赖本方法循环）。
     *
     * @param itemTable 项目表
     * @param itemId    项目 id
     * @return 有则为 true
     */
    private boolean hasApprovedAdjustment(String itemTable, Long itemId) {
        return this.priceAdjustmentQueryService.existsApprovedByItem(itemTable, itemId);
    }

    /**
     * 会话内判断是否有调价；无会话时回落单次 exists。
     *
     * @param itemTable  表名
     * @param instanceId 品规 id
     * @param session    批号会话；可空
     * @return 有调价为 true
     */
    private boolean isAdjustedInSessionOrLookup(
        String itemTable,
        Long instanceId,
        LotPriceResolveSession session) {
        if (session != null
            && session.itemTable != null
            && session.itemTable.equals(itemTable.trim())
            && session.adjustedItemIds != null) {
            return session.adjustedItemIds.contains(instanceId);
        }
        return this.hasApprovedAdjustment(itemTable, instanceId);
    }

    /**
     * 解析当前 ACTIVE 批号进价（会话内按复合键缓存，避免同批重复查）。
     *
     * @param key     批号键
     * @param session 会话；可空
     * @return 单价或 null
     */
    private BigDecimal resolveCurrentLotCost(
        ChargeItemDefinitionLotBatchKey key,
        LotPriceResolveSession session) {
        String composite = key.compositeKey();
        if (session != null && session.lotCostByCompositeKey.containsKey(composite)) {
            return session.lotCostByCompositeKey.get(composite);
        }
        Map<String, List<ChargeItemDefinition>> lotCostByKey =
            this.chargeItemDefinitionQueryService.findLotNumberCostLinesGroupedByLotBatchKey(List.of(key));
        BigDecimal price = this.firstLotCostPrice(lotCostByKey.get(composite));
        if (session != null) {
            session.lotCostByCompositeKey.put(composite, price);
        }
        return price;
    }

    /**
     * 解析当前 ACTIVE 批号售价（会话内缓存）。
     *
     * @param key     批号键
     * @param session 会话；可空
     * @return 单价或 null
     */
    private BigDecimal resolveCurrentLotSale(
        ChargeItemDefinitionLotBatchKey key,
        LotPriceResolveSession session) {
        String composite = key.compositeKey();
        if (session != null && session.lotSaleByCompositeKey.containsKey(composite)) {
            return session.lotSaleByCompositeKey.get(composite);
        }
        Map<String, List<ChargeItemDefinition>> lotSaleByKey =
            this.chargeItemDefinitionQueryService.findLotNumberPriceLinesGroupedByLotBatchKey(List.of(key));
        BigDecimal price = this.firstLotCostPrice(lotSaleByKey.get(composite));
        if (session != null) {
            session.lotSaleByCompositeKey.put(composite, price);
        }
        return price;
    }

    /**
     * 解析当前 ACTIVE SALE（会话内按品规缓存）。
     *
     * @param itemTable  表名
     * @param instanceId 品规 id
     * @param session    会话；可空
     * @return 单价或 null
     */
    private BigDecimal resolveCurrentSale(
        String itemTable,
        Long instanceId,
        LotPriceResolveSession session) {
        if (session != null
            && session.itemTable != null
            && session.itemTable.equals(itemTable.trim())
            && session.saleByItemId.containsKey(instanceId)) {
            return session.saleByItemId.get(instanceId);
        }
        BigDecimal sale =
            this.chargeItemDefinitionQueryService.resolveFirstSalePriceValue(itemTable, instanceId);
        if (session != null
            && session.itemTable != null
            && session.itemTable.equals(itemTable.trim())) {
            session.saleByItemId.put(instanceId, sale);
        }
        return sale;
    }

    /**
     * 上下文内判断品规是否有调价。
     *
     * @param table      表名
     * @param instanceId 品规 id
     * @param context    批量上下文
     * @return 有调价为 true
     */
    private boolean isItemAdjusted(String table, Long instanceId, PurchaseResolveContext context) {
        if (TableNameConstants.MED_MEDICATION_DEFINITION.equals(table)) {
            return context.adjustedMedItemIds.contains(instanceId);
        }
        if (TableNameConstants.ADM_DEVICE_DEFINITION.equals(table)) {
            return context.adjustedDeviceItemIds.contains(instanceId);
        }
        if (TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(table)
            || TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(table)) {
            return context.adjustedActivityItemIds.contains(instanceId);
        }
        return false;
    }

    /**
     * 从发放单与收费锚点定价行批量解析 chargeItem → 批号。
     *
     * <p>优先级：发放单批号 → 遗留 {@code service_table=*_dispense} 的 serviceId 发放行 →
     * 锚点 {@code LOT_NUMBER_PRICE}/{@code LOT_NUMBER_COST} 行的 {@code lot_no}。</p>
     *
     * @param chargeItems    收费明细
     * @param definitionById 锚点定价（供 lot_no 回落）；可空
     * @return chargeItemId → 批号
     */
    private Map<Long, String> loadLotNumberByChargeItemId(
        Collection<ChargeItem> chargeItems,
        Map<Long, ChargeItemDefinition> definitionById) {
        Map<Long, String> lotByChargeItemId = new HashMap<>();
        LinkedHashSet<Long> medDispenseIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceDispenseIds = new LinkedHashSet<>();
        Map<Long, Long> chargeToMedDispense = new HashMap<>();
        Map<Long, Long> chargeToDeviceDispense = new HashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            Long medDispenseId = this.resolveMedicationDispenseId(chargeItem);
            if (medDispenseId != null) {
                medDispenseIds.add(medDispenseId);
                chargeToMedDispense.put(chargeItem.getId(), medDispenseId);
                continue;
            }
            Long deviceDispenseId = this.resolveDeviceDispenseId(chargeItem);
            if (deviceDispenseId != null) {
                deviceDispenseIds.add(deviceDispenseId);
                chargeToDeviceDispense.put(chargeItem.getId(), deviceDispenseId);
            }
        }
        Map<Long, String> medLotByDispenseId = new HashMap<>();
        if (!medDispenseIds.isEmpty()) {
            List<MedicationDispense> medRows =
                this.medicationDispenseQueryService.listMedicationDispensesByIds(medDispenseIds);
            for (MedicationDispense row : medRows) {
                if (row != null && row.getId() != null && StringUtils.isNotEmpty(row.getLotNumber())) {
                    medLotByDispenseId.put(row.getId(), row.getLotNumber().trim());
                }
            }
        }
        Map<Long, String> deviceLotByDispenseId = new HashMap<>();
        if (!deviceDispenseIds.isEmpty()) {
            List<DeviceDispense> deviceRows =
                this.deviceDispenseQueryService.listDeviceDispensesByIds(deviceDispenseIds);
            for (DeviceDispense row : deviceRows) {
                if (row != null && row.getId() != null && StringUtils.isNotEmpty(row.getLotNumber())) {
                    deviceLotByDispenseId.put(row.getId(), row.getLotNumber().trim());
                }
            }
        }
        for (Map.Entry<Long, Long> entry : chargeToMedDispense.entrySet()) {
            String lot = medLotByDispenseId.get(entry.getValue());
            if (StringUtils.isNotEmpty(lot)) {
                lotByChargeItemId.put(entry.getKey(), lot);
            }
        }
        for (Map.Entry<Long, Long> entry : chargeToDeviceDispense.entrySet()) {
            String lot = deviceLotByDispenseId.get(entry.getValue());
            if (StringUtils.isNotEmpty(lot)) {
                lotByChargeItemId.put(entry.getKey(), lot);
            }
        }
        // 发放无批号：回落收费锚点 LOT_NUMBER_PRICE/COST 行上的 lot_no（挂费时已写入）
        if (definitionById != null && !definitionById.isEmpty()) {
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem == null || chargeItem.getId() == null) {
                    continue;
                }
                if (StringUtils.isNotEmpty(lotByChargeItemId.get(chargeItem.getId()))) {
                    continue;
                }
                if (chargeItem.getDefinitionId() == null) {
                    continue;
                }
                String lotFromDefinition =
                    this.lotNoFromLotPriceDefinition(definitionById.get(chargeItem.getDefinitionId()));
                if (StringUtils.isNotEmpty(lotFromDefinition)) {
                    lotByChargeItemId.put(chargeItem.getId(), lotFromDefinition);
                }
            }
        }
        return lotByChargeItemId;
    }

    /**
     * 解析药品发放主键：显式 dispense 列优先；遗留仅 service 锚定发放表时用 serviceId。
     *
     * @param chargeItem 收费明细
     * @return 发放主键；无法解析时 {@code null}
     */
    private Long resolveMedicationDispenseId(ChargeItem chargeItem) {
        String dispenseTable = chargeItem.getDispenseTable();
        String serviceTable = chargeItem.getServiceTable();
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(dispenseTable)
            && chargeItem.getDispenseId() != null) {
            return chargeItem.getDispenseId();
        }
        // 遗留：service_table=药品发放且 dispense_id 为空
        if (TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)
            && chargeItem.getServiceId() != null
            && chargeItem.getDispenseId() == null) {
            return chargeItem.getServiceId();
        }
        if (chargeItem.getDispenseId() == null) {
            return null;
        }
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable)) {
            // dispense_table 空或药品发放：按药品发放读批号
            if (StringUtils.isEmpty(dispenseTable)
                || TableNameConstants.MED_MEDICATION_DISPENSE.equals(dispenseTable)) {
                return chargeItem.getDispenseId();
            }
        }
        return null;
    }

    /**
     * 解析耗材发放主键：显式 dispense 列优先；遗留仅 service 锚定发放表时用 serviceId。
     *
     * @param chargeItem 收费明细
     * @return 发放主键；无法解析时 {@code null}
     */
    private Long resolveDeviceDispenseId(ChargeItem chargeItem) {
        String dispenseTable = chargeItem.getDispenseTable();
        String serviceTable = chargeItem.getServiceTable();
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(dispenseTable)
            && chargeItem.getDispenseId() != null) {
            return chargeItem.getDispenseId();
        }
        // 遗留：service_table=耗材发放且 dispense_id 为空
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)
            && chargeItem.getServiceId() != null
            && chargeItem.getDispenseId() == null) {
            return chargeItem.getServiceId();
        }
        if (chargeItem.getDispenseId() == null) {
            return null;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(serviceTable)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(serviceTable)) {
            // dispense_table 空或耗材发放：按耗材发放读批号
            if (StringUtils.isEmpty(dispenseTable)
                || TableNameConstants.WKF_DEVICE_DISPENSE.equals(dispenseTable)) {
                return chargeItem.getDispenseId();
            }
        }
        return null;
    }

    /**
     * 从批号价类定价行读取批号（非批号价类返回 null）。
     *
     * @param definition 收费锚点定价
     * @return 批号；无则 {@code null}
     */
    private String lotNoFromLotPriceDefinition(ChargeItemDefinition definition) {
        if (definition == null || StringUtils.isEmpty(definition.getLotNo())) {
            return null;
        }
        String priceTypeCode = definition.getPriceTypeCode();
        if (PriceType.LOT_NUMBER_PRICE.getCode().equals(priceTypeCode)
            || PriceType.LOT_NUMBER_COST.getCode().equals(priceTypeCode)) {
            return definition.getLotNo().trim();
        }
        return null;
    }

    /**
     * 批号进价行列表取首个非空单价。
     *
     * @param lines 批号进价行
     * @return 单价或 null
     */
    private BigDecimal firstLotCostPrice(List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition line : lines) {
            BigDecimal price = this.scalePriceValueOrNull(line);
            if (price != null) {
                return price;
            }
        }
        return null;
    }

    /**
     * 定价行 price_value 刻度；空值返回 null。
     *
     * @param definition 定价行
     * @return 6 位单价或 null
     */
    private BigDecimal scalePriceValueOrNull(ChargeItemDefinition definition) {
        if (definition == null || definition.getPriceValue() == null) {
            return null;
        }
        return MonetaryAmountSupport.scaleCalc(definition.getPriceValue());
    }

    /**
     * Date → LocalDateTime（系统默认时区）。
     *
     * @param date 审批时刻
     * @return LocalDateTime 或 null
     */
    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    /**
     * 由 service_table 推断品规定义表。
     *
     * @param serviceTable ChargeItem.service_table
     * @return 定义表名或 null
     */
    private String inferInstanceTableFromServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return null;
        }
        String normalized = serviceTable.trim();
        if (TableNameConstants.MED_MEDICATION_REQUEST.equals(normalized)
            || TableNameConstants.MED_MEDICATION_DISPENSE.equals(normalized)) {
            return TableNameConstants.MED_MEDICATION_DEFINITION;
        }
        if (TableNameConstants.WKF_DEVICE_REQUEST.equals(normalized)
            || TableNameConstants.WKF_DEVICE_DISPENSE.equals(normalized)) {
            return TableNameConstants.ADM_DEVICE_DEFINITION;
        }
        if (TableNameConstants.WKF_SERVICE_REQUEST.equals(normalized)) {
            return TableNameConstants.WKF_ACTIVITY_DEFINITION;
        }
        if (TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(normalized)) {
            return TableNameConstants.ADM_HEALTHCARE_SERVICE;
        }
        return null;
    }

    /**
     * 是否诊疗/挂号类收费。
     *
     * @param chargeItem 收费明细
     * @return 是则为 true
     */
    private boolean isActivityLike(ChargeItem chargeItem) {
        if (this.isRegistrationChargeItem(chargeItem)) {
            return true;
        }
        return this.isActivityServiceTable(chargeItem.getServiceTable());
    }

    /**
     * 服务表是否为诊疗类。
     *
     * @param serviceTable service_table
     * @return 诊疗为 true
     */
    private boolean isActivityServiceTable(String serviceTable) {
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        return TableNameConstants.WKF_SERVICE_REQUEST.equals(serviceTable.trim());
    }

    /**
     * 识别挂号 chargeItem。
     *
     * @param chargeItem 收费明细
     * @return 挂号为 true
     */
    private boolean isRegistrationChargeItem(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return false;
        }
        if (GenerateSource.REGISTRATION_REGISTER.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return true;
        }
        String serviceTable = chargeItem.getServiceTable();
        if (StringUtils.isEmpty(serviceTable)) {
            return false;
        }
        return TableNameConstants.ADM_HEALTHCARE_SERVICE.equals(serviceTable.trim());
    }

    /**
     * 批号进价与调价集合预取上下文。
     */
    private static final class PurchaseResolveContext {

        /** 批号复合键 → ACTIVE 批号进价行。 */
        private Map<String, List<ChargeItemDefinition>> lotCostByCompositeKey = Map.of();

        /** 批号复合键 → ACTIVE 批号售价行（缺 COST 时回落）。 */
        private Map<String, List<ChargeItemDefinition>> lotSaleByCompositeKey = Map.of();

        /** 有已审批调价的药品品规 id。 */
        private LinkedHashSet<Long> adjustedMedItemIds = new LinkedHashSet<>();

        /** 有已审批调价的耗材品规 id。 */
        private LinkedHashSet<Long> adjustedDeviceItemIds = new LinkedHashSet<>();

        /** 有已审批调价的诊疗/活动 id。 */
        private LinkedHashSet<Long> adjustedActivityItemIds = new LinkedHashSet<>();

        /** itemTable:itemId → 已预取调价历史（仅有调价品规）。 */
        private final Map<String, List<PriceAdjustmentLineQueryModel>> historyByItemKey = new HashMap<>();
    }

    /**
     * 批号进/售价批量解析会话（药房日结等）：先 warm 有调价集合与历史，再逐行解析。
     *
     * <p>无调价品规只读 ACTIVE 批号价，不触达调价 history。</p>
     */
    public static final class LotPriceResolveSession {

        /** 品规定义表。 */
        private String itemTable;

        /** 有已审批调价的品规 id。 */
        private LinkedHashSet<Long> adjustedItemIds = new LinkedHashSet<>();

        /** 品规 id → 调价历史（仅 adjusted）。 */
        private final Map<Long, List<PriceAdjustmentLineQueryModel>> historyByItemId = new HashMap<>();

        /** 批号复合键 → 当前批号进价（可含 null 占位防穿透）。 */
        private final Map<String, BigDecimal> lotCostByCompositeKey = new HashMap<>();

        /** 批号复合键 → 当前批号售价。 */
        private final Map<String, BigDecimal> lotSaleByCompositeKey = new HashMap<>();

        /** 品规 id → 当前 SALE。 */
        private final Map<Long, BigDecimal> saleByItemId = new HashMap<>();
    }
}
