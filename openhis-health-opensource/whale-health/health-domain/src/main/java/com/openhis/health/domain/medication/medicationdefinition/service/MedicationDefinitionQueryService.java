/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationdefinition.service;

import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.queries.MedicationDefinitionCatalogPageQuery;
import com.openhis.health.domain.medication.medicationdefinition.queries.model.*;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.MedicationCatalogImportConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.KeyDrugType;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.IdInQueryChunkSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.inventoryitem.entity.InventoryItem;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 药品定义主数据读侧查询服务。
 *
 * <p>职责：目录分页、详情、导入导出占位与可编辑性门控；查询条件下推至 {@link Specification} 与分页 API，不写库。</p>
 *
 * <p>聚合边界：仅本聚合 {@link MedicationDefinitionRepository} 持久化；
 * 目录 {@code positiveQuantityOnly} 仅用 Criteria EXISTS 判定 {@code wkf_inventory_item} 是否存在可用正库存行，
 * 可选按 {@code positiveQuantityLocationIds} 收窄库位（医生站候选发药库）；
 * <strong>不</strong>注入库存 Repository（读侧存在性过滤，保证 limit/offset 前过滤）。</p>
 */
@Service
@RequiredArgsConstructor
public class MedicationDefinitionQueryService {

    private final MedicationDefinitionRepository medicationDefinitionRepository;


    /**
     * 按主键加载药品定义实体（adapter 读路径）；不存在时 empty。
     */
    public Optional<MedicationDefinition> findOptionalMedicationDefinitionEntityById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return medicationDefinitionRepository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按动态条件分页查询药品目录列表行。
     *
     * @param specification JPA 动态条件（由上层组装）
     * @param pageable 分页与排序
     * @return 分页列表模型
     */
    public Page<MedicationDefinitionPageQueryModel> pageMedicationCatalog(
        Specification<MedicationDefinition> specification, Pageable pageable) {
        return medicationDefinitionRepository.findAll(specification, pageable).map(this::toPageModel);
    }

    /**
     * 药品目录维护页分页（读主表，谓词对齐原 {@code ProductDefinitionRMQueryService#pageMedicationCatalog} 药品分支）。
     *
     * @param query    分页与筛选；{@code null} 时仅未删除
     * @param pageable 分页与排序
     * @return 分页行模型
     */
    public Page<MedicationDefinitionPageQueryModel> pageMedicationCatalog(
        MedicationDefinitionCatalogPageQuery query, Pageable pageable) {
        Specification<MedicationDefinition> specification = this.buildCatalogPageSpecification(query);
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = this.unsortPageableIfKeyword(
            query != null && StringUtils.isNotEmpty(query.getKeyword()), pageable);
        return medicationDefinitionRepository.findAll(specification, effective).map(this::toPageModel);
    }

    /**
     * 采购/进货选品分页（读 {@code med_medication_definition}，谓词对齐原产品读模型采购分支）。
     */
    public Page<MedicationDefinition> pageForPurchase(ProductDefinitionPurchasePageQuery query, Pageable pageable) {
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = this.unsortPageableIfKeyword(
            query != null && StringUtils.isNotEmpty(query.getKeyword()), pageable);
        return medicationDefinitionRepository.findAll(this.buildPurchasePageSpecification(query), effective);
    }

    /**
     * 医嘱开立目录分页（读药品主表；谓词与 {@link #pageForPurchase} 一致）。
     */
    public Page<MedicationDefinition> pageForCatalog(ProductDefinitionCatalogPageQuery query, Pageable pageable) {
        return this.pageForPurchase(this.toPurchaseQuery(query), pageable);
    }

    /**
     * 医嘱开立目录分页查询 → 采购选品查询入参（字段一一对齐）。
     *
     * @param query 目录分页查询
     * @return 采购分页查询（谓词与 {@link #pageForPurchase} 共用）
     */
    private ProductDefinitionPurchasePageQuery toPurchaseQuery(ProductDefinitionCatalogPageQuery query) {
        ProductDefinitionPurchasePageQuery purchase = new ProductDefinitionPurchasePageQuery();
        if (query == null) {
            return purchase;
        }
        purchase.setCategoryCode(query.getCategoryCode());
        purchase.setExcludeMedicationCategoryCodes(query.getExcludeMedicationCategoryCodes());
        purchase.setActiveFlag(query.getActiveFlag());
        purchase.setSupplierId(query.getSupplierId());
        purchase.setManufacturerId(query.getManufacturerId());
        purchase.setManufacturerIds(query.getManufacturerIds());
        purchase.setManufacturer(query.getManufacturer());
        purchase.setKeyword(query.getKeyword());
        purchase.setPrepickedProductKeys(query.getPrepickedProductKeys());
        purchase.setPositiveQuantityOnly(query.getPositiveQuantityOnly());
        // 目录「只看库存>0」库位收窄与采购 Spec 共用 EXISTS
        purchase.setPositiveQuantityLocationIds(query.getPositiveQuantityLocationIds());
        purchase.setPageNo(query.getPageNo());
        purchase.setPageSize(query.getPageSize());
        purchase.setSortField(query.getSortField());
        purchase.setSortOrder(query.getSortOrder());
        return purchase;
    }

    /**
     * 按主键加载药品定义全量详情；不存在则阻断。
     *
     * @param id 药品定义主键
     * @return 详情读模型
     * @throws BusinessException 记录不存在
     */
    public MedicationDefinitionDetailQueryModel getMedicationCatalog(Long id) {
        MedicationDefinition definition = medicationDefinitionRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.MEDICATION_DEFINITION_NOT_FOUND, "药品定义不存在"));
        return this.toDetailModel(definition);
    }

    /**
     * 药品维护页初始化：选项字典由应用层注入，此处仅返回空集合占位，避免 NPE。
     *
     * @param orgId 机构标识（预留，当前未参与查询）
     * @return 初始化模型
     */
    public MedicationDefinitionInitQueryModel getMedicationInit(Long orgId) {
        MedicationDefinitionInitQueryModel model = new MedicationDefinitionInitQueryModel();
        model.setCategoryOptions(new ArrayList<OptionDto>());
        model.setDoseFormOptions(new ArrayList<OptionDto>());
        model.setUnitOptions(new ArrayList<OptionDto>());
        model.setFrequencyOptions(new ArrayList<OptionDto>());
        return model;
    }

    /**
     * 生成药品目录导入模板的元数据与列头字节（内容由常量拼接，非真实文件系统读）。
     *
     * @return 模板描述与二进制内容
     */
    public MedicationDefinitionTemplateQueryModel downloadMedicationCatalog() {
        MedicationDefinitionTemplateQueryModel model = new MedicationDefinitionTemplateQueryModel();
        model.setTemplateName("medicationcatalog");
        model.setTemplateVersion("v1");
        model.setContent("busNo,name,categoryCode,purchaseUnitCode,doseFormCode,methodCode,rateCode"
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return model;
    }

    /**
     * 门控：主键为空或记录已废弃时不可进入编辑流。
     *
     * @param id 药品定义主键
     * @return 允许编辑为 true
     */
    public boolean validateMedicationCatalog(Long id) {
        if (id == null) {
            return false;
        }
        return medicationDefinitionRepository.findById(id)
            .map(definition -> !PublicationStatus.DISCARDED.getCode().equals(definition.getStatusEnum()))
            .orElse(false);
    }

    /**
     * 按药品定义主键解析目录类型码（药品申请 FIFO 签发等读侧用）。
     *
     * @param medicationDefinitionId 药品定义主键
     * @return 类型码；未命中时 {@code null}
     */
    public String resolveCategoryCodeByMedicationDefinitionId(Long medicationDefinitionId) {
        if (medicationDefinitionId == null) {
            return null;
        }
        return medicationDefinitionRepository.findById(medicationDefinitionId)
            .map(MedicationDefinition::getCategoryCode)
            .orElse(null);
    }

    /**
     * 导出当前租户下药品简行（兼容旧调用；内部按导出上限截断）。
     *
     * <p>完整模板列导出由应用层 {@code exportMedicationCatalog} 基于
     * {@link #listMedicationCatalogForExport} 编排；本方法仅保留窄字段投影。</p>
     *
     * @return 导出行列表（最多 {@link MedicationCatalogImportConstants#EXPORT_MAX_ROWS} 条）
     */
    public List<MedicationDefinitionExportRowQueryModel> exportMedicationCatalog() {
        List<MedicationDefinitionDetailQueryModel> details =
            this.listMedicationCatalogForExport(null, MedicationCatalogImportConstants.EXPORT_MAX_ROWS);
        List<MedicationDefinitionExportRowQueryModel> rows = new ArrayList<>();
        // Loop：窄投影，避免旧调用方依赖完整详情图
        for (MedicationDefinitionDetailQueryModel detail : details) {
            MedicationDefinitionExportRowQueryModel row = new MedicationDefinitionExportRowQueryModel();
            row.setBusNo(detail.getBusNo());
            row.setName(detail.getName());
            row.setCategoryCode(detail.getCategoryCode());
            row.setStatusEnum(detail.getStatusEnum());
            row.setManufacturerText(detail.getManufacturerText());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 按维护列表同口径筛选加载药品详情行，供应用层按导入模板列导出（只读；本聚合 Repository）。
     *
     * @param query   分类/关键字；{@code null} 表示仅未删除
     * @param maxRows 导出行上限（含）；小于 1 时按 1 保护
     * @return 详情模型列表（关键字非空时精确优先排序，否则按 busNo 倒序）
     */
    public List<MedicationDefinitionDetailQueryModel> listMedicationCatalogForExport(
        MedicationDefinitionCatalogPageQuery query,
        int maxRows
    ) {
        // Gate：导出行上限至少为 1，避免 PageRequest 非法
        int limit = maxRows;
        if (limit < 1) {
            limit = 1;
        }
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        Pageable pageable = PageRequest.of(0, limit, sort);
        Specification<MedicationDefinition> specification = this.buildCatalogPageSpecification(query);
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = this.unsortPageableIfKeyword(
            query != null && StringUtils.isNotEmpty(query.getKeyword()), pageable);
        Page<MedicationDefinition> page = medicationDefinitionRepository.findAll(specification, effective);
        List<MedicationDefinitionDetailQueryModel> rows = new ArrayList<>();
        for (MedicationDefinition definition : page.getContent()) {
            rows.add(this.toDetailModel(definition));
        }
        return rows;
    }

    /**
     * 列表行映射：目录检索常用列。
     *
     * @param definition 实体
     * @return 分页行模型
     */
    private MedicationDefinitionPageQueryModel toPageModel(MedicationDefinition definition) {
        MedicationDefinitionPageQueryModel model = new MedicationDefinitionPageQueryModel();
        model.setId(definition.getId());
        model.setBusNo(definition.getBusNo());
        model.setName(definition.getName());
        model.setCategoryCode(definition.getCategoryCode());
        model.setStatusEnum(definition.getStatusEnum());
        model.setActiveFlag(definition.getActiveFlag());
        model.setYbMatchFlag(definition.getYbMatchFlag());
        model.setYbNo(definition.getYbNo());
        model.setTotalVolume(definition.getTotalVolume());
        model.setMinUnit(definition.getMinUnit());
        model.setMaxUnit(definition.getMaxUnit());
        model.setLocationId(definition.getLocationId());
        model.setSupplyId(definition.getSupplyId());
        model.setManufacturerId(definition.getManufacturerId());
        // 展示名由 AppService 按 manufacturerId 解析供应商名称回填，此处不读已删除的 text 列
        model.setManufacturerText(null);
        model.setRxFlag(definition.getRxFlag());
        model.setPurchaseUnitCode(definition.getPurchaseUnitCode());
        model.setSplitUnitCode(definition.getSplitUnitCode());
        model.setPackageUnitCode(definition.getPackageUnitCode());
        model.setStockUnitCode(definition.getStockUnitCode());
        model.setDoseUnitCode(definition.getDoseUnitCode());
        model.setPurchaseUnitRatio(definition.getPurchaseUnitRatio());
        model.setStockUnitRatio(definition.getStockUnitRatio());
        model.setPackageUnitRatio(definition.getPackageUnitRatio());
        model.setDoseUnitRatio(definition.getDoseUnitRatio());
        model.setTraceUnitCode(definition.getTraceUnitCode());
        model.setTraceUnitRatio(definition.getTraceUnitRatio());
        return model;
    }

    /**
     * 药品目录维护分页谓词：未删除 + 分类 + 重点药类型 + 关键字（名称/助记码/业务号）。
     *
     * @param query 目录查询条件
     * @return JPA Specification
     */
    private Specification<MedicationDefinition> buildCatalogPageSpecification(MedicationDefinitionCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                // 重点药类型（四旗）：null/空不限
                Predicate keyDrugPredicate =
                    this.buildKeyDrugTypePredicate(root, cb, query.getKeyDrugType());
                if (keyDrugPredicate != null) {
                    predicates.add(keyDrugPredicate);
                }
                // 药品性质（麻/精）：与重点药类型分字段；同时非空时 AND
                Predicate medicinePropertyPredicate =
                    this.buildKeyDrugTypePredicate(root, cb, query.getMedicinePropertyCode());
                if (medicinePropertyPredicate != null) {
                    predicates.add(medicinePropertyPredicate);
                }
                String normalizedStatus = this.normalizeKeyDrugStatusFilter(query.getStatusEnum());
                if (normalizedStatus != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), normalizedStatus));
                }
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc)
                    ));
                    // B.2.1：精确全等（名称/商品名/助记/业务号）置顶，其后模糊；次级按创建时间倒序
                    List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT)));
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        secondary,
                        root.get(FieldNameConstants.NAME),
                        root.get(FieldNameConstants.MERCHANDISE_NAME),
                        root.get(FieldNameConstants.PY_STR),
                        root.get(FieldNameConstants.WB_STR),
                        root.get(FieldNameConstants.MERCHANDISE_PY_STR),
                        root.get(FieldNameConstants.MERCHANDISE_WB_STR),
                        root.get(FieldNameConstants.BUS_NO));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 采购/开立选品分页谓词：未删除、分类、启用、供应商、排除分类、关键字与预选 OR 组合。
     *
     * @param query 采购分页查询
     * @return JPA Specification
     */
    private Specification<MedicationDefinition> buildPurchasePageSpecification(ProductDefinitionPurchasePageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                // 开立西药医嘱排除中草药等目录分类（IF-DOR-11 excludeMedicationCategoryCodes）
                if (query.getExcludeMedicationCategoryCodes() != null && !query.getExcludeMedicationCategoryCodes().isEmpty()) {
                    List<String> excluded = query.getExcludeMedicationCategoryCodes().stream()
                        .filter(StringUtils::isNotEmpty)
                        .map(String::trim)
                        .distinct()
                        .toList();
                    if (!excluded.isEmpty()) {
                        predicates.add(cb.not(root.get(FieldNameConstants.CATEGORY_CODE).in(excluded)));
                    }
                }
                if (query.getActiveFlag() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ACTIVE_FLAG), query.getActiveFlag()));
                }
                if (query.getSupplierId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.SUPPLY_ID), query.getSupplierId()));
                }
                this.appendManufacturerIdPredicates(predicates, root, cb, query.getManufacturerId(),
                    query.getManufacturerIds(), query.getManufacturer());
                Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildMedicationPrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(predicates, cb, keywordPred, prepPred);
                // 医生站选品「只看库存>0」：须在分页 limit/offset 前过滤，禁止前端筛当前页
                if (Boolean.TRUE.equals(query.getPositiveQuantityOnly())) {
                    predicates.add(this.buildPositiveAvailableInventoryExistsPredicate(
                        root, cq, cb, TableNameConstants.MED_MEDICATION_DEFINITION, query.getPositiveQuantityLocationIds()));
                }
                // B.2.1：有关键字时精确命中置顶（与 LIKE 列同集合）；次级创建时间倒序
                this.applyPurchaseKeywordExactFirstOrder(root, cq, cb, query.getKeyword());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 药品定义存在「可用正库存」行：ACTIVE、quantity&gt;0、未过期、未删除。
     *
     * <p>可选按库位集合收窄（医生站候选发药库位，与目录可开立库存展示对齐）；
     * {@code locationIds == null} 时不按库位收窄（采购/进货全院账面口径）；
     * 非 null 且空列表时恒不命中（无可发药库位）。</p>
     *
     * @param root         药品定义根
     * @param cq           外层查询（挂 Subquery）
     * @param cb           CriteriaBuilder
     * @param itemTable    库存 item_table（药品定义表名）
     * @param locationIds  可选库位收窄；null=不收窄
     * @return EXISTS 谓词；库位列表为空时返回恒假
     */
    private Predicate buildPositiveAvailableInventoryExistsPredicate(
        Root<MedicationDefinition> root,
        CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        String itemTable,
        List<Long> locationIds
    ) {
        // 医生站已解析候选库位但结果为空：无可开立库存维度，药定义一律不命中
        if (locationIds != null && locationIds.isEmpty()) {
            return cb.disjunction();
        }
        Subquery<Long> sub = cq.subquery(Long.class);
        Root<InventoryItem> inv = sub.from(InventoryItem.class);
        sub.select(inv.get(FieldNameConstants.ID));
        LocalDateTime now = LocalDateTime.now();
        List<Predicate> where = new ArrayList<>();
        where.add(cb.isFalse(inv.get(FieldNameConstants.IS_DELETED)));
        where.add(cb.equal(inv.get(FieldNameConstants.ITEM_TABLE), itemTable));
        where.add(cb.equal(inv.get(FieldNameConstants.ITEM_ID), root.get(FieldNameConstants.ID)));
        where.add(cb.equal(inv.get(FieldNameConstants.INVENTORY_STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
        where.add(cb.greaterThan(inv.get(FieldNameConstants.QUANTITY), BigDecimal.ZERO));
        where.add(cb.or(
            cb.isNull(inv.get(FieldNameConstants.EXPIRATION_DATE)),
            cb.greaterThan(inv.get(FieldNameConstants.EXPIRATION_DATE), now)));
        // 有候选库位时与目录展示同口径收窄，避免药库有量、药房为 0 仍被「只看库存>0」放行
        if (locationIds != null) {
            where.add(inv.get(FieldNameConstants.LOCATION_ID).in(locationIds));
        }
        sub.where(where.toArray(Predicate[]::new));
        return cb.exists(sub);
    }

    /**
     * 采购关键字 OR 谓词：名称、商品名、拼音五笔、业务号不区分大小写模糊匹配。
     *
     * @param root    药品定义根
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空时返回 null
     * @return 关键字谓词或 null
     */
    private Predicate buildPurchaseKeywordPredicate(Root<MedicationDefinition> root, CriteriaBuilder cb, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        // 纯拼音/五笔字母输入：只扫检索码列，减轻名称/商品名宽 OR LIKE（数据量大时更明显）
        if (SearchCodeUtil.isAsciiLetterKeyword(trimmed)) {
            return cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc)
            );
        }
        return cb.or(
            cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc),
            cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc)
        );
    }

    /**
     * 采购/开立选品关键字：SQL 层「精确全等在前、模糊在后」（B.2.1）。
     *
     * <p>纯字母关键字仅对助记码列做精确判定，与 {@link #buildPurchaseKeywordPredicate} 扫列一致。</p>
     *
     * @param root    药品定义根
     * @param cq      CriteriaQuery（count 查询由工具内部跳过 orderBy）
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空则不写排序
     */
    private void applyPurchaseKeywordExactFirstOrder(
        Root<MedicationDefinition> root,
        jakarta.persistence.criteria.CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return;
        }
        String trimmed = keyword.trim();
        List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT)));
        // 纯拼音/五笔：精确列与 LIKE 列对齐，避免对未参与检索的名称列误判精确
        if (SearchCodeUtil.isAsciiLetterKeyword(trimmed)) {
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                cq,
                cb,
                trimmed,
                secondary,
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR),
                root.get(FieldNameConstants.MERCHANDISE_PY_STR),
                root.get(FieldNameConstants.MERCHANDISE_WB_STR));
            return;
        }
        KeywordExactFirstOrderSupport.applyExactFirstOrder(
            cq,
            cb,
            trimmed,
            secondary,
            root.get(FieldNameConstants.NAME),
            root.get(FieldNameConstants.MERCHANDISE_NAME),
            root.get(FieldNameConstants.PY_STR),
            root.get(FieldNameConstants.WB_STR),
            root.get(FieldNameConstants.MERCHANDISE_PY_STR),
            root.get(FieldNameConstants.MERCHANDISE_WB_STR),
            root.get(FieldNameConstants.BUS_NO));
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
     * 预选药品主键 OR 谓词：仅匹配 {@code med_medication_definition} 表行的 productDefinitionId。
     *
     * @param root 药品定义根
     * @param cb   CriteriaBuilder
     * @param keys 批量查找键；空时返回 null
     * @return 预选 OR 谓词或 null
     */
    private Predicate buildMedicationPrepickedPredicate(
        Root<MedicationDefinition> root,
        CriteriaBuilder cb,
        List<ProductDefinitionBatchLookup> keys
    ) {
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        List<Predicate> parts = new ArrayList<>();
        for (ProductDefinitionBatchLookup lookup : keys) {
            if (lookup == null || lookup.getProductDefinitionId() == null) {
                continue;
            }
            if (StringUtils.isNotEmpty(lookup.getProductTableName())
                && !TableNameConstants.MED_MEDICATION_DEFINITION.equals(lookup.getProductTableName().trim())) {
                continue;
            }
            parts.add(cb.equal(root.get(FieldNameConstants.ID), lookup.getProductDefinitionId()));
        }
        if (parts.isEmpty()) {
            return null;
        }
        return cb.or(parts.toArray(Predicate[]::new));
    }

    /**
     * 将关键字谓词与预选谓词合并进 AND 列表：二者皆有则 OR，否则追加非空者。
     *
     * @param predicates   累积 AND 谓词列表
     * @param cb           CriteriaBuilder
     * @param keywordPred  关键字谓词
     * @param prepPred     预选谓词
     */
    private void appendKeywordOrPrepicked(
        List<Predicate> predicates,
        CriteriaBuilder cb,
        Predicate keywordPred,
        Predicate prepPred
    ) {
        if (keywordPred != null && prepPred != null) {
            predicates.add(cb.or(prepPred, keywordPred));
            return;
        }
        if (keywordPred != null) {
            predicates.add(keywordPred);
            return;
        }
        if (prepPred != null) {
            predicates.add(prepPred);
        }
    }

    /**
     * 详情映射：含单位换算、医保对码与用药限制等维护字段。
     *
     * @param definition 实体
     * @return 详情模型
     */
    private MedicationDefinitionDetailQueryModel toDetailModel(MedicationDefinition definition) {
        MedicationDefinitionDetailQueryModel model = new MedicationDefinitionDetailQueryModel();
        model.setId(definition.getId());
        model.setBusNo(definition.getBusNo());
        model.setName(definition.getName());
        model.setYbNo(definition.getYbNo());
        model.setDoseFormCode(definition.getDoseFormCode());
        model.setDosageInstruction(definition.getDosageInstruction());
        model.setStatusEnum(definition.getStatusEnum());
        model.setDomainEnum(definition.getDomainEnum());
        model.setApprovalNumber(definition.getApprovalNumber());
        model.setBasicFlag(definition.getBasicFlag());
        model.setNationalMonitorFlag(definition.getNationalMonitorFlag());
        model.setCentralPurchaseFlag(definition.getCentralPurchaseFlag());
        model.setSkinTestFlag(definition.getSkinTestFlag());
        model.setInjectFlag(definition.getInjectFlag());
        model.setChildrenFlag(definition.getChildrenFlag());
        model.setSelfFlag(definition.getSelfFlag());
        model.setAntibioticFlag(definition.getAntibioticFlag());
        model.setAntibioticCode(definition.getAntibioticCode());
        model.setMinRateCode(definition.getMinRateCode());
        model.setMaxRateCode(definition.getMaxRateCode());
        model.setItemMaxQuantity(definition.getItemMaxQuantity());
        model.setItemMinQuantity(definition.getItemMinQuantity());
        model.setUsageLimit(definition.getUsageLimit());
        model.setYbMatchFlag(definition.getYbMatchFlag());
        model.setNationalDrugCode(definition.getNationalDrugCode());
        model.setDose(definition.getDose());
        model.setMinUnit(definition.getMinUnit());
        model.setMaxUnit(definition.getMaxUnit());
        model.setRxFlag(definition.getRxFlag());
        model.setTotalVolume(definition.getTotalVolume());
        model.setOrgId(definition.getOrgId());
        model.setLocationId(definition.getLocationId());
        model.setSupplyId(definition.getSupplyId());
        model.setManufacturerId(definition.getManufacturerId());
        // 展示名由 AppService 按 manufacturerId 解析供应商名称回填，此处不读已删除的 text 列
        model.setManufacturerText(null);
        model.setPyStr(definition.getPyStr());
        model.setWbStr(definition.getWbStr());
        model.setMerchandiseName(definition.getMerchandiseName());
        model.setMerchandisePyStr(definition.getMerchandisePyStr());
        model.setMerchandiseWbStr(definition.getMerchandiseWbStr());
        model.setCategoryCode(definition.getCategoryCode());
        model.setPurchaseUnitCode(definition.getPurchaseUnitCode());
        model.setSplitUnitCode(definition.getSplitUnitCode());
        model.setPackageUnitCode(definition.getPackageUnitCode());
        model.setStockUnitCode(definition.getStockUnitCode());
        model.setDoseUnitCode(definition.getDoseUnitCode());
        model.setMethodCode(definition.getMethodCode());
        model.setRateCode(definition.getRateCode());
        model.setDoseUnitRatio(definition.getDoseUnitRatio());
        model.setPurchaseUnitRatio(definition.getPurchaseUnitRatio());
        model.setStockUnitRatio(definition.getStockUnitRatio());
        model.setPackageUnitRatio(definition.getPackageUnitRatio());
        model.setTraceUnitCode(definition.getTraceUnitCode());
        model.setTraceUnitRatio(definition.getTraceUnitRatio());
        model.setDefinition(definition.getDefinition());
        model.setDddCode(definition.getDddCode());
        model.setDddUnitCode(definition.getDddUnitCode());
        model.setIngredientItem(definition.getIngredientItem());
        model.setActiveFlag(definition.getActiveFlag());
        model.setPartAttributeEnum(definition.getPartAttributeEnum());
        model.setThoPartAttributeEnum(definition.getThoPartAttributeEnum());
        model.setRestrictedEnum(definition.getRestrictedEnum());
        model.setChrgitmLv(definition.getChrgitmLv());
        model.setMrSettlementTypeCode(definition.getMrSettlementTypeCode());
        // 结余留用批次号：详情回显术语 code
        model.setSurplusRetentionBatchCode(definition.getSurplusRetentionBatchCode());
        model.setVersion(definition.getVersion());
        model.setRestrictedFlag(definition.getRestrictedFlag());
        model.setRestrictedScope(definition.getRestrictedScope());
        model.setCharacteristic(definition.getCharacteristic());
        model.setPharmacologyCategoryCode(definition.getPharmacologyCategoryCode());
        model.setDrug69Code(definition.getDrug69Code());
        return model;
    }

    /**
     * 按主键集合批量加载药品定义（医嘱列表预热等场景）。
     *
     * <p>主键过多时按 {@link com.openhis.health.domain.share.constants.GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批。</p>
     *
     * @param ids 主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<MedicationDefinition> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return IdInQueryChunkSupport.loadByIdInChunks(
            ids,
            chunkIds -> {
                List<MedicationDefinition> rows = medicationDefinitionRepository.findAllById(chunkIds);
                if (rows == null) {
                    return Collections.emptyList();
                }
                return rows;
            });
    }

    /**
     * 按机构与业务编码批量加载药品定义（外部目录同步幂等 upsert 前置查询）。
     *
     * @param orgId   机构主键
     * @param busNos  业务编码列表
     * @return 命中实体列表；入参无效时返回空列表
     */
    public List<MedicationDefinition> findByOrgIdAndBusNos(Long orgId, List<String> busNos) {
        if (orgId == null || busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        return medicationDefinitionRepository.findByOrgIdInAndBusNoIn(List.of(orgId), busNos);
    }

    /**
     * 按业务编码批量加载药品定义（库存期初 Excel 等场景；与器材 {@code findByBusNos} 对称）。
     *
     * <p>不按机构过滤；同一编码多条时由调用方判歧义。</p>
     *
     * @param busNos 业务编码列表
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<MedicationDefinition> findByBusNos(List<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        return medicationDefinitionRepository.findByBusNoIn(busNos);
    }

    /**
     * 按药品类型编码批量解析主键（库存明细筛选，禁止 inventory 包内 Subquery 药品主表）。
     *
     * @param categoryCodes 类型编码集合
     * @return 未删除且 category_code 命中的主键列表
     */
    public List<Long> listIdsByCategoryCodesIn(Collection<String> categoryCodes) {
        if (categoryCodes == null || categoryCodes.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            root.get("categoryCode").in(categoryCodes));
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按医保等级批量解析主键（库存明细 chrgitm_lv 筛选）。
     *
     * @param chrgitmLvList 医保等级集合
     * @return 未删除且等级命中的主键列表
     */
    public List<Long> listIdsByChrgitmLvIn(Collection<Integer> chrgitmLvList) {
        if (chrgitmLvList == null || chrgitmLvList.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            root.get("chrgitmLv").in(chrgitmLvList));
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按库存明细关键字解析药品定义主键（编码、商品名、拼音/五笔等，与采购选品口径一致）。
     *
     * @param keyword 检索词；空白时返回空列表
     * @return 未删除且命中关键字的药品定义主键列表
     */
    public List<Long> listIdsByInventoryDetailKeyword(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return Collections.emptyList();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> ands = new ArrayList<>();
            ands.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, keyword);
            if (keywordPred != null) {
                ands.add(keywordPred);
            }
            return cb.and(ands.toArray(Predicate[]::new));
        };
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 批量查询库内已存在的药品唯一键 token（同租户、未删除、未废弃）。
     *
     * <p>token 口径：有院内旧编码时为 {@code name + \\u0001 + manufacturer + \\u0001 + L:legacy}；
     * 否则为 {@code name + \\u0001 + manufacturer + \\u0001 + V:normalizedVolume}。</p>
     *
     * @param tokens 待比对 token 集合
     * @return 库内已存在的 token 子集；入参无效时返回空集
     */
    public Set<String> findExistingDuplicateKeyTokens(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> tokenSet = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (String token : tokens) {
            if (StringUtils.isEmpty(token)) {
                continue;
            }
            tokenSet.add(token.trim());
            String[] parts = token.split("\u0001", -1);
            if (parts.length > 0 && StringUtils.isNotEmpty(parts[0])) {
                names.add(parts[0].trim());
            }
        }
        if (tokenSet.isEmpty() || names.isEmpty()) {
            return Collections.emptySet();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM),
                PublicationStatus.DISCARDED.getCode()));
            predicates.add(root.get(FieldNameConstants.NAME).in(names));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<MedicationDefinition> candidates = medicationDefinitionRepository.findAll(specification);
        Set<String> existing = new HashSet<>();
        for (MedicationDefinition row : candidates) {
            if (row == null) {
                continue;
            }
            String candidateToken = this.buildDuplicateKeyToken(
                row.getName(),
                row.getTotalVolume(),
                row.getManufacturerId(),
                this.extractLegacyCode(row.getDefinition()));
            if (tokenSet.contains(candidateToken)) {
                existing.add(candidateToken);
            }
        }
        return existing;
    }

    /**
     * 兼容旧签名：忽略 orgId，按同租户口径比对。
     *
     * @param orgId  已废弃，保留仅为兼容调用方
     * @param tokens 待比对 token
     * @return 库内已存在的 token 子集
     */
    public Set<String> findExistingDuplicateKeyTokens(Long orgId, Collection<String> tokens) {
        return this.findExistingDuplicateKeyTokens(tokens);
    }

    /**
     * 构造与导入校验一致的唯一键 token（厂家段为 manufacturerId 字符串，空 id 用空串）。
     *
     * @param name           药品名称
     * @param totalVolume    规格
     * @param manufacturerId 生产厂家主键；null 时厂家段为空串
     * @param legacyCode     旧系统编码；非空时走 L: 分支
     * @return token 字符串
     */
    private String buildDuplicateKeyToken(
        String name,
        String totalVolume,
        Long manufacturerId,
        String legacyCode
    ) {
        String n = this.trimToEmpty(name);
        String m = this.manufacturerIdToken(manufacturerId);
        String legacy = this.trimToEmpty(legacyCode);
        // 有旧系统编码时按名称+厂家+L:legacy 去重，忽略规格
        if (!legacy.isEmpty()) {
            return n + '\u0001' + m + '\u0001' + "L:" + legacy;
        }
        return n + '\u0001' + m + '\u0001' + "V:" + this.normalizeVolume(this.trimToEmpty(totalVolume));
    }

    /**
     * 将生产厂家主键转为重复键中的厂家段（null→空串，否则 {@code String.valueOf}）。
     *
     * @param manufacturerId 生产厂家主键
     * @return 非 null 字符串
     */
    private String manufacturerIdToken(Long manufacturerId) {
        if (manufacturerId == null) {
            return "";
        }
        return String.valueOf(manufacturerId);
    }

    /**
     * 追加生产厂家筛选谓词：优先 {@code manufacturerId}/{@code manufacturerIds}；
     * 兼容旧 {@code manufacturer} 字符串——仅当可解析为 Long 时按主键等值过滤，否则忽略
     * （名称→id 须由 AppService 解析后写入 manufacturerId）。
     *
     * @param predicates       外层 AND 列表（就地追加）
     * @param root             药品定义根
     * @param cb               CriteriaBuilder
     * @param manufacturerId   单值主键；可空
     * @param manufacturerIds  主键集合；可空
     * @param manufacturerText 兼容旧字符串入参；可空
     */
    private void appendManufacturerIdPredicates(
        List<Predicate> predicates,
        Root<MedicationDefinition> root,
        CriteriaBuilder cb,
        Long manufacturerId,
        List<Long> manufacturerIds,
        String manufacturerText
    ) {
        List<Long> ids = new ArrayList<>();
        if (manufacturerId != null) {
            ids.add(manufacturerId);
        }
        if (manufacturerIds != null) {
            for (Long id : manufacturerIds) {
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        // 旧入参仅传名称串：仅数字可当 id；名称解析留 AppService
        if (ids.isEmpty() && StringUtils.isNotEmpty(manufacturerText)) {
            Long parsed = this.tryParseLongId(manufacturerText.trim());
            if (parsed != null) {
                ids.add(parsed);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        if (ids.size() == 1) {
            predicates.add(cb.equal(root.get(FieldNameConstants.MANUFACTURER_ID), ids.get(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.MANUFACTURER_ID).in(ids));
    }

    /**
     * 尝试将纯数字字符串解析为 Long；非数字或溢出时返回 null（不抛异常）。
     *
     * @param raw 已 trim 的候选串
     * @return 解析成功的主键，或 null
     */
    private Long tryParseLongId(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return null;
        }
        for (int i = 0; i < raw.length(); i++) {
            if (!Character.isDigit(raw.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String extractLegacyCode(String definition) {
        if (StringUtils.isEmpty(definition)) {
            return null;
        }
        String prefix = MedicationCatalogImportConstants.LEGACY_CODE_DEFINITION_PREFIX;
        for (String line : definition.split("\\R")) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length()).trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }

    private String trimToEmpty(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private String normalizeVolume(String totalVolume) {
        if (totalVolume == null || totalVolume.isEmpty()) {
            return "";
        }
        return totalVolume.replace('：', ':').replace('．', '.');
    }

    /**
     * 按注射/输液目录标志解析药品定义主键（AS-OP-PHA-005 方级含输液筛预解析）。
     *
     * @param injectFlag {@code true}/{@code false}；{@code null} 时返回空列表
     * @return 未删除且 {@code inject_flag} 等于入参的目录主键列表
     */
    public List<Long> listIdsByInjectFlag(Boolean injectFlag) {
        if (injectFlag == null) {
            return Collections.emptyList();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.INJECT_FLAG), injectFlag));
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        // Loop：收集命中注射标志的目录主键，供应用层写入处方查询 resolved 字段
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按主键批量解析重点药标识（AS-RPT-011 性能路径：仅窗内出现的 productId，禁止全库扫目录）。
     *
     * <p>投影含四旗布尔与药品性质码，供基药/监控/集采/抗生素/麻/精一/精二分桶。</p>
     *
     * @param ids 药典主键集合；空返回空 Map
     * @return id → 重点药投影；已软删行不写入
     */
    public Map<Long, MedicationKeyDrugFlagQueryModel> mapKeyDrugFlagsByIds(Collection<Long> ids) {
        Map<Long, MedicationKeyDrugFlagQueryModel> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<MedicationDefinition> rows = new ArrayList<>(IdInQueryChunkSupport.loadByIdInChunks(
            ids,
            chunkIds -> this.medicationDefinitionRepository.findAllById(chunkIds)));
        // Loop：仅未删除行写入标识，供报表按类型分桶
        for (MedicationDefinition row : rows) {
            if (row == null || row.getId() == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            MedicationKeyDrugFlagQueryModel flags = new MedicationKeyDrugFlagQueryModel();
            flags.setId(row.getId());
            flags.setBasicFlag(row.getBasicFlag());
            flags.setNationalMonitorFlag(row.getNationalMonitorFlag());
            flags.setCentralPurchaseFlag(row.getCentralPurchaseFlag());
            flags.setAntibioticFlag(row.getAntibioticFlag());
            flags.setPharmacologyCategoryCode(row.getPharmacologyCategoryCode());
            result.put(row.getId(), flags);
        }
        return result;
    }

    /**
     * 按重点药类型解析药品定义主键（库存明细筛选预解析，B.6.4 禁止 inventory 跨包 JOIN）。
     *
     * <p>布尔旗类型筛对应标识为 true；麻/精一/精二筛 {@code pharmacology_category_code} 等值。
     * 未知/空 code 返回空列表。不按启用/停用收窄。</p>
     *
     * @param keyDrugTypeCode {@link KeyDrugType#getCode()}；空时返回空列表
     * @return 命中的目录主键列表
     */
    public List<Long> listIdsByKeyDrugType(String keyDrugTypeCode) {
        return this.listIdsByKeyDrugType(keyDrugTypeCode, null);
    }

    /**
     * 按重点药类型 + 可选目录状态解析药品定义主键（AS-RPT-011 启用/停用筛选）。
     *
     * <p>布尔旗类型筛对应标识为 true；麻/精一/精二筛药品性质码；{@code statusEnum} 为空则不按状态收窄；
     * 仅认 {@link PublicationStatus#ACTIVE}/{@link PublicationStatus#INACTIVE}。</p>
     *
     * @param keyDrugTypeCode {@link KeyDrugType#getCode()}；空时返回空列表
     * @param statusEnum      目录状态 code；空/未知则不按状态过滤
     * @return 命中的目录主键列表
     */
    public List<Long> listIdsByKeyDrugType(String keyDrugTypeCode, String statusEnum) {
        // Gate：空/未知类型无命中，禁止全表扫
        if (StringUtils.isEmpty(keyDrugTypeCode)) {
            return Collections.emptyList();
        }
        KeyDrugType keyDrugType = EnumUtil.fromCode(KeyDrugType.class, keyDrugTypeCode.trim());
        if (keyDrugType == null) {
            return Collections.emptyList();
        }
        String normalizedStatus = this.normalizeKeyDrugStatusFilter(statusEnum);
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            Predicate keyDrugPredicate = this.buildKeyDrugTypePredicate(root, cb, keyDrugType.getCode());
            // 无法解析的类型码：恒假，避免误返回全表
            if (keyDrugPredicate == null) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(keyDrugPredicate);
            }
            // 启用/停用筛选：有合法状态码时收窄 status_enum
            if (normalizedStatus != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), normalizedStatus));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        // Loop：收集命中重点药类型的目录主键，供库存明细 / 重点药品报表写入 resolved 字段
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按结余留用批次号精确匹配解析药品定义主键（AS-RPT-015）。
     *
     * <p>仅本聚合 Repository；未删除且 {@code surplusRetentionBatchCode} 等值。</p>
     *
     * @param surplusRetentionBatchCode 结余留用批次号；空/空白返回空列表
     * @return 命中的目录主键列表
     */
    public List<Long> listIdsBySurplusRetentionBatchCode(String surplusRetentionBatchCode) {
        String batchCode = StringUtils.trimToNull(surplusRetentionBatchCode);
        // Gate：批次号空白则无命中，禁止全表扫描
        if (batchCode == null) {
            return Collections.emptyList();
        }
        Specification<MedicationDefinition> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.SURPLUS_RETENTION_BATCH_CODE), batchCode));
        List<MedicationDefinition> rows = this.medicationDefinitionRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        // Loop：收集等值批次码命中的目录主键，供结余留用报表收窄申请/发放
        for (MedicationDefinition row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 规范化重点药品报表的目录状态筛选：仅 ACTIVE/INACTIVE；其余视为不筛选。
     *
     * @param statusEnum 请求状态码
     * @return ACTIVE/INACTIVE；无法识别时 null
     */
    private String normalizeKeyDrugStatusFilter(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return null;
        }
        String trimmed = statusEnum.trim();
        if (PublicationStatus.ACTIVE.getCode().equals(trimmed)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (PublicationStatus.INACTIVE.getCode().equals(trimmed)) {
            return PublicationStatus.INACTIVE.getCode();
        }
        // 未知状态码：不收窄，避免误滤成空目录
        return null;
    }

    /**
     * 按重点药类型组装目录谓词：布尔旗等值 true，或药品性质码等值；空/未知返回 null（调用方不追加）。
     *
     * @param root            药典 root
     * @param cb              CriteriaBuilder
     * @param keyDrugTypeCode {@link KeyDrugType#getCode()}
     * @return 类型谓词；无法解析时 null
     */
    private Predicate buildKeyDrugTypePredicate(
        Root<MedicationDefinition> root,
        CriteriaBuilder cb,
        String keyDrugTypeCode) {
        if (StringUtils.isEmpty(keyDrugTypeCode)) {
            return null;
        }
        KeyDrugType type = EnumUtil.fromCode(KeyDrugType.class, keyDrugTypeCode.trim());
        if (type == null) {
            return null;
        }
        // 麻/精一/精二：药品性质码等值（术语 medicine_properties）
        String medicinePropertyCode = this.resolveMedicinePropertyCode(type);
        if (medicinePropertyCode != null) {
            return cb.equal(root.get(FieldNameConstants.PHARMACOLOGY_CATEGORY_CODE), medicinePropertyCode);
        }
        String flagField = this.resolveKeyDrugFlagFieldName(type);
        if (flagField == null) {
            return null;
        }
        return cb.equal(root.get(flagField), Boolean.TRUE);
    }

    /**
     * 将重点药类型映射为药典布尔字段属性名；麻/精与未知返回 null。
     *
     * @param type 重点药类型枚举
     * @return {@link FieldNameConstants} 中的 flag 属性名；非布尔旗类型 null
     */
    private String resolveKeyDrugFlagFieldName(KeyDrugType type) {
        if (type == null) {
            return null;
        }
        if (type == KeyDrugType.BASIC) {
            return FieldNameConstants.BASIC_FLAG;
        }
        if (type == KeyDrugType.NATIONAL_MONITOR) {
            return FieldNameConstants.NATIONAL_MONITOR_FLAG;
        }
        if (type == KeyDrugType.CENTRAL_PURCHASE) {
            return FieldNameConstants.CENTRAL_PURCHASE_FLAG;
        }
        // 抗生素与基药等同构：筛 antibiotic_flag = true
        if (type == KeyDrugType.ANTIBIOTIC) {
            return FieldNameConstants.ANTIBIOTIC_FLAG;
        }
        return null;
    }

    /**
     * 将麻/精重点药类型映射为术语 medicine_properties 码；非性质类返回 null。
     *
     * @param type 重点药类型
     * @return 性质码；非麻/精类型 null
     */
    private String resolveMedicinePropertyCode(KeyDrugType type) {
        if (type == KeyDrugType.NARCOTIC) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_NARCOTIC;
        }
        if (type == KeyDrugType.PSYCH_ONE) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_PSYCH_ONE;
        }
        if (type == KeyDrugType.PSYCH_TWO) {
            return PrescriptionSplitConstants.MEDICINE_PROPERTY_PSYCH_TWO;
        }
        return null;
    }

}

