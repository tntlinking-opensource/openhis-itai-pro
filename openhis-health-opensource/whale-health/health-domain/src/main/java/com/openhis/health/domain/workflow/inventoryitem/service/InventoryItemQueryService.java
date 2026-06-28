/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.workflow.inventoryitem.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryLock;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.InventoryItemQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.AvailableInventoryModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryAvailSumModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.openhis.health.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.openhis.health.domain.workflow.inventoryitem.repository.InventoryItemRepository;
import com.openhis.health.domain.workflow.inventoryitem.repository.InventoryLockRepository;
import com.openhis.health.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.openhis.health.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.openhis.whale.core.application.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 库存项读侧查询服务（DM-WKF-008）。
 *
 * <p>
 * 职责：可用库存筛选（含效期与锁定）、多维检索、乐观锁版本比对及分页维护；过滤条件下推 Specification，不写库。
 * </p>
 *
 * <p>
 * <strong>可用量读取契约（出入库、发药、医嘱目录共用真源）</strong>：凡「是否够扣/够发」均须与本类下列路径之一对齐，禁止在应用层另写一套过期/锁定/状态过滤。
 * </p>
 * <ul>
 * <li><strong>推荐（行级）</strong>：{@link #findAvailableCatalogRowsForInventoryItemIds} — 按 {@code wkf_inventory_item.id}
 * 批量取可用行， 与选品/扫码/行绑定场景一致，避免仅靠批号字符串聚合。</li>
 * <li><strong>目录 / 多品 × 多库位（批号行级）</strong>：{@link #findAvailableCatalogRowsForOrder} — 按物品表 + 物品主键集合 × 库位集合批量取可用行，
 * 供医嘱开立、FIFO 分配、跨库加总前装配。</li>
 * <li><strong>单品全批汇总（少数总闸门）</strong>：{@link #findAvailableInventories} / {@link #calculateTotalAvailableInventory} —
 * 同一物品在单（或可空）库位上全部可用批次；仅在业务明确「不按批号拆行」时使用。</li>
 * </ul>
 *
 * <p>
 * 批号仅作内存聚合键、且需容忍前后端形态差异时，使用 {@link #normalizeLotNumberForAvailabilityMatch}；与四元组持久化字面值等值匹配（{@code lot_number} 列字面量）无关。
 * </p>
 *
 * <p>
 * 与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供目录页「多物品 × 多候选库位」批量可用行加载，避免在应用层循环逐条打库存。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class InventoryItemQueryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLockRepository inventoryLockRepository;
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /**
     * 按主键读取库存项读模型；不存在返回 null。
     *
     * @param id 库存项主键
     * @return 模型或 null
     */
    public InventoryItemModel getInventoryItemById(Long id) {
        InventoryItem entity = inventoryItemRepository.findById(id).orElse(null);
        if (entity == null) {
            return null;
        }
        return this.toItemModel(entity);
    }

    /**
     * 按批号、库位与物品四维精确加载库存项实体（adapter 读路径）。
     */
    public List<InventoryItem> listInventoryItemEntitiesByLotLocationItem(
        String lotNumber, Long locationId, String itemTable, Long itemId) {
        if (locationId == null || itemId == null || StringUtils.isBlank(itemTable) || StringUtils.isBlank(lotNumber)) {
            return new ArrayList<>();
        }
        List<InventoryItem> rows = inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(
            lotNumber.trim(), locationId, itemTable.trim(), itemId);
        List<InventoryItem> activeRows = new ArrayList<>();
        for (InventoryItem row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            activeRows.add(row);
        }
        return activeRows;
    }

    /**
     * 批量按主键加载库存项（空集合避免无意义查询）。
     *
     * @param idList 主键列表
     * @return 读模型列表
     */
    public List<InventoryItemModel> findByIdIn(List<Long> idList) {
        if (idList == null || idList.isEmpty()) {
            return new ArrayList<>();
        }
        List<InventoryItem> rows = inventoryItemRepository.findByIdIn(idList);
        List<InventoryItemModel> result = new ArrayList<>();
        // Loop：遍历查询结果，逐条映射为读模型
        for (InventoryItem row : rows) {
            result.add(this.toItemModel(row));
        }
        return result;
    }

    /**
     * 列出满足「可用」口径的库存批次（正数量、未过期、未锁定），用于发药/出库挑批。
     *
     * <p>
     * 属于「可用量读取契约」中的<strong>单品全批</strong>路径：返回该物品在库位上的全部可用批次简表；行级精确场景请优先
     * {@link #findAvailableCatalogRowsForInventoryItemIds} 或 {@link #findAvailableCatalogRowsForOrder}。
     * </p>
     *
     * @param itemTable 物品主表名
     * @param itemId 物品主键
     * @param locationId 库位，null 表示不限定库位
     * @return 可用批次简表
     */
    public List<AvailableInventoryModel> findAvailableInventories(String itemTable, Long itemId, Long locationId) {
        LocalDateTime now = LocalDateTime.now();
        List<InventoryItem> candidates = this.findAvailableEntities(itemTable, itemId, locationId, now);
        List<AvailableInventoryModel> models = new ArrayList<>();
        // Loop：遍历可用库存实体，组装可用批次简表
        for (InventoryItem row : candidates) {
            AvailableInventoryModel model = new AvailableInventoryModel();
            model.setId(row.getId());
            model.setLotNumber(row.getLotNumber());
            model.setExpirationDate(row.getExpirationDate());
            model.setQuantity(row.getQuantity());
            models.add(model);
        }
        return models;
    }

    /**
     * 调价锁预览：全院加载 ACTIVE 正数量库存行（含已锁定行，不按库位收窄，BR-PRC-LOCK-008）。
     *
     * @param itemTable 物品主表名
     * @param itemIds   物品定义主键集合
     * @return 候选锁库存行；入参非法时空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findActiveInventoryRowsForPriceAdjustmentLockPreview(
        String itemTable,
        Collection<Long> itemIds
    ) {
        if (StringUtils.isBlank(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return new ArrayList<>();
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            predicates.add(
                cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            jakarta.persistence.criteria.Predicate notExpired =
                cb.or(cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                    cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
            predicates.add(notExpired);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Sort sort =
            Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        for (InventoryItem row : rows) {
            InventoryCatalogAvailabilityRowModel model = new InventoryCatalogAvailabilityRowModel();
            model.setId(row.getId());
            model.setItemTable(row.getItemTable());
            model.setItemId(row.getItemId());
            model.setLocationId(row.getLocationId());
            model.setLotNumber(row.getLotNumber());
            model.setExpirationDate(row.getExpirationDate());
            model.setQuantity(row.getQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 汇总可用库存数量（逐批累加 quantity）。
     *
     * @param itemTable 物品主表名
     * @param itemId 物品主键
     * @param locationId 库位，可空
     * @return 总可用数量
     */
    public BigDecimal calculateTotalAvailableInventory(String itemTable, Long itemId, Long locationId) {
        List<AvailableInventoryModel> models = this.findAvailableInventories(itemTable, itemId, locationId);
        BigDecimal total = BigDecimal.ZERO;
        // Loop：累加各批次可用数量，计算总可用库存
        for (AvailableInventoryModel m : models) {
            if (m.getQuantity() != null) {
                total = total.add(m.getQuantity());
            }
        }
        return total;
    }

    /**
     * 批量汇总全院可用库存数量（与 {@link #calculateTotalAvailableInventory} 同一可用口径，{@code locationId} 为空）。
     *
     * @param itemTable 物品主表名
     * @param itemIds   物品定义主键集合
     * @return 按物品主键聚合的可用量总和；入参非法或集合为空时返回空 Map
     */
    public Map<Long, BigDecimal> sumTotalAvailableInventoryByItemIds(String itemTable, Collection<Long> itemIds) {
        Map<Long, BigDecimal> quantityByItemId = new HashMap<>();
        if (StringUtils.isBlank(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return quantityByItemId;
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            predicates.add(
                cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            jakarta.persistence.criteria.Predicate notExpired =
                cb.or(cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                    cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
            predicates.add(notExpired);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Sort sort =
            Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);
        rows = this.withoutLockedInventoryRows(rows);
        // Loop：按 itemId 累加各批次可用数量
        for (InventoryItem row : rows) {
            if (row == null || row.getItemId() == null || row.getQuantity() == null) {
                continue;
            }
            BigDecimal current = quantityByItemId.getOrDefault(row.getItemId(), BigDecimal.ZERO);
            quantityByItemId.put(row.getItemId(), current.add(row.getQuantity()));
        }
        return quantityByItemId;
    }

    /**
     * 批量汇总可用库存（多库位 × 多物品，全批累加；与 {@link #calculateTotalAvailableInventory} 同一可用口径）。
     *
     * @param itemTable   物品主表名
     * @param locationIds 库位主键集合
     * @param itemIds     物品定义主键集合
     * @return 按库位+物品聚合的可用量；入参缺表名或任一集合为空时返回空列表
     */
    public List<InventoryAvailSumModel> sumAvailableInventory(
        String itemTable, Collection<Long> locationIds, Collection<Long> itemIds) {
        List<InventoryAvailSumModel> results = new ArrayList<>();
        if (StringUtils.isBlank(itemTable) || locationIds == null || locationIds.isEmpty()
            || itemIds == null || itemIds.isEmpty()) {
            return results;
        }
        List<InventoryCatalogAvailabilityRowModel> rows =
            this.findAvailableCatalogRowsForOrder(itemTable, new ArrayList<>(itemIds), new ArrayList<>(locationIds));
        Map<Long, Map<Long, BigDecimal>> sumByLocationItem = new HashMap<>();
        for (InventoryCatalogAvailabilityRowModel row : rows) {
            if (row.getLocationId() == null || row.getItemId() == null || row.getQuantity() == null) {
                continue;
            }
            Map<Long, BigDecimal> byItem = sumByLocationItem.computeIfAbsent(row.getLocationId(), ignored -> new HashMap<>());
            BigDecimal current = byItem.getOrDefault(row.getItemId(), BigDecimal.ZERO);
            byItem.put(row.getItemId(), current.add(row.getQuantity()));
        }
        for (Map.Entry<Long, Map<Long, BigDecimal>> locationEntry : sumByLocationItem.entrySet()) {
            for (Map.Entry<Long, BigDecimal> itemEntry : locationEntry.getValue().entrySet()) {
                InventoryAvailSumModel model = new InventoryAvailSumModel();
                model.setLocationId(locationEntry.getKey());
                model.setItemId(itemEntry.getKey());
                model.setQuantity(itemEntry.getValue());
                results.add(model);
            }
        }
        return results;
    }

    /**
     * 医嘱开立目录：同一物品表下多 {@code itemId} × 多候选库位一次性加载可用批次行（效期、数量、状态与锁定规则同 {@link #findAvailableInventories}）。
     *
     * <p>
     * 属于「可用量读取契约」中的<strong>多品 × 多库位批号行级</strong>路径；与按 {@code wkf_inventory_item.id} 批量加载的
     * {@link #findAvailableCatalogRowsForInventoryItemIds} 共用同一套核心谓词与锁定剔除。
     * </p>
     *
     * @param itemTable 物品主表名
     * @param itemIds 物品定义主键集合
     * @param locationIds 候选库位主键集合
     * @return 可用行列表；入参缺表名或任一集合为空时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findAvailableCatalogRowsForOrder(String itemTable,
        List<Long> itemIds, List<Long> locationIds) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (StringUtils.isBlank(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            this.appendCoreAvailableInventoryCatalogRowPredicates(root, cb, now, locationIds, predicates);
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.loadAvailableInventoryCatalogRowsBySpecification(specification);
    }

    /**
     * 按库存项主键批量加载「可用目录行」视图（与 {@link #findAvailableCatalogRowsForOrder} 同一可用口径：可用品状态、正数量、未过期、剔除锁定）。
     *
     * <p>
     * 属于「可用量读取契约」中的<strong>行级（推荐）</strong>路径：与选品、扫码、出库行绑定的 {@code wkf_inventory_item.id} 对齐。
     * </p>
     *
     * @param inventoryItemIds 库存项主键集合；空或 null 时返回空列表
     * @param locationIds 候选库位主键集合；空或 null 时返回空列表
     * @return 可用行列表；入参非法时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel>
        findAvailableCatalogRowsForInventoryItemIds(List<Long> inventoryItemIds, List<Long> locationIds) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (inventoryItemIds == null || inventoryItemIds.isEmpty()) {
            return result;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            this.appendCoreAvailableInventoryCatalogRowPredicates(root, cb, now, locationIds, predicates);
            predicates.add(root.get(FieldNameConstants.ID).in(inventoryItemIds));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.loadAvailableInventoryCatalogRowsBySpecification(specification);
    }

    /**
     * 将「可用目录行」共通谓词追加到 {@link Specification} 条件列表。
     *
     * <p>
     * 本方法表达的是<strong>与具体业务入口无关</strong>的库存可用事实：
     * </p>
     * <ul>
     * <li>未逻辑删除；</li>
     * <li>库位落在调用方给出的集合内；</li>
     * <li>库存状态为「可用品」持久化码；</li>
     * <li>账面数量大于零；</li>
     * <li>效期为空或晚于当前时刻（未过期）。</li>
     * </ul>
     *
     * <p>
     * 调用方在追加本段后，还须自行追加物品表、物品 id 或库存主键等<strong>入口差异</strong>条件。
     * </p>
     *
     * @param root 库存项根
     * @param cb 条件构建器
     * @param now 「当前时刻」，与效期列比较用
     * @param locationIds 允许参与聚合的库位主键集合
     * @param predicates 输出列表；本方法向其 append
     */
    /**
     * 将「可用目录行」共通谓词追加到 {@link Specification} 条件列表。
     *
     * <p>
     * 本方法表达的是<strong>与具体业务入口无关</strong>的库存可用事实：
     * </p>
     * <ul>
     * <li>未逻辑删除；</li>
     * <li>库位落在调用方给出的集合内；</li>
     * <li>库存状态为「可用品」持久化码；</li>
     * <li>账面数量大于零；</li>
     * <li>效期为空或晚于当前时刻（未过期）。</li>
     * </ul>
     *
     * <p>
     * 调用方在追加本段后，还须自行追加物品表、物品 id 或库存主键等<strong>入口差异</strong>条件。
     * </p>
     *
     * @param root 库存项根
     * @param cb 条件构建器
     * @param now 「当前时刻」，与效期列比较用
     * @param locationIds 允许参与聚合的库位主键集合
     * @param predicates 输出列表；本方法向其 append
     */
    private void appendCoreAvailableInventoryCatalogRowPredicates(jakarta.persistence.criteria.Root<InventoryItem> root,
        jakarta.persistence.criteria.CriteriaBuilder cb, LocalDateTime now, List<Long> locationIds,
        List<jakarta.persistence.criteria.Predicate> predicates) {
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        predicates
            .add(cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
        predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
        jakarta.persistence.criteria.Predicate notExpired =
            cb.or(cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
        predicates.add(notExpired);
    }

    /**
     * 执行可用目录行查询：按效期、创建时间排序，一次性落库，剔除锁定行后映射为读侧 DTO。
     *
     * <p>
     * 锁定剔除在 Java 层完成：{@link #withoutLockedInventoryRows} 依赖锁表与库存主键关联， 与「目录行批量」路径共用，避免在 SQL 中重复表达复杂锁语义。
     * </p>
     *
     * @param specification 已含完整谓词（含本类 appendCore 与入口差异）的库存项规格
     * @return 可用行列表；无命中时为空列表
     */
    /**
     * 执行可用目录行查询：按效期、创建时间排序，一次性落库，剔除锁定行后映射为读侧 DTO。
     *
     * <p>
     * 锁定剔除在 Java 层完成：{@link #withoutLockedInventoryRows} 依赖锁表与库存主键关联， 与「目录行批量」路径共用，避免在 SQL 中重复表达复杂锁语义。
     * </p>
     *
     * @param specification 已含完整谓词（含本类 appendCore 与入口差异）的库存项规格
     * @return 可用行列表；无命中时为空列表
     */
    private List<InventoryCatalogAvailabilityRowModel>
        loadAvailableInventoryCatalogRowsBySpecification(Specification<InventoryItem> specification) {
        Sort sort =
            Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);
        List<InventoryItem> unlocked = this.withoutLockedInventoryRows(rows);
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        // Loop：遍历可用库存行，逐条装配为目录可用行读模型
        for (InventoryItem row : unlocked) {
            InventoryCatalogAvailabilityRowModel model = new InventoryCatalogAvailabilityRowModel();
            model.setId(row.getId());
            model.setItemTable(row.getItemTable());
            model.setItemId(row.getItemId());
            model.setLocationId(row.getLocationId());
            model.setLotNumber(row.getLotNumber());
            model.setExpirationDate(row.getExpirationDate());
            model.setQuantity(row.getQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 批号在「可用量汇总 / 内存聚合键」场景下的归一化：trim、Unicode NFKC、{@link Locale#ROOT} 小写。
     *
     * <p>
     * 与四元组持久化字面值等值匹配（{@link InventoryItemRepository#findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc}）无关；
     * 用于跨前后端、复制粘贴导致的批号表面形态差异在内存聚合键上的收敛（应用层与域内读侧均可调用）。
     * </p>
     *
     * @param raw 原始批号，可 null
     * @return 归一化后字符串；输入空白时为 {@link GeneralSharedConstants#EMPTY_STRING}
     */
    public static String normalizeLotNumberForAvailabilityMatch(String raw) {
        if (StringUtils.isBlank(raw)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String trimmed = raw.trim();
        String nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    /**
     * 按四元组（库位 + 物品表 + 物品主键 + 批号字面值）精确匹配单条正常供应且未删除的库存行（DM-WKF-008；{@code supplierId} 不参与定位）。
     *
     * @param key 复合匹配键（仅 {@code locationId}/{@code itemTable}/{@code itemId}/{@code lotNumber} 参与）
     * @return 读模型；key 为空或未命中时为 null
     */
    public InventoryItemModel findMatchingInventory(InventoryMatchKey key) {
        if (key == null) {
            return null;
        }
        if (key.getLocationId() == null || key.getItemId() == null || StringUtils.isBlank(key.getItemTable())) {
            return null;
        }
        String lot;
        if (key.getLotNumber() == null) {
            lot = "";
        } else {
            lot = key.getLotNumber();
        }
        List<InventoryItem> rows =
            inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(lot,
                key.getLocationId(), key.getItemTable().trim(), key.getItemId());
        for (InventoryItem row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(row.getInventoryStatusEnum())) {
                continue;
            }
            return this.toItemModel(row);
        }
        return null;
    }

    /**
     * 按物品信息多维过滤库存项（表名、批号、库位、供应商等可选）。
     *
     * @param query 查询条件，可 null 表示仅软删过滤
     * @return 读模型列表
     */
    public List<InventoryItemModel> findInventoryItemByItemInfo(InventoryItemQuery query) {
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getItemTable())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
                }
                if (query.getItemId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
                }
                if (StringUtils.isNotBlank(query.getLotNumber())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), query.getLotNumber().trim()));
                }
                if (query.getLocationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
                }
                if (query.getSupplierId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.SUPPLIER_ID), query.getSupplierId()));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification);
        List<InventoryItemModel> result = new ArrayList<>();
        // Loop：遍历查询结果，逐条映射为读模型
        for (InventoryItem row : rows) {
            result.add(this.toItemModel(row));
        }
        return result;
    }

    /**
     * 按仓库（库位 id）与物品主表批量列出库存项，用于仓内盘点或调拨候选。
     *
     * @param warehouseId 库位/仓库标识，可 null 表示不按库位过滤
     * @param itemTable 物品主表，可空串表示不按表过滤
     * @return 读模型列表
     */
    public List<InventoryItemModel> getBatchInventoryByWarehouse(Long warehouseId, String itemTable) {
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (warehouseId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), warehouseId));
            }
            if (StringUtils.isNotBlank(itemTable)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification);
        List<InventoryItemModel> result = new ArrayList<>();
        // Loop：遍历查询结果，逐条映射为读模型
        for (InventoryItem row : rows) {
            result.add(this.toItemModel(row));
        }
        return result;
    }

    /**
     * 乐观锁版本比对：入参或实体缺 version 时视为不匹配。
     *
     * @param id 库存项主键
     * @param version 期望版本号
     * @return 一致为 true
     */
    public Boolean checkVersion(Long id, Integer version) {
        if (id == null || version == null) {
            return false;
        }
        InventoryItem item = inventoryItemRepository.findById(id).orElse(null);
        if (item == null) {
            return false;
        }
        if (item.getVersion() == null) {
            return false;
        }
        return item.getVersion().equals(version);
    }

    /**
     * 判断库存项是否存在进行中的锁定记录（如盘点锁、待发锁）。
     *
     * @param inventoryId 库存项主键
     * @return 已锁定为 true；id 空为 false
     */
    public Boolean isLocked(Long inventoryId) {
        if (inventoryId == null) {
            return false;
        }
        return inventoryLockRepository.findByInventoryIdAndLockFlagTrue(inventoryId).isPresent();
    }

    /**
     * 查询库存项当前生效锁的业务单号（调价锁冲突预览 BR-PRC-LOCK-004）。
     *
     * @param inventoryId 库存项主键
     * @return 锁定单号；未锁定时为 {@code null}
     */
    public String findActiveLockBusNo(Long inventoryId) {
        if (inventoryId == null) {
            return null;
        }
        return inventoryLockRepository.findByInventoryIdAndLockFlagTrue(inventoryId)
            .map(InventoryLock::getBusNo)
            .orElse(null);
    }

    /**
     * 库存项分页检索：表、物品、库位、状态与关键字（名称/批号/检索码）组合过滤。
     *
     * @param query 筛选条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<InventoryItemModel> queryInventoryItemsByPage(InventoryItemPageQuery query, Pageable pageable) {
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getItemTable())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
                }
                if (query.getItemId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
                }
                if (query.getLocationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
                }
                if (query.getInventoryStatusEnum() != null) {
                    predicates.add(
                        cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), query.getInventoryStatusEnum()));
                }
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.or(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return inventoryItemRepository.findAll(specification, pageable).map(this::toItemModel);
    }

    /**
     * 按主键强制加载库存项读模型。
     *
     * @param id 主键
     * @return 模型
     * @throws BusinessException 不存在
     */
    public InventoryItemModel requireActiveRow(Long id) {
        InventoryItem item = inventoryItemRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_NOT_FOUND, "库存项不存在"));
        return this.toItemModel(item);
    }

    /**
     * 将库存项聚合行裁剪为列表/分页用读模型（含物品复合键、批号、效期等选品必需列）。
     *
     * @param entity 持久化库存项行
     * @return 读模型；字段与 {@code wkf_inventory_item} 对齐（B.10.8.1 枚举列仍为持久化 code）
     */
    /**
     * 将库存项聚合行裁剪为列表/分页用读模型（含物品复合键、批号、效期等选品必需列）。
     *
     * @param entity 持久化库存项行
     * @return 读模型；字段与 {@code wkf_inventory_item} 对齐（B.10.8.1 枚举列仍为持久化 code）
     */
    private InventoryItemModel toItemModel(InventoryItem entity) {
        InventoryItemModel model = new InventoryItemModel();
        model.setId(entity.getId());
        model.setQuantity(entity.getQuantity());
        model.setInventoryStatusEnum(entity.getInventoryStatusEnum());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setName(entity.getName());
        model.setUnitCode(entity.getUnitCode());
        model.setSupplierId(entity.getSupplierId());
        model.setLotNumber(entity.getLotNumber());
        model.setExpirationDate(entity.getExpirationDate());
        model.setProductionDate(entity.getProductionDate());
        model.setLocationId(entity.getLocationId());
        return model;
    }

    /**
     * 将库存项实体转为药库库存明细行查询模型。
     *
     * @param entity 库存项
     * @return 明细行模型
     */
    /**
     * 将库存项实体转为药库库存明细行查询模型。
     *
     * @param entity 库存项
     * @return 明细行模型
     */
    private WarehouseInventoryDetailItemModel toWarehouseDetailItemModel(InventoryItem entity) {
        WarehouseInventoryDetailItemModel model = new WarehouseInventoryDetailItemModel();
        model.setId(entity.getId());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setName(entity.getName());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setLotNumber(entity.getLotNumber());
        model.setQuantity(entity.getQuantity());
        model.setUnitCode(entity.getUnitCode());
        model.setWarehouseId(entity.getLocationId());
        model.setSupplierId(entity.getSupplierId());
        model.setExpirationDate(entity.getExpirationDate());
        model.setProductionDate(entity.getProductionDate());
        model.setInventoryStatusEnum(entity.getInventoryStatusEnum());
        model.setTraceNo(entity.getTraceNo());
        if (entity.getExpirationDate() != null) {
            LocalDate expirationDate = entity.getExpirationDate().toLocalDate();
            long days = ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
            model.setRemainingDays(Integer.valueOf((int) days));
        }
        return model;
    }

    /**
     * 药库库存明细现库分页：谓词与关键字下推到仓储。
     *
     * <p>
     * 机构与库房数据范围由应用层在调用前通过 {@code LocationQueryService} 与
     * {@link com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver}、
     * {@link com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver} 解析为
     * {@link com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery#getEffectiveWarehouseIds()}，
     * 本方法不在此重复拼接 {@code organization_id}（与 B.10.11 职责划分一致）。
     * </p>
     *
     * @param query 筛选条件（含应用层解析后的库房范围）
     * @param pageable 分页与排序（列表场景须含 {@code py_str} NULLS LAST 语义）
     * @return 分页结果
     */
    /**
     * 药库库存明细现库分页：谓词与关键字下推到仓储。
     *
     * <p>
     * 机构与库房数据范围由应用层在调用前通过 {@code LocationQueryService} 与
     * {@link com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver}、
     * {@link com.openhis.health.module.common.validator.CurrentManagedLocationIdsResolver} 解析为
     * {@link com.openhis.health.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery#getEffectiveWarehouseIds()}，
     * 本方法不在此重复拼接 {@code organization_id}（与 B.10.11 职责划分一致）。
     * </p>
     *
     * @param query 筛选条件（含应用层解析后的库房范围）
     * @param pageable 分页与排序（列表场景须含 {@code py_str} NULLS LAST 语义）
     * @return 分页结果
     */
    public Page<WarehouseInventoryDetailItemModel>
        queryWarehouseInventoryDetailPage(WarehouseInventoryDetailPageQuery query, Pageable pageable) {
        Specification<InventoryItem> specification = this.buildWarehouseInventoryDetailSpecification(query);
        return inventoryItemRepository.findAll(specification, pageable).map(this::toWarehouseDetailItemModel);
    }

    /**
     * 药库库存明细：拉取至多 {@code maxRows} 行用于底部汇总或导出装配。
     *
     * @param query 筛选条件
     * @param maxRows 最大行数（现库汇总 AS BR-009 为 30000）
     * @param sort 排序；汇总可为 {@link Sort#unsorted()}
     * @return 行集合
     */
    /**
     * 药库库存明细：拉取至多 {@code maxRows} 行用于底部汇总或导出装配。
     *
     * @param query 筛选条件
     * @param maxRows 最大行数（现库汇总 AS BR-009 为 30000）
     * @param sort 排序；汇总可为 {@link Sort#unsorted()}
     * @return 行集合
     */
    public List<WarehouseInventoryDetailItemModel>
        queryWarehouseInventoryDetailTopN(WarehouseInventoryDetailPageQuery query, int maxRows, Sort sort) {
        Specification<InventoryItem> specification = this.buildWarehouseInventoryDetailSpecification(query);
        Pageable pageable = PageRequest.of(0, maxRows, sort);
        Page<InventoryItem> page = inventoryItemRepository.findAll(specification, pageable);
        List<WarehouseInventoryDetailItemModel> list = new ArrayList<>();
        // Loop：遍历查询结果，逐条映射为明细行模型
        for (InventoryItem row : page.getContent()) {
            list.add(this.toWarehouseDetailItemModel(row));
        }
        return list;
    }

    /**
     * 组装药库库存明细现库查询 {@link Specification}：库房/供应商/状态/零库存/近效期；分类/医保等级经应用层预解析定义主键 IN 过滤。
     *
     * @param query 筛选入参
     * @return 规格
     */
    private Specification<InventoryItem>
        buildWarehouseInventoryDetailSpecification(WarehouseInventoryDetailPageQuery query) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query == null) {
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            }
            if (query.getEffectiveWarehouseIds() != null && !query.getEffectiveWarehouseIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(query.getEffectiveWarehouseIds()));
            }
            if (query.getSupplierIds() != null && !query.getSupplierIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.SUPPLIER_ID).in(query.getSupplierIds()));
            }
            if (query.getInventoryStatusEnums() != null && !query.getInventoryStatusEnums().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM).in(query.getInventoryStatusEnums()));
            }
            if (query.getZeroStockOnly() != null) {
                // 库存范围：数量等于 0 或数量大于 0（与 inventory_range 术语一致）。
                if (query.getZeroStockOnly()) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                } else {
                    predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                }
            }
            if (query.getExpireWithinDays() != null && query.getExpireWithinDays() >= 0) {
                LocalDateTime deadline = LocalDate.now().plusDays(query.getExpireWithinDays()).atTime(LocalTime.MAX);
                predicates.add(cb.isNotNull(root.get(FieldNameConstants.EXPIRATION_DATE)));
                predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.EXPIRATION_DATE), deadline));
            }
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String trimmed = query.getKeyword().trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                List<jakarta.persistence.criteria.Predicate> keywordOr = new ArrayList<>();
                // 库存项冗余列：名称、批号、拼音/五笔
                keywordOr.add(cb.or(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)));
                // 产品定义预解析：编码、商品名等（B.6.4 跨聚合，禁止 inventory Criteria JOIN）
                if (query.getResolvedMedicationDefinitionIdsForKeyword() != null
                    && !query.getResolvedMedicationDefinitionIdsForKeyword().isEmpty()) {
                    keywordOr.add(this.medicationDefinitionItemPredicate(
                        root, cb, query.getResolvedMedicationDefinitionIdsForKeyword()));
                }
                if (query.getResolvedDeviceDefinitionIdsForKeyword() != null
                    && !query.getResolvedDeviceDefinitionIdsForKeyword().isEmpty()) {
                    keywordOr.add(this.deviceDefinitionItemPredicate(
                        root, cb, query.getResolvedDeviceDefinitionIdsForKeyword()));
                }
                predicates.add(cb.or(keywordOr.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            if (query.getMedicationCategoryCodes() != null && !query.getMedicationCategoryCodes().isEmpty()) {
                predicates.add(this.medicationDefinitionItemPredicate(root, cb, query.getResolvedMedicationDefinitionIds()));
            }
            if (query.getDeviceCategoryCodes() != null && !query.getDeviceCategoryCodes().isEmpty()) {
                predicates.add(this.deviceDefinitionItemPredicate(root, cb, query.getResolvedDeviceDefinitionIds()));
            }
            if (query.getChrgitmLvList() != null && !query.getChrgitmLvList().isEmpty()) {
                List<jakarta.persistence.criteria.Predicate> chrgitmOr = new ArrayList<>();
                chrgitmOr.add(this.medicationDefinitionItemPredicate(root, cb, query.getResolvedMedicationDefinitionIds()));
                chrgitmOr.add(this.deviceDefinitionItemPredicate(root, cb, query.getResolvedDeviceDefinitionIds()));
                predicates.add(cb.or(chrgitmOr.toArray(new jakarta.persistence.criteria.Predicate[0])));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private jakarta.persistence.criteria.Predicate medicationDefinitionItemPredicate(
        Root<InventoryItem> root,
        CriteriaBuilder cb,
        List<Long> definitionIds) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return cb.equal(root.get(FieldNameConstants.ID), -1L);
        }
        return cb.and(
            cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION),
            root.get(FieldNameConstants.ITEM_ID).in(definitionIds));
    }

    private jakarta.persistence.criteria.Predicate deviceDefinitionItemPredicate(
        Root<InventoryItem> root,
        CriteriaBuilder cb,
        List<Long> definitionIds) {
        if (definitionIds == null || definitionIds.isEmpty()) {
            return cb.equal(root.get(FieldNameConstants.ID), -1L);
        }
        return cb.and(
            cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.ADM_DEVICE_DEFINITION),
            root.get(FieldNameConstants.ITEM_ID).in(definitionIds));
    }

    /**
     * 内部：筛出可用库存实体集合，并剔除存在 lockFlag=true 锁定行的批次。
     *
     * @param itemTable 物品主表
     * @param itemId 物品 id
     * @param locationId 库位，可空
     * @param now 效期判定时刻
     * @return 可用实体列表（已排除锁定）
     */
    /**
     * 内部：筛出可用库存实体集合，并剔除存在 lockFlag=true 锁定行的批次。
     *
     * @param itemTable 物品主表
     * @param itemId 物品 id
     * @param locationId 库位，可空
     * @param now 效期判定时刻
     * @return 可用实体列表（已排除锁定）
     */
    private List<InventoryItem> findAvailableEntities(String itemTable, Long itemId, Long locationId,
        LocalDateTime now) {
        if (StringUtils.isBlank(itemTable) || itemId == null) {
            return new ArrayList<>();
        }
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemId));
            predicates
                .add(cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            if (locationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), locationId));
            }
            jakarta.persistence.criteria.Predicate notExpired =
                cb.or(cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                    cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
            predicates.add(notExpired);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Sort sort =
            Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);
        return this.withoutLockedInventoryRows(rows);
    }

    /**
     * 剔除存在 {@code lockFlag=true} 锁定记录的库存行。
     *
     * @param rows 已由规格查询筛出的候选库存实体集合（可能为空）
     * @return 去掉被锁定主键后的实体列表；无 id 或全未锁时返回原集合引用
     */
    /**
     * 剔除存在 {@code lockFlag=true} 锁定记录的库存行。
     *
     * @param rows 已由规格查询筛出的候选库存实体集合（可能为空）
     * @return 去掉被锁定主键后的实体列表；无 id 或全未锁时返回原集合引用
     */
    private List<InventoryItem> withoutLockedInventoryRows(List<InventoryItem> rows) {
        List<Long> ids = new ArrayList<>();
        // Loop：遍历候选行，收集库存项主键
        for (InventoryItem row : rows) {
            if (row.getId() != null) {
                ids.add(row.getId());
            }
        }
        if (ids.isEmpty()) {
            return rows;
        }
        List<InventoryLock> locks = inventoryLockRepository.findByInventoryIdInAndLockFlagTrue(ids);
        if (locks.isEmpty()) {
            return rows;
        }
        Set<Long> lockedIds = new HashSet<>();
        // Loop：遍历锁定记录，收集被锁定库存项主键
        for (InventoryLock lock : locks) {
            if (lock.getInventoryId() != null) {
                lockedIds.add(lock.getInventoryId());
            }
        }
        List<InventoryItem> unlocked = new ArrayList<>();
        // Loop：遍历候选行，过滤掉被锁定的行
        for (InventoryItem row : rows) {
            if (row.getId() == null) {
                continue;
            }
            if (!lockedIds.contains(row.getId())) {
                unlocked.add(row);
            }
        }
        return unlocked;
    }

    /**
     * 按四元组匹配既有可扣减库存项主键：{@code location_id + item_table + item_id + lot_number}（批号为持久化字面值）； 仅返回正常供应、未删除且未锁定的首条（与
     *
     * @param key 匹配键（仅库位、物品表、物品主键、批号参与）
     * @return 命中则返回主键
     */
    public Optional<Long> findFirstIdByInventoryMatchKey(InventoryMatchKey key) {
        if (key == null) {
            return Optional.empty();
        }
        String lot;
        if (key.getLotNumber() == null) {
            lot = "";
        } else {
            lot = key.getLotNumber();
        }
        return this.findFirstActiveUnlockedIdByQuadruple(key.getLocationId(), key.getItemTable(), key.getItemId(), lot);
    }

    /**
     * 内部：四元组定位首条可参与增减的库存主键（ACTIVE、未删除、未锁定）。
     */
    private Optional<Long> findFirstActiveUnlockedIdByQuadruple(Long locationId, String itemTable, Long itemId,
        String lotNumber) {
        if (locationId == null || itemId == null || StringUtils.isBlank(itemTable)) {
            return Optional.empty();
        }
        String lot;
        if (lotNumber == null) {
            lot = "";
        } else {
            lot = lotNumber;
        }
        List<InventoryItem> rows = inventoryItemRepository
            .findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(lot, locationId, itemTable.trim(), itemId);
        List<InventoryItem> activeRows = new ArrayList<>();
        for (InventoryItem row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(row.getInventoryStatusEnum())) {
                continue;
            }
            activeRows.add(row);
        }
        if (activeRows.isEmpty()) {
            return Optional.empty();
        }
        List<InventoryItem> unlocked = this.withoutLockedInventoryRows(activeRows);
        if (unlocked.isEmpty()) {
            return Optional.empty();
        }
        InventoryItem first = unlocked.get(0);
        if (first.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(first.getId());
    }

    /**
     * 门诊发药前置库存断言：库存账面数量一律为<strong>最小单位（拆零单位）</strong>； 先将发放行业务单位数量按目录换算比转为最小单位，再与候选批次可用量之和比较。
     *
     * <p>
     * 若指定批号则仅累计匹配批次；批号为空则累计全部可用批次。
     * </p>
     *
     * @param itemTableName 物品主表名（如
     *            {@link com.openhis.health.domain.share.constants.TableNameConstants#MED_MEDICATION_DEFINITION}）
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 发放库房库位主键
     * @param lotNumber 批号；可空表示不按批号收窄
     * @param requiredQuantityInBusinessUnit 发放行业务单位下的需求数量
     * @param businessUnitCode 与发放行一致的单位编码（通常同 {@code MedicationDispense#getUnitCode()}）
     * @param productDefinition 药品产品定义读模型，用于单位换算
     * @throws BusinessException
     *             参数不完整、无法换算或可用量不足（{@link com.openhis.health.domain.share.constants.ErrorCodeConstants#EX_OPP_205_STOCK_OR_LOCATION_INVALID}）
     */
    /**
     * 发药前库存充足断言（单位换算基于 {@link com.openhis.health.domain.share.product.MedicationProductUnitConversionSource}）。
     */
    public void assertSufficientForMedicationDispense(String itemTableName, Long medicationId, Long pharmacyLocationId,
        String lotNumber, BigDecimal requiredQuantityInBusinessUnit, String businessUnitCode,
        com.openhis.health.domain.share.product.MedicationProductUnitConversionSource productDefinition) {
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少药品或库房标识");
        }
        if (requiredQuantityInBusinessUnit == null || requiredQuantityInBusinessUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言需求数量必须大于 0");
        }
        if (StringUtils.isBlank(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少物品主表名");
        }
        BigDecimal requiredInSplitUnit = MedicationProductQuantityToSplitUnitConverter
            .medicationQuantityToSplitUnit(productDefinition, businessUnitCode, requiredQuantityInBusinessUnit);
        List<AvailableInventoryModel> rows =
            this.findAvailableInventories(itemTableName, medicationId, pharmacyLocationId);
        BigDecimal sum = BigDecimal.ZERO;
        for (AvailableInventoryModel row : rows) {
            if (row.getQuantity() == null) {
                continue;
            }
            if (StringUtils.isNotBlank(lotNumber)) {
                String rowLot = row.getLotNumber();
                if (rowLot == null) {
                    continue;
                }
                if (!lotNumber.trim().equals(rowLot.trim())) {
                    continue;
                }
            }
            sum = sum.add(row.getQuantity());
        }
        if (sum.compareTo(requiredInSplitUnit) < 0) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "可用库存不足以覆盖待发数量");
        }
    }

    /**
     * 发药前库存充足断言（物理库存 − 全局待发预占，GAP-EXE-003）。
     *
     * @param excludeDispenseId 当前发放行主键；计入预占统计时排除自身
     */
    public void assertSufficientForMedicationDispenseWithReserve(
        String itemTableName,
        Long medicationId,
        Long pharmacyLocationId,
        String lotNumber,
        BigDecimal requiredQuantityInBusinessUnit,
        String businessUnitCode,
        com.openhis.health.domain.share.product.MedicationProductUnitConversionSource productDefinition,
        Long excludeDispenseId) {
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少药品或库房标识");
        }
        if (requiredQuantityInBusinessUnit == null || requiredQuantityInBusinessUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言需求数量必须大于 0");
        }
        if (StringUtils.isBlank(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少物品主表名");
        }
        BigDecimal requiredInSplitUnit = MedicationProductQuantityToSplitUnitConverter
            .medicationQuantityToSplitUnit(productDefinition, businessUnitCode, requiredQuantityInBusinessUnit);
        List<AvailableInventoryModel> rows =
            this.findAvailableInventories(itemTableName, medicationId, pharmacyLocationId);
        BigDecimal physicalSum = BigDecimal.ZERO;
        for (AvailableInventoryModel row : rows) {
            if (row.getQuantity() == null) {
                continue;
            }
            if (StringUtils.isNotBlank(lotNumber)) {
                String rowLot = row.getLotNumber();
                if (rowLot == null) {
                    continue;
                }
                if (!lotNumber.trim().equals(rowLot.trim())) {
                    continue;
                }
            }
            physicalSum = physicalSum.add(row.getQuantity());
        }
        BigDecimal reserveInSplitUnit = medicationDispenseQueryService.sumDispenseExeReserveQuantityInSplitUnit(
            pharmacyLocationId, medicationId, productDefinition, excludeDispenseId);
        BigDecimal netAvailable = physicalSum.subtract(reserveInSplitUnit);
        if (netAvailable.compareTo(requiredInSplitUnit) < 0) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "可用库存不足以覆盖待发数量");
        }
    }

    /**
     * 对 FIFO Gate 涉及的库存账面行加悲观写锁（GAP-EXE-001/005）。
     */
    public void lockInventoryRowsForCatalogAvailability(List<InventoryCatalogAvailabilityRowModel> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (InventoryCatalogAvailabilityRowModel row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        inventoryItemRepository.findWithLockByIdIn(ids);
    }

    /**
     * 定位门诊发药将要扣减的<strong>单条</strong>库存项主键（药品物品表 + 药品定义 + 库房 + 批号）。
     *
     * <p>
     * 排除已逻辑删除行；若命中零条或多条（例如不同供应商五元组并存的历史脏数据）则阻断发药，避免误扣。
     * </p>
     *
     * @param itemTableName 物品主表名
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 库房库位主键
     * @param lotNumber 批号（非空且 trim 后用于等值匹配）
     * @return 库存项主键
     * @throws BusinessException 参数非法、未命中或命中多条
     */
    public Long requireInventoryItemIdForMedicationDispenseBatch(String itemTableName, Long medicationId,
        Long pharmacyLocationId, String lotNumber) {
        InventoryItem hit = resolveSingleActiveMedicationInventoryBatchEntity(itemTableName, medicationId,
            pharmacyLocationId, lotNumber);
        if (hit.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "库存账面行缺少主键，无法扣减");
        }
        return hit.getId();
    }

    /**
     * 解析药品在指定库房+批号下的唯一启用库存账面行（与发药扣减定位规则一致）。
     *
     * @param itemTableName 物品主表名
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 库房库位主键
     * @param lotNumber 批号
     * @return 非空实体
     * @throws BusinessException 参数非法、未命中或命中多条
     */
    private InventoryItem resolveSingleActiveMedicationInventoryBatchEntity(String itemTableName, Long medicationId,
        Long pharmacyLocationId, String lotNumber) {
        if (StringUtils.isBlank(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "定位库存项缺少物品主表名");
        }
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "定位库存项缺少药品或库房标识");
        }
        if (StringUtils.isBlank(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT,
                "发放批号为空时无法在账面锁定单一库存批次，请先执行保存配药并选定批号");
        }
        List<InventoryItem> rows =
            inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(lotNumber.trim(),
                pharmacyLocationId, itemTableName.trim(), medicationId);
        List<InventoryItem> activeRows = new ArrayList<>();
        for (InventoryItem row : rows) {
            if (row == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            activeRows.add(row);
        }
        if (activeRows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "未找到与发放批号匹配的库存账面行");
        }
        if (activeRows.size() > 1) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                "同一药品同一批号存在多条库存账面记录，禁止自动扣减");
        }
        return activeRows.get(0);
    }

    /**
     * 定位耗材发放将要扣减的<strong>单条</strong>库存项主键（物品表 + 耗材定义主键 + 库位 + 批号）。
     *
     * <p>
     * 规则与 {@link #requireInventoryItemIdForMedicationDispenseBatch} 同构：排除逻辑删除；零条或多条均阻断。
     * </p>
     *
     * @param itemTableName 物品主表名（如 {@code adm_device_definition}）
     * @param deviceDefinitionId 耗材定义主键
     * @param locationId 库房库位主键
     * @param lotNumber 批号（trim 后等值匹配）
     * @return 库存项主键
     */
    public Long requireInventoryItemIdForDeviceDispenseBatch(String itemTableName, Long deviceDefinitionId,
        Long locationId, String lotNumber) {
        InventoryItem hit = this.resolveSingleActiveDeviceInventoryBatchEntity(itemTableName, deviceDefinitionId,
            locationId, lotNumber);
        if (hit.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "库存账面行缺少主键，无法扣减");
        }
        return hit.getId();
    }

    private InventoryItem resolveSingleActiveDeviceInventoryBatchEntity(String itemTableName, Long deviceDefinitionId,
        Long locationId, String lotNumber) {
        if (StringUtils.isBlank(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "定位耗材库存项缺少物品主表名");
        }
        if (deviceDefinitionId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "定位耗材库存项缺少耗材定义或库房标识");
        }
        if (StringUtils.isBlank(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "发放批号为空时无法在账面锁定单一库存批次");
        }
        List<InventoryItem> rows =
            inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(lotNumber.trim(),
                locationId, itemTableName.trim(), deviceDefinitionId);
        List<InventoryItem> activeRows = new ArrayList<>();
        for (InventoryItem row : rows) {
            if (row == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            activeRows.add(row);
        }
        if (activeRows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID, "未找到与发放批号匹配的耗材库存账面行");
        }
        if (activeRows.size() > 1) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                "同一耗材同一批号存在多条库存账面记录，禁止自动扣减");
        }
        return activeRows.get(0);
    }

    /**
     * 全院范围列出某物品可用库存实体（调价加锁/预览专用，BR-PRC-LOCK-001～003）。
     *
     * <p>口径与 {@link #findAvailableEntities} 一致（ACTIVE、quantity&gt;0、未过期），
     * 但<strong>不排除</strong>已锁定行，以便预览与按 inventory_id 逐行加锁。</p>
     *
     * @param itemTable 物品物理表
     * @param itemId    物品定义主键
     * @return 库存实体列表；参数非法时返回空列表
     */
    public List<InventoryItem> listHospitalWideActiveInventoryRows(String itemTable, Long itemId) {
        if (StringUtils.isBlank(itemTable) || itemId == null) {
            return new ArrayList<>();
        }
        LocalDateTime now = LocalDateTime.now();
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemId));
            predicates.add(
                cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            jakarta.persistence.criteria.Predicate notExpired = cb.or(
                cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
            predicates.add(notExpired);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Sort sort = Sort.by(
            Sort.Order.asc(FieldNameConstants.LOT_NUMBER),
            Sort.Order.asc(FieldNameConstants.LOCATION_ID),
            Sort.Order.asc(FieldNameConstants.ID));
        return inventoryItemRepository.findAll(specification, sort);
    }
}
