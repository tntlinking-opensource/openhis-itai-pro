/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.zxplat.domain.share.product.MedicationProductUnitConversionSource;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.workflow.inventoryitem.entity.InventoryItem;
import com.zxplat.domain.workflow.inventoryitem.entity.InventoryLock;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryItemQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.InventoryProductAggregateKey;
import com.zxplat.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery;
import com.zxplat.domain.workflow.inventoryitem.queries.model.AvailableInventoryModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryProductLatestExpiryNearModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryProductQuantityAggregateModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryAvailSumModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryCatalogAvailabilityRowModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.InventoryItemModel;
import com.zxplat.domain.workflow.inventoryitem.queries.model.WarehouseInventoryDetailItemModel;
import com.zxplat.domain.workflow.inventoryitem.repository.InventoryItemRepository;
import com.zxplat.domain.workflow.inventoryitem.repository.InventoryLockRepository;
import com.zxplat.domain.workflow.inventoryitem.support.MedicationProductQuantityToSplitUnitConverter;
import com.zxplat.domain.workflow.inventoryitem.valueobject.InventoryMatchKey;
import com.zxplat.framework.core.application.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 库存项读侧查询服务（DM-WKF-008）。
 *
 * <p>
 * 职责：可用库存筛选（含效期与锁定）、库房可操作量 / 账面量分流、多维检索、乐观锁版本比对及分页维护；过滤条件下推 Specification，不写库。
 * </p>
 *
 * <p>
 * <strong>临床可用量读取契约（医嘱开立 / 发药 / FEFO）</strong>：过期批不可开、不可发；凡「是否够开/够发」须与下列路径对齐，禁止削弱效期谓词。
 * </p>
 * <ul>
 * <li><strong>推荐（行级）</strong>：{@link #findAvailableCatalogRowsForInventoryItemIds}</li>
 * <li><strong>目录 / 多品 × 多库位</strong>：{@link #findAvailableCatalogRowsForOrder}</li>
 * <li><strong>单品全批汇总</strong>：{@link #findAvailableInventories} / {@link #calculateTotalAvailableInventory}</li>
 * </ul>
 *
 * <p>
 * <strong>库房单据契约（过期 / 锁定均不门禁）</strong>：药库 / 药房 / 科室全部单据选品、数量回填与草稿断言按账面结存；
 * 禁止误用临床 {@code findAvailableCatalogRows*}。
 * </p>
 * <ul>
 * <li><strong>可操作量 / 账面量</strong>：{@link #findWarehouseOperableCatalogRowsForOrder} /
 * {@link #findWarehouseOperableCatalogRowsForInventoryItemIds} /
 * {@link #findWarehouseBookCatalogRowsForOrder} — 可用品、数量 &gt;= 0，<strong>不过期、不剔锁定</strong></li>
 * </ul>
 *
 * <p>
 * 批号仅作内存聚合键、且需容忍前后端形态差异时，使用 {@link #normalizeLotNumberForAvailabilityMatch}；与四元组持久化字面值等值匹配（{@code lot_number} 列字面量）无关。
 * </p>
 *
 * <p>
 * 与 {@code AS-OP-DOR-003} IF-DOR-11 对齐：提供目录页「多物品 × 多候选库位」批量可用行加载，避免在应用层循环逐条打库存。
 * </p>
 *
 * <p>
 * <strong>跨聚合</strong>：仅注入本聚合 {@link InventoryItemRepository} / {@link InventoryLockRepository}；
 * {@link EntityManager} 仅用于本表 {@code GROUP BY} 汇总（库存预警），禁止 JOIN 他聚合表（B.6.4）。
 * </p>
 *
 * <p>聚合边界：仅本聚合 {@link InventoryItemRepository}、{@link InventoryLockRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class InventoryItemQueryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryLockRepository inventoryLockRepository;
    private final EntityManager entityManager;

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
        if (locationId == null || itemId == null || StringUtils.isEmpty(itemTable) || StringUtils.isEmpty(lotNumber)) {
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
            // 调价子表按库房拆行依赖本字段；其它调用方可忽略
            model.setLocationId(row.getLocationId());
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
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
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
            model.setCreatedAt(row.getCreatedAt());
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
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
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
        if (StringUtils.isEmpty(itemTable) || locationIds == null || locationIds.isEmpty()
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
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
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
     * 划价/目录回落：按物品表 + 多 itemId 批量加载可用批次行（<strong>不限定库位</strong>）。
     *
     * <p>可用口径与 {@link #findAvailableCatalogRowsForOrder} 同构（可用品、正数量、未过期、剔除锁定），
     * 仅省略库位 IN 谓词。供发材模板候选库位为空时仍能按品展开批号行。</p>
     *
     * <p>仅本聚合 {@code InventoryItemRepository}；规模风险：itemIds 须为当前页产品 id（禁止全库扫）。</p>
     *
     * @param itemTable 物品主表名
     * @param itemIds   物品定义主键集合
     * @return 可用行列表；入参缺表名或 itemIds 为空时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findAvailableCatalogRowsByItemIds(
        String itemTable,
        Collection<Long> itemIds
    ) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> idList = new ArrayList<>(itemIds);
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            // 不限库位：仅保留可用品/正数量/未过期/未删除
            this.appendCoreAvailableInventoryCatalogRowPredicatesWithoutLocation(root, cb, now, predicates);
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(idList));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.loadAvailableInventoryCatalogRowsBySpecification(specification);
    }

    /**
     * 护士划价选品：按物品表 + 多 itemId × 管辖库位批量加载库存目录行（<strong>含账面数量 0</strong>）。
     *
     * <p>与 {@link #findAvailableCatalogRowsForOrder} 同构（可用品、未过期、未删除、库位 IN、剔除锁定），
     * 唯一差异：数量谓词为 {@code quantity &gt;= 0}（有库存记录即返回，零库存行仍展示；无记录不返回）。</p>
     *
     * <p>仅本聚合 {@code InventoryItemRepository}；规模风险：itemIds/locationIds 须为当前页与管辖库位（禁止全库扫）。</p>
     *
     * @param itemTable   物品主表名
     * @param itemIds     物品定义主键集合
     * @param locationIds 允许的库位主键（如登录人管辖科室药房）
     * @return 库存目录行；入参缺表名或任一侧集合为空时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findCatalogInventoryRowsIncludingZeroForLocations(
        String itemTable,
        Collection<Long> itemIds,
        List<Long> locationIds
    ) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Long> idList = new ArrayList<>(itemIds);
        List<Long> locationIdList = new ArrayList<>(locationIds);
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            // 含数量 0：有库存行即返回；无记录则自然不命中
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(
                cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
            jakarta.persistence.criteria.Predicate notExpired =
                cb.or(cb.isNull(root.get(FieldNameConstants.EXPIRATION_DATE)),
                    cb.greaterThan(root.get(FieldNameConstants.EXPIRATION_DATE), now));
            predicates.add(notExpired);
            predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIdList));
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(idList));
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
     * 库房单据可操作量（多品 × 多库位批号行）：未删、可用品、库位 IN；<strong>不过滤效期、不剔除锁定</strong>。
     *
     * <p>
     * 业务口径：过期 / 锁定仅约束医生开立 / 发药等临床路径；药库 / 药房 / 科室全部单据选品与数量回填看账面结存，
     * 与 {@link #findWarehouseBookCatalogRowsForOrder} 同口径（数量 &gt;= 0）。
     * </p>
     *
     * <p>仅本聚合 {@code InventoryItemRepository}；规模风险：itemIds/locationIds 须为当前页与单据库位（禁止全库扫）。</p>
     *
     * @param itemTable   物品主表名
     * @param itemIds     物品定义主键集合
     * @param locationIds 候选库位主键集合
     * @return 账面行列表；入参缺表名或任一集合为空时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findWarehouseOperableCatalogRowsForOrder(String itemTable,
        List<Long> itemIds, List<Long> locationIds) {
        // 库房单据与账面量同口径：不过期、不剔锁定
        return this.findWarehouseBookCatalogRowsForOrder(itemTable, itemIds, locationIds);
    }

    /**
     * 库房单据可操作量（按库存项主键批量）：未删、可用品、库位 IN；<strong>不过滤效期、不剔除锁定</strong>。
     *
     * <p>供出库 / 调出 / 请领等草稿行断言与选品回填；与账面量同口径。</p>
     *
     * @param inventoryItemIds 库存项主键集合；空或 null 时返回空列表
     * @param locationIds      候选库位主键集合；空或 null 时返回空列表
     * @return 账面行列表；入参非法时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findWarehouseOperableCatalogRowsForInventoryItemIds(
        List<Long> inventoryItemIds, List<Long> locationIds) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (inventoryItemIds == null || inventoryItemIds.isEmpty()) {
            return result;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            // 与账面共通谓词一致：不过期；数量 >= 0
            this.appendCoreWarehouseBookInventoryCatalogRowPredicates(root, cb, locationIds, predicates);
            predicates.add(root.get(FieldNameConstants.ID).in(inventoryItemIds));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        // 不剔除锁定行
        return this.loadBookInventoryCatalogRowsBySpecification(specification);
    }

    /**
     * 库房账面量目录行（多品 × 多库位）：未删、可用品、库位 IN；<strong>不过滤效期、不剔除锁定、数量 &gt;= 0</strong>。
     *
     * <p>
     * 供损益 / 盘点 / 出库等全部库房单据展示与断言：过期、锁定批号仍按账面剩余数量回填。
     * </p>
     *
     * <p>仅本聚合 {@code InventoryItemRepository}；规模风险：itemIds/locationIds 须为当前页与单据库位。</p>
     *
     * @param itemTable   物品主表名
     * @param itemIds     物品定义主键集合
     * @param locationIds 候选库位主键集合
     * @return 账面行列表；入参缺表名或任一集合为空时返回空列表
     */
    public List<InventoryCatalogAvailabilityRowModel> findWarehouseBookCatalogRowsForOrder(String itemTable,
        List<Long> itemIds, List<Long> locationIds) {
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        if (StringUtils.isEmpty(itemTable) || itemIds == null || itemIds.isEmpty()) {
            return result;
        }
        if (locationIds == null || locationIds.isEmpty()) {
            return result;
        }
        Specification<InventoryItem> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            this.appendCoreWarehouseBookInventoryCatalogRowPredicates(root, cb, locationIds, predicates);
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
            predicates.add(root.get(FieldNameConstants.ITEM_ID).in(itemIds));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.loadBookInventoryCatalogRowsBySpecification(specification);
    }

    /**
     * 将「库房账面目录行」共通谓词追加到条件列表（未删、库位 IN、可用品、数量 &gt;= 0；不含效期、不含锁定）。
     *
     * @param root        库存项根
     * @param cb          条件构建器
     * @param locationIds 允许参与聚合的库位主键集合
     * @param predicates  输出列表；本方法向其 append
     */
    private void appendCoreWarehouseBookInventoryCatalogRowPredicates(
        jakarta.persistence.criteria.Root<InventoryItem> root, jakarta.persistence.criteria.CriteriaBuilder cb,
        List<Long> locationIds, List<jakarta.persistence.criteria.Predicate> predicates) {
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates
            .add(cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
        predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
        predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
    }

    /**
     * 执行账面目录行查询：按效期、创建时间排序后映射为读侧 DTO，<strong>不剔除锁定行</strong>。
     *
     * @param specification 已含完整谓词的库存项规格
     * @return 账面行列表；无命中时为空列表
     */
    private List<InventoryCatalogAvailabilityRowModel>
        loadBookInventoryCatalogRowsBySpecification(Specification<InventoryItem> specification) {
        Sort sort =
            Sort.by(Sort.Order.asc(FieldNameConstants.EXPIRATION_DATE), Sort.Order.asc(FieldNameConstants.CREATED_AT));
        List<InventoryItem> rows = inventoryItemRepository.findAll(specification, sort);
        List<InventoryCatalogAvailabilityRowModel> result = new ArrayList<>();
        // Loop：账面行逐条装配读模型（保留锁定行，库房单据一律按账面展示）
        for (InventoryItem row : rows) {
            InventoryCatalogAvailabilityRowModel model = new InventoryCatalogAvailabilityRowModel();
            model.setId(row.getId());
            model.setItemTable(row.getItemTable());
            model.setItemId(row.getItemId());
            model.setLocationId(row.getLocationId());
            model.setLotNumber(row.getLotNumber());
            model.setExpirationDate(row.getExpirationDate());
            model.setCreatedAt(row.getCreatedAt());
            model.setQuantity(row.getQuantity());
            result.add(model);
        }
        return result;
    }

    /**
     * 将「可用目录行」共通谓词追加到 {@link Specification} 条件列表。
     *
     * <p>
     * 本方法表达的是<strong>与具体业务入口无关</strong>的库存可用事实（<strong>临床可用量</strong>）：
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
     * 库房单据请改用 {@link #findWarehouseOperableCatalogRowsForOrder} /
     * {@link #findWarehouseBookCatalogRowsForOrder}，勿削弱本方法效期谓词。
     * </p>
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
        this.appendCoreAvailableInventoryCatalogRowPredicatesWithoutLocation(root, cb, now, predicates);
        predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(locationIds));
    }

    /**
     * 可用目录行共通谓词（不含库位限定）：未删除、可用品、正数量、未过期。
     *
     * @param root       库存项根
     * @param cb         条件构建器
     * @param now        当前时刻（效期比较）
     * @param predicates 输出列表
     */
    private void appendCoreAvailableInventoryCatalogRowPredicatesWithoutLocation(
        jakarta.persistence.criteria.Root<InventoryItem> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        LocalDateTime now,
        List<jakarta.persistence.criteria.Predicate> predicates
    ) {
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
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
            model.setCreatedAt(row.getCreatedAt());
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
        if (StringUtils.isEmpty(raw)) {
            return GeneralSharedConstants.EMPTY_STRING;
        }
        String trimmed = raw.trim();
        String nfkc = Normalizer.normalize(trimmed, Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    /**
     * 按五元组（库位 + 物品表 + 物品主键 + 批号字面值 + 供应商）精确匹配单条正常供应且未删除的库存行（DM-WKF-008）。
     *
     * <p>{@code supplierId} 非空时按供应商收窄；为空时忽略数量已清零的历史行，同批多行按 FEFO 返回首条有量行（与发药择行一致，不因多供应商返回空）。</p>
     *
     * @param key 复合匹配键
     * @return 读模型；key 为空或未命中时为 null
     */
    public InventoryItemModel findMatchingInventory(InventoryMatchKey key) {
        if (key == null) {
            return null;
        }
        Optional<Long> id = this.findFirstIdByInventoryMatchKey(key);
        if (id.isEmpty()) {
            return null;
        }
        return this.getInventoryItemById(id.get());
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
                if (StringUtils.isNotEmpty(query.getItemTable())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
                }
                if (query.getItemId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
                }
                if (StringUtils.isNotEmpty(query.getLotNumber())) {
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
            if (StringUtils.isNotEmpty(itemTable)) {
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
     * 列出库位下现库 DISTINCT {@code item_table}（日结按库位解析药/耗）。
     *
     * @param locationId 库位主键
     * @return 表名列表；库位为空时返回空列表
     */
    public List<String> listDistinctItemTablesByLocationId(Long locationId) {
        if (locationId == null) {
            return new ArrayList<>();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<InventoryItem> root = cq.from(InventoryItem.class);
        cq.select(root.get(FieldNameConstants.ITEM_TABLE));
        cq.distinct(true);
        cq.where(
            cb.equal(root.get(FieldNameConstants.LOCATION_ID), locationId),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
        );
        cq.orderBy(cb.asc(root.get(FieldNameConstants.ITEM_TABLE)));
        return this.entityManager.createQuery(cq).getResultList();
    }

    /**
     * 列出库房下未删除库存行（期初整库替换清账读侧 · 裁决 4B）。
     *
     * @param locationId 库房位置主键
     * @return 库存读模型列表；库位空时返回空列表
     */
    public List<InventoryItemModel> listByLocationId(Long locationId) {
        if (locationId == null) {
            return new ArrayList<>();
        }
        List<InventoryItem> rows = inventoryItemRepository.findByLocationIdAndIsDeletedFalse(locationId);
        List<InventoryItemModel> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
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
     * 库存项分页检索：表、物品、库位、状态与关键字组合过滤。
     *
     * <p>关键字匹配库存冗余列（名称/批号/拼音/五笔），并可 OR 应用层预解析的药品/耗材定义主键
     *（编码、商品名等，见 {@link InventoryItemPageQuery#getResolvedMedicationDefinitionIdsForKeyword()}）。</p>
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
                if (StringUtils.isNotEmpty(query.getItemTable())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
                }
                if (query.getItemIds() != null && !query.getItemIds().isEmpty()) {
                    // 预选/本页物品 id IN（优先于单 itemId）
                    predicates.add(root.get(FieldNameConstants.ITEM_ID).in(query.getItemIds()));
                } else if (query.getItemId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
                }
                if (query.getLocationIds() != null && !query.getLocationIds().isEmpty()) {
                    // 多库位 IN（优先于单 locationId；管辖科室药房等）
                    predicates.add(root.get(FieldNameConstants.LOCATION_ID).in(query.getLocationIds()));
                } else if (query.getLocationId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), query.getLocationId()));
                }
                if (query.getInventoryStatusEnum() != null) {
                    predicates.add(
                        cb.equal(root.get(FieldNameConstants.INVENTORY_STATUS_ENUM), query.getInventoryStatusEnum()));
                }
                if (Boolean.TRUE.equals(query.getIncludeZeroQuantity())) {
                    // 护士划价等：有库存记录即可，数量 0 仍返回
                    predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                } else if (Boolean.TRUE.equals(query.getPositiveQuantityOnly())) {
                    // —— 选品分页仅展示账面量大于零的批号行，与库存明细「有库存」列表口径一致。——
                    predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                }
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    List<jakarta.persistence.criteria.Predicate> keywordOr = new ArrayList<>();
                    // 库存项冗余列：名称、批号、拼音/五笔（不含商品名；商品名经定义预解析）
                    keywordOr.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)));
                    // 产品定义预解析：编码、商品名等（B.6.4；与库存明细选品同口径）
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
                    // B.2.1：库存选品关键字对冗余列精确全等置顶（定义 id 命中无法在本表做全等）
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        List.of(cb.desc(root.get(FieldNameConstants.ID))),
                        root.get(FieldNameConstants.NAME),
                        root.get(FieldNameConstants.LOT_NUMBER),
                        root.get(FieldNameConstants.PY_STR),
                        root.get(FieldNameConstants.WB_STR));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return inventoryItemRepository.findAll(specification, effective).map(this::toItemModel);
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
     * {@link com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver}、
     * {@link com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver} 解析为
     * {@link com.zxplat.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery#getEffectiveWarehouseIds()}，
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
     * {@link com.zxplat.application.shared.validator.CurrentManagedOrganizationIdsResolver}、
     * {@link com.zxplat.application.shared.validator.CurrentManagedLocationIdsResolver} 解析为
     * {@link com.zxplat.domain.workflow.inventoryitem.queries.WarehouseInventoryDetailPageQuery#getEffectiveWarehouseIds()}，
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
            if (StringUtils.isNotEmpty(query.getItemTable())) {
                // 项目类型：按药品/耗材定义表名等值收窄
                predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
            }
            if (StringUtils.isNotEmpty(query.getKeyword())) {
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
                // B.2.1：库存明细关键字对冗余列精确全等置顶（定义 id 命中无法在本表做全等，仅作次级）
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.LOT_NUMBER),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
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
            // 重点药类型 / 药品性质：仅药品行且 item_id 落在预解析定义主键（耗材无该标识，直接排除）
            if (StringUtils.isNotEmpty(query.getKeyDrugType())
                || StringUtils.isNotEmpty(query.getMedicinePropertyCode())) {
                predicates.add(this.medicationDefinitionItemPredicate(
                    root, cb, query.getResolvedMedicationDefinitionIds()));
            }
            this.appendLowStockAggregateKeyPredicates(root, cb, predicates, query.getLowStockWarningAggregateKeys());
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 库存预警：按全院品种聚合拆零数量（各库房合计，含 quantity=0 的未删行；全院合计为 0 亦须出候选）。
     *
     * @return 聚合行列表
     */
    public List<InventoryProductQuantityAggregateModel> listHospitalWideProductQuantityAggregates() {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<InventoryItem> root = cq.from(InventoryItem.class);
        List<Predicate> predicates = new ArrayList<>();
        // 全院预警须覆盖断货：不排除 quantity=0 行，合计可为 0
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        cq.multiselect(
            root.get(FieldNameConstants.ITEM_TABLE),
            root.get(FieldNameConstants.ITEM_ID),
            cb.sum(root.get(FieldNameConstants.QUANTITY)));
        cq.where(predicates.toArray(new Predicate[0]));
        cq.groupBy(
            root.get(FieldNameConstants.ITEM_TABLE),
            root.get(FieldNameConstants.ITEM_ID));
        TypedQuery<Object[]> typedQuery = this.entityManager.createQuery(cq);
        List<Object[]> rows = typedQuery.getResultList();
        List<InventoryProductQuantityAggregateModel> result = new ArrayList<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 3) {
                continue;
            }
            InventoryProductQuantityAggregateModel model = new InventoryProductQuantityAggregateModel();
            model.setItemTable((String) row[0]);
            model.setItemId((Long) row[1]);
            BigDecimal total = (BigDecimal) row[2];
            if (total == null) {
                model.setTotalSplitQuantity(BigDecimal.ZERO);
            } else {
                model.setTotalSplitQuantity(total);
            }
            result.add(model);
        }
        return result;
    }

    /**
     * 效期预警批号行计数：与库存明细近效期筛选同口径。
     *
     * @param query 须含 {@code effectiveWarehouseIds} 与 {@code expireWithinDays}
     * @return 命中行数
     */
    public long countExpireWarningInventoryRows(WarehouseInventoryDetailPageQuery query) {
        Specification<InventoryItem> specification = this.buildWarehouseInventoryDetailSpecification(query);
        return this.inventoryItemRepository.count(specification);
    }

    /**
     * 订货快捷：按品种取在库批（quantity&gt;0）中<strong>最晚效期</strong>一批，若该批剩余天数 ≤ 阈值则返回。
     *
     * <p>仅本聚合 {@code wkf_inventory_item} Criteria（B.6.4）；{@code expiration_date} 为 null 的批不参与最晚竞选。
     * 与 {@link #countExpireWarningInventoryRows} / 明细「任意近效批」口径不同（AS-INV-WHS-001 BR-WHS-PO-014）。</p>
     *
     * @param expireWithinDays 剩余天数阈值（含）；&lt;0 返回空列表
     * @param itemTable        可选物品表收窄；空表示药品+耗材均参与
     * @return 命中品种行（含最晚效期时刻）；永不为 null
     */
    public List<InventoryProductLatestExpiryNearModel> listProductKeysWhoseLatestLotNearExpiry(
        int expireWithinDays,
        String itemTable) {
        // 非法阈值不产生快捷订货候选，避免误扫全表
        if (expireWithinDays < 0) {
            return List.of();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<InventoryItem> root = cq.from(InventoryItem.class);
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        // 仅账面量 > 0 的批参与「最晚一批」竞选
        predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.EXPIRATION_DATE)));
        // 表头药/耗收窄：可选 item_table
        if (StringUtils.isNotEmpty(itemTable)) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
        }
        // 本表 GROUP BY：品种键 + 最晚效期（禁止 JOIN 他聚合，B.6.4）
        cq.multiselect(
            root.get(FieldNameConstants.ITEM_TABLE),
            root.get(FieldNameConstants.ITEM_ID),
            cb.greatest(root.<LocalDateTime>get(FieldNameConstants.EXPIRATION_DATE)));
        cq.where(predicates.toArray(new Predicate[0]));
        cq.groupBy(
            root.get(FieldNameConstants.ITEM_TABLE),
            root.get(FieldNameConstants.ITEM_ID));
        TypedQuery<Object[]> typedQuery = this.entityManager.createQuery(cq);
        List<Object[]> rows = typedQuery.getResultList();
        LocalDate today = LocalDate.now();
        List<InventoryProductLatestExpiryNearModel> result = new ArrayList<>();
        for (Object[] row : rows) {
            // 投影列残缺则跳过，避免 ClassCast/NPE
            if (row == null || row.length < 3) {
                continue;
            }
            String table = (String) row[0];
            Long itemId = (Long) row[1];
            LocalDateTime latestExpiration = (LocalDateTime) row[2];
            // 无表名/主键/效期则跳过
            if (StringUtils.isEmpty(table) || itemId == null || latestExpiration == null) {
                continue;
            }
            long remainingDays = ChronoUnit.DAYS.between(today, latestExpiration.toLocalDate());
            // 剩余天数 ≤ 阈值（含已过期负数）→ 最晚一批已近效；未近效则不入结果
            if (remainingDays > expireWithinDays) {
                continue;
            }
            InventoryProductLatestExpiryNearModel model = new InventoryProductLatestExpiryNearModel();
            model.setItemTable(table.trim());
            model.setItemId(itemId);
            model.setLatestExpirationDate(latestExpiration);
            result.add(model);
        }
        return result;
    }

    /**
     * 低库存聚合键 OR 谓词：无键或键无效时构造恒 false（id=-1）。
     *
     * @param root       InventoryItem 根
     * @param cb         CriteriaBuilder
     * @param predicates 待追加谓词列表
     * @param keys       品种聚合键
     */
    private void appendLowStockAggregateKeyPredicates(
        Root<InventoryItem> root,
        CriteriaBuilder cb,
        List<jakarta.persistence.criteria.Predicate> predicates,
        List<InventoryProductAggregateKey> keys) {
        if (keys == null) {
            return;
        }
        // 无命中键时强制空结果，避免误返回全表
        if (keys.isEmpty()) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ID), -1L));
            return;
        }
        List<jakarta.persistence.criteria.Predicate> keyOr = new ArrayList<>();
        for (InventoryProductAggregateKey key : keys) {
            if (key == null || key.getItemId() == null || StringUtils.isEmpty(key.getItemTable())) {
                continue;
            }
            // 全院品种命中：展示该品种在各库房的全部批号行（列表仍受 BR-016 库房 Gate 约束）
            keyOr.add(cb.and(
                cb.equal(root.get(FieldNameConstants.ITEM_TABLE), key.getItemTable().trim()),
                cb.equal(root.get(FieldNameConstants.ITEM_ID), key.getItemId())));
        }
        if (keyOr.isEmpty()) {
            // 有效键均被过滤时等同无命中
            predicates.add(cb.equal(root.get(FieldNameConstants.ID), -1L));
            return;
        }
        predicates.add(cb.or(keyOr.toArray(new jakarta.persistence.criteria.Predicate[0])));
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
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
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
     * 按五元组匹配既有可扣减库存项主键：
     * {@code location_id + item_table + item_id + lot_number + supplier_id}（批号为持久化字面值）；
     * 仅返回正常供应、未删除且未锁定的首条。
     *
     * <p>{@code supplierId} 为空时：数量已清零的历史行不参与择行；同批仍有多条有量行时按 FEFO 取首条，不再拒绝自动定位。
     * 整笔扣减不得只依赖本方法，库房出库须走 {@code allocateAndDecrease} 拆分。</p>
     *
     * @param key 匹配键
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
        return this.findFirstActiveUnlockedIdByInventoryMatch(
            key.getSupplierId(), key.getLocationId(), key.getItemTable(), key.getItemId(), lot);
    }

    /**
     * 内部：五元组（供应商非空）或同批号 FEFO 首条有量行，定位可参与增减的库存主键（ACTIVE、未删除、未锁定）。
     *
     * @param supplierId 供应商；可空（空则同批多行按 FEFO 择首条有量行，数量为 0 的历史行不参与）
     * @param locationId 库位
     * @param itemTable  物品表
     * @param itemId     物品主键
     * @param lotNumber  批号字面值
     * @return 命中主键
     */
    private Optional<Long> findFirstActiveUnlockedIdByInventoryMatch(Long supplierId, Long locationId, String itemTable,
        Long itemId, String lotNumber) {
        if (locationId == null || itemId == null || StringUtils.isEmpty(itemTable)) {
            return Optional.empty();
        }
        String lot;
        if (lotNumber == null) {
            lot = "";
        } else {
            lot = lotNumber;
        }
        List<InventoryItem> rows;
        // 供应商非空：五元组精确候选
        if (supplierId != null) {
            rows = inventoryItemRepository.findBySupplierIdAndLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(
                supplierId, lot, locationId, itemTable.trim(), itemId);
        } else {
            rows = inventoryItemRepository.findByLotNumberAndLocationIdAndItemTableAndItemIdOrderByIdAsc(lot,
                locationId, itemTable.trim(), itemId);
        }
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
        List<InventoryItem> selectable = unlocked;
        // 未指定供应商时，退货清零后仍 ACTIVE 的历史行不参与择行，避免挡住同批仍有量的账面
        if (supplierId == null && unlocked.size() > 1) {
            List<InventoryItem> positiveRows = new ArrayList<>();
            for (InventoryItem row : unlocked) {
                if (row.getQuantity() != null && row.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    positiveRows.add(row);
                }
            }
            if (!positiveRows.isEmpty()) {
                selectable = positiveRows;
            }
        }
        InventoryItem first = selectable.get(0);
        // 同批多行：与发药一致按效期、创建时间择首条，不再因多供应商拒绝定位
        if (selectable.size() > 1) {
            first = this.pickFefoFirstInventoryRow(selectable);
        }
        if (first.getId() == null) {
            return Optional.empty();
        }
        return Optional.of(first.getId());
    }

    /**
     * 门诊发药前置库存断言：库存账面数量一律为<strong>最小单位（拆零单位）</strong>；
     * 先将发放行业务单位数量按目录换算比转为最小单位，再与候选批次可用量之和比较。
     *
     * <p>若指定批号则仅累计匹配批次；批号为空则累计全部可用批次。不足时文案带药品名与批号。</p>
     *
     * @param itemTableName 物品主表名（如
     *            {@link com.zxplat.domain.share.constants.TableNameConstants#MED_MEDICATION_DEFINITION}）
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 发放库房库位主键
     * @param lotNumber 批号；可空表示不按批号收窄
     * @param requiredQuantityInBusinessUnit 发放行业务单位下的需求数量
     * @param businessUnitCode 与发放行一致的单位编码（通常同 {@code MedicationDispense#getUnitCode()}）
     * @param productDefinition 药品产品定义读模型，用于单位换算
     * @param productDisplayName 药品展示名；用于不足提示定位具体品种，可空则回落为「药品」
     * @throws BusinessException
     *             参数不完整、无法换算或可用量不足（{@link com.zxplat.domain.share.constants.ErrorCodeConstants#EX_OPP_205_STOCK_OR_LOCATION_INVALID}）
     */
    public void assertSufficientForMedicationDispense(String itemTableName, Long medicationId, Long pharmacyLocationId,
        String lotNumber, BigDecimal requiredQuantityInBusinessUnit, String businessUnitCode,
        MedicationProductUnitConversionSource productDefinition, String productDisplayName) {
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少药品或库房标识");
        }
        if (requiredQuantityInBusinessUnit == null || requiredQuantityInBusinessUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言需求数量必须大于 0");
        }
        if (StringUtils.isEmpty(itemTableName)) {
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
            if (StringUtils.isNotEmpty(lotNumber)) {
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
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                buildMedicationDispenseInsufficientStockMessage(productDisplayName, lotNumber));
        }
    }

    /**
     * 护士换耗材房/自动扣库前：断言目标库房下<strong>签发批号</strong>可用量足够。
     *
     * <p>禁止跨批 FIFO 顶替。{@code alreadyReservedInSameBatch} 为本批内同键（库房+品规+批号）
     * 已累计待扣量，避免多行各自过闸后合计超库存。</p>
     *
     * @param itemTableName               物品主表名（如 {@code adm_device_definition}）
     * @param deviceDefinitionId          耗材定义主键
     * @param locationId                  目标耗材房库位主键
     * @param lotNumber                   签发批号（必填）
     * @param requiredQuantity            本行需求数量（与库存账面同单位）
     * @param alreadyReservedInSameBatch  本批已累计待扣量；可空视为 0
     * @param productDisplayName          耗材展示名；不足提示点名用，可空则回落「耗材」
     * @throws BusinessException 参数缺失、无批次或库存不足时
     */
    public void assertSufficientForDeviceDispenseAtLocationLot(
        String itemTableName,
        Long deviceDefinitionId,
        Long locationId,
        String lotNumber,
        BigDecimal requiredQuantity,
        BigDecimal alreadyReservedInSameBatch,
        String productDisplayName) {
        // Gate：定位库存须齐备库房、品规、批号
        if (StringUtils.isEmpty(itemTableName) || deviceDefinitionId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "库存断言缺少耗材或库房标识");
        }
        if (StringUtils.isEmpty(lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "发放批号缺失，请医生改签后再执行");
        }
        if (requiredQuantity == null || requiredQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.DEVICE_DISPENSE_INVALID_ARGUMENT, "库存断言需求数量必须大于 0");
        }
        // Pre：本批同键已预占量，空视为 0
        BigDecimal reserved = alreadyReservedInSameBatch;
        if (reserved == null) {
            reserved = BigDecimal.ZERO;
        }
        BigDecimal needTotal = requiredQuantity.add(reserved);
        List<AvailableInventoryModel> rows =
            this.findAvailableInventories(itemTableName.trim(), deviceDefinitionId, locationId);
        BigDecimal sum = BigDecimal.ZERO;
        String lotTrim = lotNumber.trim();
        for (AvailableInventoryModel row : rows) {
            if (row == null || row.getQuantity() == null) {
                continue;
            }
            String rowLot = row.getLotNumber();
            // 分支：只累计签发批号，禁止用其它批号顶替
            if (rowLot == null || !lotTrim.equals(rowLot.trim())) {
                continue;
            }
            sum = sum.add(row.getQuantity());
        }
        // Gate：目标房该批可用量须覆盖本批累计需求；不足时点名耗材与批号
        if (sum.compareTo(needTotal) < 0) {
            throw new BusinessException(
                ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                buildDeviceDispenseInsufficientStockMessage(productDisplayName, lotTrim));
        }
    }

    /**
     * 组装耗材发放库存不足提示：点名品种与批号，便于护士对照侧栏耗材明细。
     *
     * @param productDisplayName 耗材展示名；空则回落「耗材」
     * @param lotNumber          签发批号；可空
     * @return 面向用户的不足文案
     */
    private static String buildDeviceDispenseInsufficientStockMessage(String productDisplayName, String lotNumber) {
        String displayName = productDisplayName;
        if (StringUtils.isEmpty(displayName)) {
            displayName = "耗材";
        }
        if (StringUtils.isNotEmpty(lotNumber)) {
            return "【" + displayName + "】批号" + lotNumber.trim() + "可用库存不足，无法执行发放";
        }
        return "【" + displayName + "】可用库存不足，无法执行发放";
    }

    /**
     * 发药前库存充足断言（物理库存 − 已汇总的待发预占，GAP-EXE-003）。
     *
     * <p>{@code lotNumber} 非空时本方法仅累计该批物理量；调用方传入的 {@code reserveInSplitUnit}
     * <strong>必须</strong>已按同批号收窄（见 {@code MedicationDispenseQueryService#sumDispenseExeReserveQuantityInSplitUnit}
     * 的 {@code lotNumber} 参数），禁止跨批预占扣减本批物理量。</p>
     *
     * @param itemTableName                  物品主表名
     * @param medicationId                   药品定义主键
     * @param pharmacyLocationId             发放库房库位主键
     * @param lotNumber                      批号；可空表示不按批收窄物理量
     * @param requiredQuantityInBusinessUnit 发放行业务单位需求量
     * @param businessUnitCode               与发放行一致的单位编码
     * @param productDefinition              单位换算源
     * @param reserveInSplitUnit             待发预占量（最小单位）；须与 {@code lotNumber} 同口径
     * @param productDisplayName             药品展示名；用于不足提示定位具体品种，可空则回落为「药品」
     */
    public void assertSufficientForMedicationDispenseGivenReserve(
        String itemTableName,
        Long medicationId,
        Long pharmacyLocationId,
        String lotNumber,
        BigDecimal requiredQuantityInBusinessUnit,
        String businessUnitCode,
        MedicationProductUnitConversionSource productDefinition,
        BigDecimal reserveInSplitUnit,
        String productDisplayName) {
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少药品或库房标识");
        }
        if (requiredQuantityInBusinessUnit == null || requiredQuantityInBusinessUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言需求数量必须大于 0");
        }
        if (StringUtils.isEmpty(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "库存断言缺少物品主表名");
        }
        // Pre：预占空视为 0，避免 NPE
        BigDecimal safeReserve = reserveInSplitUnit;
        if (safeReserve == null) {
            safeReserve = BigDecimal.ZERO;
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
            // 有批号时只累计匹配批次，与预占同批口径对齐
            if (StringUtils.isNotEmpty(lotNumber)) {
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
        // Gate：净可用 = 物理(批) − 预占(同批)；不足则 EX-OPP-205
        BigDecimal netAvailable = physicalSum.subtract(safeReserve);
        if (netAvailable.compareTo(requiredInSplitUnit) < 0) {
            throw new BusinessException(ErrorCodeConstants.EX_OPP_205_STOCK_OR_LOCATION_INVALID,
                buildMedicationDispenseInsufficientStockMessage(productDisplayName, lotNumber));
        }
    }

    /**
     * 组装发药库存不足提示：点名品种，有批号时一并带上，便于药房定位。
     *
     * @param productDisplayName 药品展示名；空则回落「药品」
     * @param lotNumber          批号；可空
     * @return 面向用户的不足文案
     */
    private static String buildMedicationDispenseInsufficientStockMessage(String productDisplayName, String lotNumber) {
        String displayName = productDisplayName;
        if (StringUtils.isEmpty(displayName)) {
            displayName = "药品";
        }
        if (StringUtils.isNotEmpty(lotNumber)) {
            return "【" + displayName + "】批号" + lotNumber.trim() + "可用库存不足以覆盖待发数量";
        }
        return "【" + displayName + "】可用库存不足以覆盖待发数量";
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
     * 定位「药品 + 库房 + 批号」下 FEFO 首条未删库存项主键（退药回补等须锚定单行场景）。
     *
     * <p>同批号多供应商并存时按 FEFO 择首条；<strong>发药实物扣减不再经本方法整笔砸单行</strong>，
     * 须走 {@code InventoryItemDomainService#allocateAndDecrease} 并传入批号做同批拆分扣。
     * 批号价仍按「品+批号」取价，不因供应商拆价。</p>
     *
     * @param itemTableName 物品主表名
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 库房库位主键
     * @param lotNumber 批号（非空且 trim 后用于等值匹配）
     * @return 库存项主键
     * @throws BusinessException 参数非法或未命中
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
     * 解析药品在指定库房+批号下的库存账面行；同批号多行时 FEFO 择首条（与发药扣减定位规则一致）。
     *
     * @param itemTableName 物品主表名
     * @param medicationId 药品定义主键
     * @param pharmacyLocationId 库房库位主键
     * @param lotNumber 批号
     * @return 非空实体
     * @throws BusinessException 参数非法或未命中
     */
    private InventoryItem resolveSingleActiveMedicationInventoryBatchEntity(String itemTableName, Long medicationId,
        Long pharmacyLocationId, String lotNumber) {
        if (StringUtils.isEmpty(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "定位库存项缺少物品主表名");
        }
        if (medicationId == null || pharmacyLocationId == null) {
            throw new BusinessException(ErrorCodeConstants.MEDICATION_DISPENSE_INVALID_ARGUMENT, "定位库存项缺少药品或库房标识");
        }
        if (StringUtils.isEmpty(lotNumber)) {
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
        return this.pickFefoFirstInventoryRow(activeRows);
    }

    /**
     * 同批号多账面行时按 FEFO 择首条：效期升序（空效期靠后）→ 创建时间升序 → id 升序。
     *
     * @param activeRows 未删候选；调用方保证非空
     * @return FEFO 首条
     */
    private InventoryItem pickFefoFirstInventoryRow(List<InventoryItem> activeRows) {
        InventoryItem best = activeRows.get(0);
        for (int i = 1; i < activeRows.size(); i++) {
            InventoryItem candidate = activeRows.get(i);
            if (candidate == null) {
                continue;
            }
            if (this.compareInventoryRowFefo(candidate, best) < 0) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * FEFO 比较：效期早者优先；同效期创建早者优先；再比主键。
     *
     * @param left  左行
     * @param right 右行
     * @return 负值表示 left 更先出
     */
    private int compareInventoryRowFefo(InventoryItem left, InventoryItem right) {
        LocalDateTime leftExp = left.getExpirationDate();
        LocalDateTime rightExp = right.getExpirationDate();
        if (leftExp == null && rightExp != null) {
            return 1;
        }
        if (leftExp != null && rightExp == null) {
            return -1;
        }
        if (leftExp != null && rightExp != null) {
            int byExp = leftExp.compareTo(rightExp);
            if (byExp != 0) {
                return byExp;
            }
        }
        LocalDateTime leftCreated = left.getCreatedAt();
        LocalDateTime rightCreated = right.getCreatedAt();
        if (leftCreated == null && rightCreated != null) {
            return 1;
        }
        if (leftCreated != null && rightCreated == null) {
            return -1;
        }
        if (leftCreated != null && rightCreated != null) {
            int byCreated = leftCreated.compareTo(rightCreated);
            if (byCreated != 0) {
                return byCreated;
            }
        }
        Long leftId = left.getId();
        Long rightId = right.getId();
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return 1;
        }
        if (rightId == null) {
            return -1;
        }
        return leftId.compareTo(rightId);
    }

    /**
     * 定位耗材发放将要扣减的<strong>单条</strong>库存项主键（物品表 + 耗材定义主键 + 库位 + 批号）。
     *
     * <p>
     * 规则与 {@link #requireInventoryItemIdForMedicationDispenseBatch} 同构：排除逻辑删除；
     * 同批号多供应商时 FEFO 择首条。
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

    /**
     * 按批号+库房+耗材定义定位未删库存批次；同批号多行时 FEFO 择首条。
     *
     * @param itemTableName       物品表名
     * @param deviceDefinitionId  耗材定义 id
     * @param locationId          库房 id
     * @param lotNumber           批号
     * @return FEFO 首条库存行
     * @throws BusinessException 参数缺失或无批次
     */
    private InventoryItem resolveSingleActiveDeviceInventoryBatchEntity(String itemTableName, Long deviceDefinitionId,
        Long locationId, String lotNumber) {
        if (StringUtils.isEmpty(itemTableName)) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "定位耗材库存项缺少物品主表名");
        }
        if (deviceDefinitionId == null || locationId == null) {
            throw new BusinessException(ErrorCodeConstants.INVENTORY_ITEM_INVALID_ARGUMENT, "定位耗材库存项缺少耗材定义或库房标识");
        }
        if (StringUtils.isEmpty(lotNumber)) {
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
        return this.pickFefoFirstInventoryRow(activeRows);
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
        if (StringUtils.isEmpty(itemTable) || itemId == null) {
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

    /**
     * 全院按「物品表 + 物品主键 + 批号」汇总未删库存行数量（含各库位/供应商；quantity 可为 0）。
     *
     * <p>仅本聚合 {@code wkf_inventory_item} Criteria（B.6.4）；供进货/期初同批号异价门禁判断是否仍有账面量（BR-WHS-IN-018）。</p>
     *
     * @param itemTable  物品物理表名
     * @param itemId     物品定义主键
     * @param lotNumber  批号字面值（trim 后等值匹配）
     * @return 数量合计；参数不全或无行时为 {@link BigDecimal#ZERO}
     */
    public BigDecimal sumQuantityByItemTableAndItemIdAndLotNumber(
        String itemTable, Long itemId, String lotNumber) {
        // Gate：键不全无法汇总，视为无库存
        if (StringUtils.isEmpty(itemTable) || itemId == null || StringUtils.isEmpty(lotNumber)) {
            return BigDecimal.ZERO;
        }
        String lotTrim = lotNumber.trim();
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<InventoryItem> root = cq.from(InventoryItem.class);
        List<Predicate> predicates = new ArrayList<>();
        // 未软删行全院合计（不按库位/供应商收窄）
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), itemTable.trim()));
        predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), itemId));
        predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), lotTrim));
        cq.select(cb.sum(root.get(FieldNameConstants.QUANTITY)));
        cq.where(predicates.toArray(new Predicate[0]));
        TypedQuery<BigDecimal> typedQuery = this.entityManager.createQuery(cq);
        BigDecimal total = typedQuery.getSingleResult();
        // 无行时 JPA sum 可能为 null
        if (total == null) {
            return BigDecimal.ZERO;
        }
        return total;
    }
}
