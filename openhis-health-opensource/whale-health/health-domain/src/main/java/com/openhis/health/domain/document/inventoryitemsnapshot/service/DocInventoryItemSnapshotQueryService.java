/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.document.inventoryitemsnapshot.service;

import com.openhis.health.domain.document.inventoryitemsnapshot.entity.DocInventoryItemSnapshotEntity;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.DocInventoryItemSnapshotPageQuery;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotBusNoOptionQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.document.inventoryitemsnapshot.repository.DocInventoryItemSnapshotRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.InventoryDetailSnapshotBusNoSupport;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 库存明细快照读侧查询服务（DM-DOC-001）。
 *
 * <p>聚合边界：仅本聚合 {@link DocInventoryItemSnapshotRepository}；跨聚合读由应用层编排（B.6.4）。</p>
 * <p>备份读四价直读本表冻结列，禁止联结 ChargeItemDefinition 重算（BR-DOC-SNAP-008）。</p>
 * <p>与 {@code InventoryItemQueryService} 同包惯例：读写在 {@code service} 包；{@code queries} 仅放入参/出参模型。</p>
 */
@Service
@RequiredArgsConstructor
public class DocInventoryItemSnapshotQueryService {

    private final DocInventoryItemSnapshotRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 判定租户在自然日是否已有 ABU 前缀的成功快照批次（IF-10 Pre · BR-DOC-SNAP-007 / AS BR-INV-DTL-020）。
     *
     * @param tenantId  租户
     * @param localDate 自然日
     * @return 存在未删行时为 true；参数非法时为 false
     */
    public boolean existsSuccessfulBackupForDate(Long tenantId, LocalDate localDate) {
        if (tenantId == null || localDate == null) {
            // 参数不完整时视为「无成功批次」，由应用层 Gate 保证入参
            return false;
        }
        String prefix = InventoryDetailSnapshotBusNoSupport.formatDayPrefix(localDate);
        return repository.existsByTenantIdAndBusNoStartingWithAndIsDeletedFalse(tenantId, prefix);
    }

    /**
     * 租户历史备份批次单号列表（IF-11 · {@code bus_no} DESC 去重）。
     *
     * @param tenantId 租户
     * @return 单号列表；租户为空时空列表
     */
    public List<String> findDistinctBusNoByTenant(Long tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        // 构建查询条件：租户 + 软删过滤 → DISTINCT bus_no 降序
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<DocInventoryItemSnapshotEntity> root = cq.from(DocInventoryItemSnapshotEntity.class);
        cq.select(root.get(FieldNameConstants.BUS_NO));
        cq.distinct(true);
        cq.where(
            cb.equal(root.get(FieldNameConstants.TENANT_ID), tenantId),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
        );
        cq.orderBy(cb.desc(root.get(FieldNameConstants.BUS_NO)));
        return this.entityManager.createQuery(cq).getResultList();
    }

    /**
     * 租户历史备份批次下拉（单号 + 抓拍时刻，IF-11 / 库房期初期末明细 IF-01）。
     *
     * @param tenantId 租户
     * @return 按 {@code bus_no} 降序；租户为空时空列表
     */
    public List<DocInventoryItemSnapshotBusNoOptionQueryModel> findDistinctBusNoOptionsByTenant(Long tenantId) {
        if (tenantId == null) {
            return Collections.emptyList();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<DocInventoryItemSnapshotEntity> root = cq.from(DocInventoryItemSnapshotEntity.class);
        cq.multiselect(
            root.get(FieldNameConstants.BUS_NO),
            root.get(FieldNameConstants.SNAPSHOT_TIME)
        );
        cq.distinct(true);
        cq.where(
            cb.equal(root.get(FieldNameConstants.TENANT_ID), tenantId),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
        );
        cq.orderBy(cb.desc(root.get(FieldNameConstants.BUS_NO)));
        List<Object[]> rows = this.entityManager.createQuery(cq).getResultList();
        List<DocInventoryItemSnapshotBusNoOptionQueryModel> models = new ArrayList<>();
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            String busNo = null;
            if (row[0] != null) {
                busNo = row[0].toString();
            }
            if (StringUtils.isEmpty(busNo)) {
                continue;
            }
            DocInventoryItemSnapshotBusNoOptionQueryModel model = new DocInventoryItemSnapshotBusNoOptionQueryModel();
            model.setBusNo(busNo.trim());
            if (row[1] instanceof LocalDateTime snapshotTime) {
                model.setSnapshotTime(snapshotTime);
            }
            models.add(model);
        }
        return models;
    }

    /**
     * 期初/期末快照：按租户、备份单号、库位 DISTINCT {@code item_table}（日结药/耗解析回落）。
     *
     * @param tenantId    租户
     * @param busNo       备份单号
     * @param warehouseId 库位
     * @return 表名列表
     */
    public List<String> findDistinctItemTablesByTenantBusNoAndWarehouse(
        Long tenantId,
        String busNo,
        Long warehouseId
    ) {
        if (tenantId == null || warehouseId == null || StringUtils.isEmpty(busNo)) {
            return Collections.emptyList();
        }
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<String> cq = cb.createQuery(String.class);
        Root<DocInventoryItemSnapshotEntity> root = cq.from(DocInventoryItemSnapshotEntity.class);
        cq.select(root.get(FieldNameConstants.ITEM_TABLE));
        cq.distinct(true);
        cq.where(
            cb.equal(root.get(FieldNameConstants.TENANT_ID), tenantId),
            cb.equal(root.get(FieldNameConstants.BUS_NO), busNo.trim()),
            cb.equal(root.get(FieldNameConstants.WAREHOUSE_ID), warehouseId),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
        );
        cq.orderBy(cb.asc(root.get(FieldNameConstants.ITEM_TABLE)));
        return this.entityManager.createQuery(cq).getResultList();
    }

    /**
     * 备份明细分页（IF-04 · TC-DOC-SNAP-PAGE-001）。
     *
     * @param query    筛选条件（{@code busNo} 必填由应用 Gate 保证）
     * @param pageable 分页与排序（列表默认 {@code pyStr ASC NULLS LAST} 由应用层传入）
     * @return 分页读模型
     */
    public Page<DocInventoryItemSnapshotRowQueryModel> pageSnapshotRows(
        DocInventoryItemSnapshotPageQuery query,
        Pageable pageable
    ) {
        // 构建查询条件（含软删、busNo 必填）→ 单次 SQL 分页
        Specification<DocInventoryItemSnapshotEntity> specification = this.buildSnapshotSpecification(query);
        Pageable effectivePageable = pageable;
        if (effectivePageable == null) {
            effectivePageable = PageRequest.of(0, GeneralSharedConstants.DEFAULT_QUERY_PAGE_SIZE);
        }
        // 关键字非空时剥掉 Sort，保留 Spec 精确优先 ORDER BY（B.2.1）
        boolean keywordPresent = query != null && StringUtils.isNotEmpty(query.getKeyword());
        effectivePageable = this.unsortPageableIfKeyword(keywordPresent, effectivePageable);
        return repository.findAll(specification, effectivePageable).map(this::toRowQueryModel);
    }

    /**
     * 备份汇总候选行（IF-05 · TC-DOC-SNAP-SUM-001）。
     *
     * @param query   筛选条件
     * @param maxRows 最大行数（AS 默认 30000）
     * @return 行集合
     */
    public List<DocInventoryItemSnapshotRowQueryModel> listSnapshotRowsForSum(
        DocInventoryItemSnapshotPageQuery query,
        int maxRows
    ) {
        return this.listSnapshotRowsTopN(query, maxRows, Sort.unsorted());
    }

    /**
     * 备份导出行（IF-07 · TC-DOC-SNAP-EXP-001）。
     *
     * @param query   筛选条件
     * @param maxRows 最大行数（AS 默认 100000）
     * @return 行集合
     */
    public List<DocInventoryItemSnapshotRowQueryModel> listSnapshotRowsForExport(
        DocInventoryItemSnapshotPageQuery query,
        int maxRows
    ) {
        return this.listSnapshotRowsTopN(query, maxRows, Sort.unsorted());
    }

    /**
     * 拉取至多 {@code maxRows} 行快照明细（汇总/导出共用谓词）。
     *
     * @param query   筛选条件
     * @param maxRows 上限
     * @param sort    排序
     * @return 读模型列表
     */
    private List<DocInventoryItemSnapshotRowQueryModel> listSnapshotRowsTopN(
        DocInventoryItemSnapshotPageQuery query,
        int maxRows,
        Sort sort
    ) {
        if (maxRows < 1) {
            return Collections.emptyList();
        }
        Specification<DocInventoryItemSnapshotEntity> specification = this.buildSnapshotSpecification(query);
        Sort effectiveSort = sort;
        if (effectiveSort == null) {
            effectiveSort = Sort.unsorted();
        }
        Pageable pageable = PageRequest.of(0, maxRows, effectiveSort);
        Page<DocInventoryItemSnapshotEntity> page = repository.findAll(specification, pageable);
        List<DocInventoryItemSnapshotRowQueryModel> models = new ArrayList<>();
        // Loop：逐行映射为读侧模型（四价直读表列）
        for (DocInventoryItemSnapshotEntity row : page.getContent()) {
            models.add(this.toRowQueryModel(row));
        }
        return models;
    }

    /**
     * 组装备份读侧 {@link Specification}：静表谓词下推 DB（关键字不含批号，AS BR-INV-DTL-010）。
     *
     * @param query 筛选入参
     * @return 规格
     */
    private Specification<DocInventoryItemSnapshotEntity> buildSnapshotSpecification(
        DocInventoryItemSnapshotPageQuery query
    ) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (query.getTenantId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
            }
            String busNo = query.getBusNo();
            if (StringUtils.isNotEmpty(busNo)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.BUS_NO), busNo.trim()));
            }
            if (query.getWarehouseIds() != null && !query.getWarehouseIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.WAREHOUSE_ID).in(query.getWarehouseIds()));
            }
            if (query.getSupplierIds() != null && !query.getSupplierIds().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.SUPPLIER_ID).in(query.getSupplierIds()));
            }
            if (query.getInventoryStatusEnums() != null && !query.getInventoryStatusEnums().isEmpty()) {
                predicates.add(
                    root.get(FieldNameConstants.INVENTORY_STATUS_ENUM).in(query.getInventoryStatusEnums())
                );
            }
            if (query.getZeroStockFlag() != null) {
                // 零库存筛选：true 仅零库存行，false 仅正库存行
                if (Boolean.TRUE.equals(query.getZeroStockFlag())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                } else {
                    predicates.add(cb.greaterThan(root.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
                }
            }
            if (query.getRemainingDaysLe() != null) {
                predicates.add(cb.isNotNull(root.get(FieldNameConstants.REMAINING_DAYS)));
                predicates.add(
                    cb.lessThanOrEqualTo(root.get(FieldNameConstants.REMAINING_DAYS), query.getRemainingDaysLe())
                );
            }
            if (StringUtils.isNotEmpty(query.getItemTable())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_TABLE), query.getItemTable().trim()));
            }
            if (query.getMedicationCategoryCodes() != null && !query.getMedicationCategoryCodes().isEmpty()) {
                predicates.add(
                    root.get(FieldNameConstants.MEDICATION_CATEGORY_CODE).in(query.getMedicationCategoryCodes())
                );
            }
            if (query.getDeviceCategoryCodes() != null && !query.getDeviceCategoryCodes().isEmpty()) {
                predicates.add(
                    root.get(FieldNameConstants.DEVICE_CATEGORY_CODE).in(query.getDeviceCategoryCodes())
                );
            }
            if (query.getChrgitmLvs() != null && !query.getChrgitmLvs().isEmpty()) {
                predicates.add(root.get(FieldNameConstants.CHRGITM_LV).in(query.getChrgitmLvs()));
            }
            // 重点药类型 / 药品性质：静表无药典标识列，按应用层预解析的药品定义主键收窄（耗材排除）
            if (StringUtils.isNotEmpty(query.getKeyDrugType())
                || StringUtils.isNotEmpty(query.getMedicinePropertyCode())) {
                List<Long> medIds = query.getResolvedMedicationDefinitionIds();
                if (medIds == null || medIds.isEmpty()) {
                    // 无命中定义时强制空结果，避免漏筛成全量
                    predicates.add(cb.equal(root.get(FieldNameConstants.ID), -1L));
                } else {
                    predicates.add(cb.and(
                        cb.equal(root.get(FieldNameConstants.ITEM_TABLE), TableNameConstants.MED_MEDICATION_DEFINITION),
                        root.get(FieldNameConstants.ITEM_ID).in(medIds)));
                }
            }
            if (StringUtils.isNotEmpty(query.getKeyword())) {
                String trimmed = query.getKeyword().trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                List<Predicate> keywordOr = new ArrayList<>();
                // 备份关键字：名称/拼音/五笔/编码/商品名/批号（与现库 IF-02 口径对齐）
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc));
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc));
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.PRODUCT_BUS_NO)), likeIc));
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc));
                keywordOr.add(cb.like(cb.lower(root.get(FieldNameConstants.LOT_NUMBER)), likeIc));
                predicates.add(cb.or(keywordOr.toArray(new Predicate[0])));
                // B.2.1：精确全等置顶，次级主键倒序
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR),
                    root.get(FieldNameConstants.PRODUCT_BUS_NO),
                    root.get(FieldNameConstants.MERCHANDISE_NAME),
                    root.get(FieldNameConstants.LOT_NUMBER));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 关键字非空且 Pageable 带 Sort 时剥掉 Sort，避免覆盖 Specification 精确优先 ORDER BY。
     *
     * @param keywordPresent 是否带非空关键字
     * @param pageable       原始分页
     * @return 关键字场景下的无 Sort Pageable；否则原 pageable
     */
    private Pageable unsortPageableIfKeyword(boolean keywordPresent, Pageable pageable) {
        if (!keywordPresent || pageable == null) {
            return pageable;
        }
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * 实体映射为读侧行模型（四价直读表列，BR-DOC-SNAP-008）。
     *
     * @param entity 快照行
     * @return 读模型
     */
    private DocInventoryItemSnapshotRowQueryModel toRowQueryModel(DocInventoryItemSnapshotEntity entity) {
        DocInventoryItemSnapshotRowQueryModel model = new DocInventoryItemSnapshotRowQueryModel();
        model.setId(entity.getId());
        model.setInventoryItemId(entity.getInventoryItemId());
        model.setBusNo(entity.getBusNo());
        model.setSnapshotTime(entity.getSnapshotTime());
        model.setItemTable(entity.getItemTable());
        model.setItemId(entity.getItemId());
        model.setChargeItemDefinitionId(entity.getChargeItemDefinitionId());
        model.setWarehouseId(entity.getWarehouseId());
        model.setSupplierId(entity.getSupplierId());
        model.setProductBusNo(entity.getProductBusNo());
        model.setName(entity.getName());
        model.setMerchandiseName(entity.getMerchandiseName());
        model.setTotalVolume(entity.getTotalVolume());
        model.setManufacturer(entity.getManufacturer());
        model.setApprovalNumber(entity.getApprovalNumber());
        model.setYbNo(entity.getYbNo());
        model.setTraceNo(entity.getTraceNo());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setLotNumber(entity.getLotNumber());
        model.setQuantity(entity.getQuantity());
        model.setPackageUnitRatio(entity.getPackageUnitRatio());
        model.setPackageUnitCode(entity.getPackageUnitCode());
        model.setLotNumberCostAmount(entity.getLotNumberCostAmount());
        model.setLotNumberPriceAmount(entity.getLotNumberPriceAmount());
        model.setLineCostTotal(entity.getLineCostTotal());
        model.setLinePriceTotal(entity.getLinePriceTotal());
        model.setProductionDate(entity.getProductionDate());
        model.setExpirationDate(entity.getExpirationDate());
        model.setRemainingDays(entity.getRemainingDays());
        model.setInventoryStatusEnum(entity.getInventoryStatusEnum());
        model.setChrgitmLv(entity.getChrgitmLv());
        model.setMedicationCategoryCode(entity.getMedicationCategoryCode());
        model.setDeviceCategoryCode(entity.getDeviceCategoryCode());
        model.setDoseFormCode(entity.getDoseFormCode());
        return model;
    }
}
