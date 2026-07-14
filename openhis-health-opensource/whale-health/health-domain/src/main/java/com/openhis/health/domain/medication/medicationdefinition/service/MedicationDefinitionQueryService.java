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
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.query.ProductDefinitionPurchasePageQuery;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 药品定义主数据读侧查询服务。
 *
 * <p>职责：目录分页、详情、导入导出占位与可编辑性门控；查询条件下推至 {@link Specification} 与分页 API，不写库。</p>
 *
 * <p>聚合边界：仅本聚合 {@link MedicationDefinitionRepository}。</p>
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
        return medicationDefinitionRepository.findAll(specification, pageable).map(this::toPageModel);
    }

    /**
     * 采购/进货选品分页（读 {@code med_medication_definition}，谓词对齐原产品读模型采购分支）。
     */
    public Page<MedicationDefinition> pageForPurchase(ProductDefinitionPurchasePageQuery query, Pageable pageable) {
        return medicationDefinitionRepository.findAll(this.buildPurchasePageSpecification(query), pageable);
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
        purchase.setManufacturer(query.getManufacturer());
        purchase.setKeyword(query.getKeyword());
        purchase.setPrepickedProductKeys(query.getPrepickedProductKeys());
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
     * 导出当前租户下全部药品简行（全量加载，规模受主数据量约束）。
     *
     * @return 导出行列表
     */
    public List<MedicationDefinitionExportRowQueryModel> exportMedicationCatalog() {
        List<MedicationDefinition> definitions = medicationDefinitionRepository.findAllBy();
        List<MedicationDefinitionExportRowQueryModel> rows = new ArrayList<>();
        // Loop：逐行扁平化导出字段，避免暴露完整聚合图
        for (MedicationDefinition definition : definitions) {
            MedicationDefinitionExportRowQueryModel row = new MedicationDefinitionExportRowQueryModel();
            row.setBusNo(definition.getBusNo());
            row.setName(definition.getName());
            row.setCategoryCode(definition.getCategoryCode());
            row.setStatusEnum(definition.getStatusEnum());
            row.setManufacturerText(definition.getManufacturerText());
            rows.add(row);
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
        model.setMaxUnit(definition.getMaxUnit());
        model.setLocationId(definition.getLocationId());
        model.setSupplyId(definition.getSupplyId());
        model.setManufacturerText(definition.getManufacturerText());
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
        return model;
    }

    /**
     * 药品目录维护分页谓词：未删除 + 分类 + 关键字（名称/助记码/业务号）。
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
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.MERCHANDISE_WB_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc)
                    ));
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
                if (StringUtils.isNotEmpty(query.getManufacturer())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.MANUFACTURER_TEXT), query.getManufacturer().trim()));
                }
                Predicate keywordPred = this.buildPurchaseKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildMedicationPrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(predicates, cb, keywordPred, prepPred);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
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
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keyword.trim());
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
        model.setMaxUnit(definition.getMaxUnit());
        model.setRxFlag(definition.getRxFlag());
        model.setTotalVolume(definition.getTotalVolume());
        model.setOrgId(definition.getOrgId());
        model.setLocationId(definition.getLocationId());
        model.setSupplyId(definition.getSupplyId());
        model.setManufacturerId(definition.getManufacturerId());
        model.setManufacturerText(definition.getManufacturerText());
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
     * @param ids 主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<MedicationDefinition> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return medicationDefinitionRepository.findAllById(ids);
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

}
