/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitemdefinition.service;

import com.openhis.health.domain.administration.chargeitemdefinition.entity.ChargeItemDefinition;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionQueryCommand;
import com.openhis.health.domain.administration.chargeitemdefinition.repository.ChargeItemDefinitionRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PriceType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 收费项定义读侧查询服务。
 *
 * <p>职责：按实例（药品/项目等）与 {@link PriceType} 持久化码解析<strong>当前有效</strong>定价行；
 * 销售侧零售价统一为 {@link PriceType#SALE}（不再使用 {@code UNIT}）。</p>
 *
 * <p>边界：只读；{@code instanceTable} 有值时走表+实例联合键，否则仅按实例标识查询。
 * 「当前价」读路径（**BR-PRICE-SRC-001** / **BR-CHGDEF-QRY-002**）仅返回 {@code status_enum=ACTIVE} 且未软删行；
 * {@link #findAllDefinitionsByIdIn} 按主键快照加载，供 {@code ChargeItem.definition_id} 历史锚点，不过滤 ACTIVE。</p>
 * <p>聚合边界：仅本聚合 {@link ChargeItemDefinitionRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeItemDefinitionQueryService {
    private final ChargeItemDefinitionRepository repository;

    /**
     * 查询销售侧定价行：仅 {@link PriceType#SALE}（零售价持久化码）。
     *
     * @param cmd 查询命令（{@code instanceId} 必填；{@code instanceTable} 可选）
     * @return 命中的定价行列表，可能为空
     */
    public List<ChargeItemDefinition> findSaleItemByInstanceId(ChargeItemDefinitionQueryCommand cmd) {
        String saleCode = PriceType.SALE.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCode(
                cmd.getInstanceTable(), cmd.getInstanceId(), saleCode));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCode(cmd.getInstanceId(), saleCode));
    }

    /**
     * 查询采购侧定价行（{@link PriceType#PURCHASE}）。
     *
     * @param cmd 查询命令
     * @return 采购定价行列表
     */
    public List<ChargeItemDefinition> findPurchaseItemByInstanceId(ChargeItemDefinitionQueryCommand cmd) {
        String purchaseCode = PriceType.PURCHASE.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCode(
                cmd.getInstanceTable(), cmd.getInstanceId(), purchaseCode));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCode(cmd.getInstanceId(), purchaseCode));
    }

    /**
     * 按批号查询销售定价：仅 {@link PriceType#SALE}。
     *
     * @param cmd 查询命令（含 {@code lotNo}）
     * @return 命中的定价行列表
     */
    public List<ChargeItemDefinition> findSalePriceByInstanceIdAndLot(ChargeItemDefinitionQueryCommand cmd) {
        String saleCode = PriceType.SALE.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCodeAndLotNo(
                cmd.getInstanceTable(), cmd.getInstanceId(), saleCode, cmd.getLotNo()));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCodeAndLotNo(cmd.getInstanceId(), saleCode, cmd.getLotNo()));
    }

    /**
     * 按批号查询采购定价（{@link PriceType#PURCHASE}）。
     *
     * @param cmd 查询命令（含 {@code lotNo}）
     * @return 采购定价行列表
     */
    public List<ChargeItemDefinition> findPurchasePriceByInstanceIdAndLot(ChargeItemDefinitionQueryCommand cmd) {
        String purchaseCode = PriceType.PURCHASE.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCodeAndLotNo(
                cmd.getInstanceTable(), cmd.getInstanceId(), purchaseCode, cmd.getLotNo()));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCodeAndLotNo(cmd.getInstanceId(), purchaseCode, cmd.getLotNo()));
    }

    /**
     * 按批号查询批号维进价定价行（{@link PriceType#LOT_NUMBER_COST}）。
     *
     * <p>用于药库出库、药房入库选品等「价随批号」展示；入参 {@code lotNo} 须非空白，否则调用方应直接跳过查询。业务上同一物品实例+批号应至多一条有效进价行；若数据层返回多行，调用侧宜按定价行主键升序稳定择首个非空 {@code price_value}。</p>
     *
     * @param cmd 查询命令（{@code instanceId}、非空白 {@code lotNo} 必填；{@code instanceTable} 有值时与表名联合键查询）
     * @return 命中的定价行列表，可能为空
     */
    public List<ChargeItemDefinition> findLotNumberCostByInstanceIdAndLot(ChargeItemDefinitionQueryCommand cmd) {
        String costCode = PriceType.LOT_NUMBER_COST.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCodeAndLotNo(
                cmd.getInstanceTable(), cmd.getInstanceId(), costCode, cmd.getLotNo()));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCodeAndLotNo(cmd.getInstanceId(), costCode, cmd.getLotNo()));
    }

    /**
     * 按批号查询批号维销售定价行（{@link PriceType#LOT_NUMBER_PRICE}）。
     *
     * <p>与 {@link #findLotNumberCostByInstanceIdAndLot(ChargeItemDefinitionQueryCommand)} 同构，仅价类码不同（AS-INV-WHS-004 INV-DTL-SDD-R-001）。</p>
     *
     * @param cmd 查询命令（{@code instanceId}、非空白 {@code lotNo} 必填；{@code instanceTable} 有值时与表名联合键查询）
     * @return 命中的定价行列表，可能为空
     */
    public List<ChargeItemDefinition> findLotNumberPriceByInstanceIdAndLot(ChargeItemDefinitionQueryCommand cmd) {
        String priceCode = PriceType.LOT_NUMBER_PRICE.getCode();
        if (cmd.getInstanceTable() != null) {
            return filterCurrentActivePriceLines(repository.findAllByInstanceTableAndInstanceIdAndPriceTypeCodeAndLotNo(
                cmd.getInstanceTable(), cmd.getInstanceId(), priceCode, cmd.getLotNo()));
        }
        return filterCurrentActivePriceLines(
            repository.findAllByInstanceIdAndPriceTypeCodeAndLotNo(cmd.getInstanceId(), priceCode, cmd.getLotNo()));
    }

    /**
     * 批量加载批号进价定价行并按 {@link ChargeItemDefinitionLotBatchKey#compositeKey()} 分组（一次或多次规格查询下推，禁止调用方在循环内逐条仓储访问）。
     *
     * @param keys 键集合，允许含 null 或重复（内部去重）
     * @return 复合键 → 命中行列表；无键时返回空映射
     */
    public Map<String, List<ChargeItemDefinition>> findLotNumberCostLinesGroupedByLotBatchKey(
        Collection<ChargeItemDefinitionLotBatchKey> keys
    ) {
        return this.findLotNumberLinesGroupedByLotBatchKey(keys, PriceType.LOT_NUMBER_COST.getCode());
    }

    /**
     * 解析定价表 {@link com.openhis.health.domain.share.enums.PriceType#PURCHASE} 当前有效择优单价（每<strong>包装单位</strong>）。
     *
     * <p>库房单据读路径两档锚价之一（无批号时）；与 {@link #resolveFirstLotNumberCostPriceValue} 成对使用，<strong>禁止</strong>在库房选品/详情槽价路径取 {@code SALE}。</p>
     *
     * @param instanceTable 物品物理表名
     * @param instanceId    物品定义主键
     * @return 每包装单位进价；参数不全或无命中时为 {@code null}
     */
    public BigDecimal resolveFirstPurchasePriceValue(String instanceTable, Long instanceId) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(instanceTable.trim());
        priceCmd.setInstanceId(instanceId);
        return selectFirstNonNullPriceValueStable(this.findPurchaseItemByInstanceId(priceCmd));
    }

    /**
     * 解析定价表 {@link com.openhis.health.domain.share.enums.PriceType#SALE} 当前有效择优单价（每<strong>包装单位</strong>）。
     *
     * @param instanceTable 物品物理表名
     * @param instanceId    物品定义主键
     * @return 每包装单位售价；参数不全或无命中时为 {@code null}
     */
    public BigDecimal resolveFirstSalePriceValue(String instanceTable, Long instanceId) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(instanceTable.trim());
        priceCmd.setInstanceId(instanceId);
        return selectFirstNonNullPriceValueStable(this.findSaleItemByInstanceId(priceCmd));
    }

    /**
     * 批量解析同表多实例的 {@link PriceType#SALE} 当前有效择优单价（语义同 {@link #resolveFirstSalePriceValue}）。
     *
     * <p>用于套餐子项展开等避免按子项 N+1；择优规则仍走 {@link #selectFirstNonNullPriceValueStable}。</p>
     *
     * @param instanceTable 物品物理表名（如诊疗定义表）
     * @param instanceIds   实例主键集合
     * @return instanceId → 单价；无命中的 id 不出现在 Map 中（never null）
     */
    public Map<Long, BigDecimal> mapFirstSalePriceValueByInstanceIds(
        String instanceTable,
        Collection<Long> instanceIds
    ) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (StringUtils.isEmpty(instanceTable) || instanceIds == null || instanceIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : instanceIds) {
            if (id != null) {
                uniqueIds.add(id);
            }
        }
        // 无有效主键：直接空结果
        if (uniqueIds.isEmpty()) {
            return result;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(instanceTable.trim());
        priceCmd.setInstanceIds(uniqueIds);
        priceCmd.setPriceTypeCodes(List.of(PriceType.SALE.getCode()));
        List<ChargeItemDefinition> lines = this.findPriceLinesByProjects(priceCmd);
        Map<Long, List<ChargeItemDefinition>> byInstanceId = new HashMap<>();
        if (lines != null) {
            for (ChargeItemDefinition line : lines) {
                if (line == null || line.getInstanceId() == null) {
                    continue;
                }
                byInstanceId.computeIfAbsent(line.getInstanceId(), k -> new ArrayList<>()).add(line);
            }
        }
        // 逐实例择优，与单条 resolveFirstSalePriceValue 同口径
        for (Long instanceId : uniqueIds) {
            BigDecimal price = selectFirstNonNullPriceValueStable(byInstanceId.get(instanceId));
            if (price != null) {
                result.put(instanceId, price);
            }
        }
        return result;
    }

    /**
     * 解析实例+批号维 {@link PriceType#LOT_NUMBER_COST} 当前有效择优单价（价随批号展示，不做实例维回落）。
     *
     * <p>库房单据读路径两档锚价之二（有批号时）；{@link PriceType#LOT_NUMBER_PRICE} 不参与库房读路径。</p>
     *
     * @param instanceTable 物品物理表名
     * @param instanceId    物品定义主键
     * @param lotNumber     批号
     * @return 首个非空批号进价；参数不全、批号空白或无命中时为 {@code null}
     */
    public BigDecimal resolveFirstLotNumberCostPriceValue(String instanceTable, Long instanceId, String lotNumber) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null || StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(instanceTable.trim());
        priceCmd.setInstanceId(instanceId);
        priceCmd.setLotNo(lotNumber.trim());
        return selectFirstNonNullPriceValueStable(this.findLotNumberCostByInstanceIdAndLot(priceCmd));
    }

    /**
     * 解析实例+批号维 {@link PriceType#LOT_NUMBER_PRICE} 当前有效择优单价（每<strong>包装单位</strong>）。
     *
     * <p>用于进销存 3502 等需报「批号售价」的场景；<strong>不</strong>替代库房选品/详情槽价读路径
     * （库房单据读路径有批号时用 {@link #resolveFirstLotNumberCostPriceValue}）。</p>
     *
     * @param instanceTable 物品物理表名
     * @param instanceId    物品定义主键
     * @param lotNumber     批号
     * @return 首个非空批号售价；参数不全、批号空白或无命中时为 {@code null}
     */
    public BigDecimal resolveFirstLotNumberPriceValue(String instanceTable, Long instanceId, String lotNumber) {
        if (StringUtils.isEmpty(instanceTable) || instanceId == null || StringUtils.isEmpty(lotNumber)) {
            return null;
        }
        ChargeItemDefinitionQueryCommand priceCmd = new ChargeItemDefinitionQueryCommand();
        priceCmd.setInstanceTable(instanceTable.trim());
        priceCmd.setInstanceId(instanceId);
        priceCmd.setLotNo(lotNumber.trim());
        return selectFirstNonNullPriceValueStable(this.findLotNumberPriceByInstanceIdAndLot(priceCmd));
    }

    /**
     * 批量加载批号售价定价行并按复合键分组（价类为 {@link PriceType#LOT_NUMBER_PRICE}，其余同 {@link #findLotNumberCostLinesGroupedByLotBatchKey}）。
     *
     * @param keys 键集合
     * @return 复合键 → 命中行列表
     */
    public Map<String, List<ChargeItemDefinition>> findLotNumberPriceLinesGroupedByLotBatchKey(
        Collection<ChargeItemDefinitionLotBatchKey> keys
    ) {
        return this.findLotNumberLinesGroupedByLotBatchKey(keys, PriceType.LOT_NUMBER_PRICE.getCode());
    }

    /**
     * 判断实例+批号维度是否已存在任一条<strong>当前有效</strong>「批号进价」或「批号售价」定价行（用于进货同意路径批号维定价去重，WHS-IN-SDD-R-006）。
     *
     * @param cmd 查询命令；{@code instanceTable}、{@code instanceId}、非空白 {@code lotNo} 须已由调用方保证
     * @return 已存在任一条 ACTIVE {@link PriceType#LOT_NUMBER_COST} 或 {@link PriceType#LOT_NUMBER_PRICE} 且批号一致时为 true
     */
    public boolean existsLotNumberCostOrLotNumberPriceForInstanceAndLot(ChargeItemDefinitionQueryCommand cmd) {
        if (cmd == null || cmd.getInstanceId() == null || StringUtils.isEmpty(cmd.getLotNo())) {
            return false;
        }
        if (!this.findLotNumberCostByInstanceIdAndLot(cmd).isEmpty()) {
            return true;
        }
        return !this.findLotNumberPriceByInstanceIdAndLot(cmd).isEmpty();
    }

    /**
     * 批量按实例主键集合加载定价行（用于组套/多项目一次取价）。
     *
     * <p>若 {@link ChargeItemDefinitionQueryCommand#getPriceTypeCodes()} 非空，则仓储侧按价类 IN 收窄，
     * 避免把进价/限价等无关行拉进目录选品内存。</p>
     *
     * @param cmd 查询命令（{@code instanceIds} 必填）
     * @return 定价行列表（已滤当前 ACTIVE）
     */
    public List<ChargeItemDefinition> findPriceLinesByProjects(ChargeItemDefinitionQueryCommand cmd) {
        // 批量取价：有表维度时用 IN 查询限定在同表多实例，防止跨业务表 id 碰撞。
        if (cmd.getInstanceTable() != null) {
            Collection<String> priceTypeCodes = cmd.getPriceTypeCodes();
            // 目录等场景显式价类收窄：少扫批号进价/限价等无关行
            if (priceTypeCodes != null && !priceTypeCodes.isEmpty()) {
                return filterCurrentActivePriceLines(
                    repository.findAllByInstanceTableAndInstanceIdInAndPriceTypeCodeIn(
                        cmd.getInstanceTable(), cmd.getInstanceIds(), priceTypeCodes));
            }
            return filterCurrentActivePriceLines(
                repository.findAllByInstanceTableAndInstanceIdIn(cmd.getInstanceTable(), cmd.getInstanceIds()));
        }
        return filterCurrentActivePriceLines(repository.findAllByInstanceIdIn(cmd.getInstanceIds()));
    }

    /**
     * 挂号场景取单条销售定价行：在 {@link #findSaleItemByInstanceId(ChargeItemDefinitionQueryCommand)} 结果上按 priority/id 择优。
     *
     * @param cmd 查询命令
     * @return 择优定价行；无命中时 {@code null}
     */
    public ChargeItemDefinition findSaleLineForHealthcareServiceRegistration(ChargeItemDefinitionQueryCommand cmd) {
        return selectBestCurrentPriceLine(this.findSaleItemByInstanceId(cmd));
    }

    /**
     * 按收费项定义主键批量加载，供收费明细列表、预结算预览等一次性解析 {@code charge_name}。
     *
     * <p>按主键快照加载，供 {@code ChargeItem.definition_id} 历史锚点；<strong>不过滤</strong> {@code status_enum}（**BR-PRICE-SRC-001** 已发生业务读账单）。</p>
     *
     * @param definitionIds 定价定义主键集合，允许含 {@code null}（忽略）
     * @return 以定义主键为键的映射；入参为空时返回空映射
     */
    public Map<Long, ChargeItemDefinition> findAllDefinitionsByIdIn(Collection<Long> definitionIds) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long definitionId : definitionIds) {
            if (definitionId != null) {
                unique.add(definitionId);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChargeItemDefinition> list = repository.findAllById(unique);
        Map<Long, ChargeItemDefinition> map = new HashMap<>(list.size() * 2);
        for (ChargeItemDefinition row : list) {
            map.put(row.getId(), row);
        }
        return map;
    }

    /**
     * 按实例加载全部历史定价行（含 INACTIVE；遗留 API）。
     *
     * <p><strong>禁止</strong>在报表/费用明细/日结进价路径调用本方法做「无脑全量兄弟扫描」。
     * 进价/成本回放统一走 {@code HistoricalChargeItemPurchasePriceSupport}
     *（无调价直读批号价；有调价才查调价历史）。</p>
     *
     * @param instanceTable 实例表名，可空
     * @param instanceId    实例主键
     * @return 未软删定价行；无命中时返回空列表
     */
    public List<ChargeItemDefinition> listHistoricalDefinitionsByInstance(String instanceTable, Long instanceId) {
        if (instanceId == null) {
            return Collections.emptyList();
        }
        List<ChargeItemDefinition> rows;
        if (StringUtils.isNotEmpty(instanceTable)) {
            rows = repository.findAllByInstanceTableAndInstanceId(instanceTable.trim(), instanceId);
        } else {
            rows = repository.findAllByInstanceId(instanceId);
        }
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChargeItemDefinition> result = new ArrayList<>(rows.size());
        for (ChargeItemDefinition row : rows) {
            if (row != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 内部：按价类码与一批实例+批号键装载批号维定价行，分段 OR 查询避免单条 SQL 过长。
     *
     * @param keys            键集合
     * @param priceTypeCode   {@link PriceType#getCode()} 持久化串
     * @return 复合键 → 行列表
     */
    private Map<String, List<ChargeItemDefinition>> findLotNumberLinesGroupedByLotBatchKey(
        Collection<ChargeItemDefinitionLotBatchKey> keys,
        String priceTypeCode
    ) {
        if (keys == null || keys.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> unique = new LinkedHashSet<>();
        for (ChargeItemDefinitionLotBatchKey key : keys) {
            if (key != null) {
                unique.add(key);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ChargeItemDefinitionLotBatchKey> asList = new ArrayList<>(unique);
        List<ChargeItemDefinition> loaded = new ArrayList<>();
        int chunkSize = 200;
        int fromIndex = 0;
        while (fromIndex < asList.size()) {
            int toIndex = Math.min(fromIndex + chunkSize, asList.size());
            List<ChargeItemDefinitionLotBatchKey> chunk = asList.subList(fromIndex, toIndex);
            Specification<ChargeItemDefinition> spec = this.buildLotBatchOrSpecification(chunk, priceTypeCode);
            loaded.addAll(this.repository.findAll(spec));
            fromIndex = toIndex;
        }
        return this.groupLotLinesByCompositeKey(loaded);
    }

    /**
     * 构造「软删 + ACTIVE + 价类 + 多组 (表,实例,批号) OR」规格。
     *
     * @param chunk           单段键列表，调用方保证非空
     * @param priceTypeCode   价类持久化码
     * @return 规格
     */
    private Specification<ChargeItemDefinition> buildLotBatchOrSpecification(
        List<ChargeItemDefinitionLotBatchKey> chunk,
        String priceTypeCode
    ) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> andPredicates = new ArrayList<>();
            andPredicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            andPredicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            andPredicates.add(cb.equal(root.get(FieldNameConstants.PRICE_TYPE_CODE), priceTypeCode));
            List<jakarta.persistence.criteria.Predicate> orPredicates = new ArrayList<>();
            for (ChargeItemDefinitionLotBatchKey key : chunk) {
                jakarta.persistence.criteria.Predicate rowMatch = cb.and(
                    cb.equal(root.get(FieldNameConstants.INSTANCE_TABLE), key.getInstanceTable()),
                    cb.equal(root.get(FieldNameConstants.INSTANCE_ID), key.getInstanceId()),
                    cb.equal(root.get(FieldNameConstants.LOT_NO), key.getLotNo())
                );
                orPredicates.add(rowMatch);
            }
            andPredicates.add(cb.or(orPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
            return cb.and(andPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 保留未软删且 {@code status_enum=ACTIVE} 的定价行（当前价读路径，**BR-CHGDEF-QRY-002**）。
     *
     * @param lines 仓储原始结果
     * @return 过滤后列表；无命中时返回空列表
     */
    private static List<ChargeItemDefinition> filterCurrentActivePriceLines(List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        List<ChargeItemDefinition> filtered = new ArrayList<>(lines.size());
        String activeCode = PublicationStatus.ACTIVE.getCode();
        for (ChargeItemDefinition line : lines) {
            if (line == null || Boolean.TRUE.equals(line.getIsDeleted())) {
                continue;
            }
            if (activeCode.equals(line.getStatusEnum())) {
                filtered.add(line);
            }
        }
        return filtered;
    }

    /**
     * 在 ACTIVE 定价行中按 priority 升序、同 priority 时 id 升序择优（与 {@code ClinicalCatalogPriceResolver} 一致）。
     *
     * @param lines 已过滤或待过滤行
     * @return 择优行；无命中时 {@code null}
     */
    private static ChargeItemDefinition selectBestCurrentPriceLine(List<ChargeItemDefinition> lines) {
        if (lines == null || lines.isEmpty()) {
            return null;
        }
        ChargeItemDefinition best = null;
        for (ChargeItemDefinition line : lines) {
            if (line == null) {
                continue;
            }
            best = betterCurrentPriceLine(best, line);
        }
        return best;
    }

    /**
     * 当前价行择优：priority 小者优先；同 priority 按 id 稳定择一。
     *
     * @param current   当前最优
     * @param candidate 候选价行
     * @return 更优价行
     */
    private static ChargeItemDefinition betterCurrentPriceLine(ChargeItemDefinition current, ChargeItemDefinition candidate) {
        if (candidate == null) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        Integer cp = candidate.getPriority();
        Integer curp = current.getPriority();
        if (cp != null && curp != null) {
            int cmp = Integer.compare(cp, curp);
            if (cmp < 0) {
                return candidate;
            }
            if (cmp > 0) {
                return current;
            }
        } else if (cp != null) {
            return candidate;
        } else if (curp != null) {
            return current;
        }
        Long cid = candidate.getId();
        Long curid = current.getId();
        if (cid != null && curid != null && cid < curid) {
            return candidate;
        }
        return current;
    }

    /**
     * 读取同实例任意定价行上的 {@code type_code}（含 INACTIVE；供批号价/调价写路径继承 fin_type）。
     *
     * @param cmd 查询命令（{@code instanceId} 必填；{@code instanceTable} 建议传入）
     * @return 首个非空 type_code；无命中时为 {@code null}
     */
    public String findTypeCodeFromInstancePricing(ChargeItemDefinitionQueryCommand cmd) {
        if (cmd == null || cmd.getInstanceId() == null) {
            return null;
        }
        List<ChargeItemDefinition> rows;
        if (StringUtils.isNotEmpty(cmd.getInstanceTable())) {
            rows = repository.findAllByInstanceTableAndInstanceId(
                cmd.getInstanceTable().trim(), cmd.getInstanceId());
        } else {
            rows = repository.findAllByInstanceId(cmd.getInstanceId());
        }
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (ChargeItemDefinition row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (StringUtils.isNotEmpty(row.getTypeCode())) {
                return row.getTypeCode().trim();
            }
        }
        return null;
    }

    /**
     * 在 ACTIVE 行中择优并返回非空 {@code price_value}；无命中时为 {@code null}。
     *
     * @param lines 定价行（通常已由 {@link #filterCurrentActivePriceLines} 过滤）
     * @return 择优单价
     */
    private static BigDecimal selectFirstNonNullPriceValueStable(List<ChargeItemDefinition> lines) {
        ChargeItemDefinition best = selectBestCurrentPriceLine(lines);
        if (best == null) {
            return null;
        }
        return best.getPriceValue();
    }

    /**
     * 将定价行按 {@link ChargeItemDefinitionLotBatchKey#compositeKey()} 分组，供应用层 O(1) 命中。
     *
     * @param lines 仓储返回行
     * @return 分组映射
     */
    private Map<String, List<ChargeItemDefinition>> groupLotLinesByCompositeKey(List<ChargeItemDefinition> lines) {
        Map<String, List<ChargeItemDefinition>> map = new HashMap<>();
        if (lines == null || lines.isEmpty()) {
            return map;
        }
        for (ChargeItemDefinition line : lines) {
            if (line == null) {
                continue;
            }
            if (StringUtils.isEmpty(line.getInstanceTable()) || line.getInstanceId() == null || StringUtils.isEmpty(line.getLotNo())) {
                continue;
            }
            ChargeItemDefinitionLotBatchKey key = new ChargeItemDefinitionLotBatchKey(
                line.getInstanceTable().trim(),
                line.getInstanceId(),
                line.getLotNo().trim()
            );
            String composite = key.compositeKey();
            List<ChargeItemDefinition> bucket = map.computeIfAbsent(composite, k -> new ArrayList<>());
            bucket.add(line);
        }
        return map;
    }
}
