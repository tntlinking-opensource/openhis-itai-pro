/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.activitydefinition.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.enums.PublicationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinitionChild;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionExportQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.*;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionChildRepository;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗项目定义读侧查询服务。
 *
 * <p>约束：只读查询，不写库；过滤口径（含软删）必须下推到数据库侧完成。</p>
 * <p>聚合边界：仅本聚合 {@link ActivityDefinitionRepository} / {@link ActivityDefinitionChildRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class ActivityDefinitionQueryService {

    private final ActivityDefinitionRepository activityDefinitionRepository;
    private final ActivityDefinitionChildRepository activityDefinitionChildRepository;

    /**
     * 页面初始化字典查询。
     *
     * <p>本方法只返回前端渲染所需的选项集合；字典来源由上层应用服务/字典服务注入，此处返回空集合占位。</p>
     *
     * @param organizationId 机构主键（预留，当前未参与筛选）
     * @return 初始化读模型（选项集合为空占位）
     */
    public ActivityDefinitionInitQueryModel getDiseaseTreatmentInit(Long organizationId) {
        ActivityDefinitionInitQueryModel model = new ActivityDefinitionInitQueryModel();
        model.setCategoryOptions(new ArrayList<OptionDto>());
        model.setUnitOptions(new ArrayList<OptionDto>());
        model.setTypeOptions(new ArrayList<OptionDto>());
        model.setStatusOptions(new ArrayList<OptionDto>());
        return model;
    }

    /**
     * 分页查询目录。
     *
     * <p>默认不返回已被套餐引用为子项的目录行；仅当
     * {@link ActivityDefinitionPageQuery#getIncludeReferencedPackageChildren()} 为 {@code true}
     *（目录维护 / 体检配置 / 导出）时包含明细子项。</p>
     *
     * @param query 查询条件
     * @param pageable 分页参数（默认排序由调用方决定；目录维护页上层应使用 busNo DESC）
     * @return 分页结果
     */
    public Page<ActivityDefinitionPageQueryModel> pageServiceCatalog(ActivityDefinitionPageQuery query, Pageable pageable) {
        Specification<ActivityDefinition> specification = this.buildPageSpecification(query);
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return activityDefinitionRepository.findAll(specification, effective).map(this::toPageModel);
    }

    /**
     * 查询目录详情。
     *
     * @param id 项目标识
     * @return 详情模型
     */
    public ActivityDefinitionDetailQueryModel getServiceCatalog(Long id) {
        ActivityDefinition definition = activityDefinitionRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在"));

        ActivityDefinitionDetailQueryModel model = new ActivityDefinitionDetailQueryModel();
        model.setBaseInfo(this.toBaseModel(definition));

        // 子项目/组合项单独仓储加载，避免详情接口无谓 join 放大
        List<ActivityDefinitionChild> children = activityDefinitionChildRepository.findByActivityId(definition.getId());
        List<ActivityDefinitionChildQueryModel> childModels = new ArrayList<>();
        for (ActivityDefinitionChild child : children) {
            childModels.add(this.toChildModel(child));
        }
        model.setChildren(childModels);

        // 收费展示信息：属于读模型拼接能力，本领域查询服务保持占位，不跨域读取收费域事实。
        model.setPricingSnapshot(new PricingSnapshotQueryModel());
        return model;
    }

    /**
     * 按主键加载诊疗定义实体（写侧门禁/编辑前校验用）。
     *
     * @param id 项目标识
     * @return 未软删实体；不存在时 empty
     */
    public Optional<ActivityDefinition> findOptionalActivityDefinitionEntityById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return activityDefinitionRepository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按诊疗项目名称精确解析唯一主键。
     *
     * <p>用于皮试检查等固定名称场景（{@code GeneralSharedConstants.SKIN_TEST_INSPECTION_ACTIVITY_NAME}）。</p>
     *
     * @param name 诊疗项目名称（精确匹配 {@code wkf_activity_definition.name}）
     * @return 唯一主键
     */
    public Long requireIdByExactName(String name) {
        if (StringUtils.isEmpty(name)) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "诊疗项目名称不能为空");
        }
        String trimmed = name.trim();
        Specification<ActivityDefinition> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.NAME), trimmed));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ActivityDefinition> definitions = activityDefinitionRepository.findAll(specification);
        if (definitions.isEmpty()) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND,
                "未找到诊疗项目：" + trimmed);
        }
        if (definitions.size() > 1) {
            throw new BusinessException(
                ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT,
                "诊疗项目名称不唯一：" + trimmed);
        }
        return definitions.get(0).getId();
    }

    /**
     * 关键字检索。
     *
     * @param keyword 关键字
     * @param limit 最大返回条数
     * @return 命中列表
     */
    public List<ActivityDefinitionSearchQueryModel> findBySearchKey(String keyword, Integer limit) {
        // 无关键字则不做全表扫描式检索，直接空结果
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        // 未传上限时用默认 20 条，防止一次返回过大拖慢检索接口
        if (limit == null || limit <= 0) {
            limit = 20;
        }

        Pageable pageable = PageRequest.of(0, limit);
        Specification<ActivityDefinition> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            String trimmed = keyword.trim();
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
            predicates.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
            ));
            // B.2.1：关键字检索精确全等置顶
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                cq,
                cb,
                trimmed,
                List.of(cb.desc(root.get(FieldNameConstants.ID))),
                root.get(FieldNameConstants.NAME),
                root.get(FieldNameConstants.BUS_NO),
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<ActivityDefinition> page = activityDefinitionRepository.findAll(specification, pageable);
        List<ActivityDefinitionSearchQueryModel> models = new ArrayList<>();
        for (ActivityDefinition definition : page.getContent()) {
            models.add(this.toSearchModel(definition));
        }
        return models;
    }

    /**
     * 查询启用列表（诊疗–耗材绑定等下拉）。
     *
     * <p>始终排除已被套餐引用为子项的明细行，仅暴露可单独开立/绑定的父项。</p>
     *
     * @param organizationId 机构/科室维度（可选）
     * @param locationId 位置维度（可选）
     * @return 启用集合
     */
    public List<ActivityDefinitionEnabledQueryModel> diseaseTreatmentList(Long organizationId, Long locationId) {
        Specification<ActivityDefinition> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            // 绑定下拉不暴露套餐明细子项
            predicates.add(this.buildNotReferencedPackageChildPredicate(root, cq, cb, null));
            // 传入机构时收窄到该院可开展的诊疗项目
            if (organizationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), organizationId));
            }
            // 传入执行地点时进一步过滤场所维度
            if (locationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.LOCATION_ID), locationId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<ActivityDefinition> definitions = activityDefinitionRepository.findAll(specification);
        List<ActivityDefinitionEnabledQueryModel> models = new ArrayList<>();
        for (ActivityDefinition definition : definitions) {
            ActivityDefinitionEnabledQueryModel model = new ActivityDefinitionEnabledQueryModel();
            model.setId(definition.getId());
            model.setName(definition.getName());
            model.setTypeEnum(definition.getTypeEnum());
            model.setPackageUnitCode(definition.getPackageUnitCode());
            models.add(model);
        }
        return models;
    }

    /**
     * 编辑前可编辑性校验。
     *
     * <p>本方法仅提供领域内可见的门控：记录存在与否、软删与发布状态基础判断；更复杂的锁定/引用约束由上层应用服务补充。</p>
     */
    public ActivityEditValidationQueryModel validateActivityEdit(Long id) {
        ActivityEditValidationQueryModel model = new ActivityEditValidationQueryModel();
        ActivityDefinition definition = activityDefinitionRepository.findById(id).orElse(null);
        // 记录不存在或已软删时禁止进入编辑流
        if (definition == null) {
            model.setEditable(Boolean.FALSE);
            model.setReasonCode("NOT_FOUND");
            model.setReasonMessage("诊疗项目不存在或已删除");
            return model;
        }

        // DOC-CONFLICT: DM/SDD 未定义“不可编辑条件”的完整规则，此处只做基础兜底判断。
        if (PublicationStatus.INACTIVE.getCode().equals(definition.getStatusEnum())) {
            model.setEditable(Boolean.FALSE);
            model.setReasonCode("INACTIVE");
            model.setReasonMessage("停用状态的诊疗项目不允许编辑");
            return model;
        }

        model.setEditable(Boolean.TRUE);
        model.setReasonCode("OK");
        model.setReasonMessage("允许编辑");
        return model;
    }

    /**
     * 下载导入模板。
     */
    public ActivityCatalogTemplateQueryModel downloadActivityCatalogTemplate(String templateVersion) {
        ActivityCatalogTemplateQueryModel model = new ActivityCatalogTemplateQueryModel();
        model.setTemplateName("activity-catalog-template");
        model.setHeaders(new ArrayList<String>());
        model.setSampleRows(new ArrayList<Map<String, String>>());
        return model;
    }

    /**
     * 导出诊疗目录。
     */
    public ActivityCatalogExportQueryModel exportActivityCatalog(ActivityDefinitionExportQuery query) {
        Specification<ActivityDefinition> specification = this.buildExportSpecification(query);
        List<ActivityDefinition> definitions = activityDefinitionRepository.findAll(specification);

        List<ActivityCatalogExportRowQueryModel> rows = new ArrayList<>();
        for (ActivityDefinition definition : definitions) {
            ActivityCatalogExportRowQueryModel row = new ActivityCatalogExportRowQueryModel();
            row.setBusNo(definition.getBusNo());
            row.setName(definition.getName());
            row.setCategoryCode(definition.getCategoryCode());
            row.setTypeEnum(definition.getTypeEnum());
            row.setStatusEnum(definition.getStatusEnum());
            rows.add(row);
        }

        ActivityCatalogExportQueryModel model = new ActivityCatalogExportQueryModel();
        model.setRows(rows);
        model.setTotal((long) rows.size());
        return model;
    }

    /**
     * 按目录维护筛选条件列出诊疗定义基础信息（导出编排用；受 maxRows 截断）。
     *
     * <p>筛选口径与 {@link #pageServiceCatalog} 一致；未传
     * {@code includeReferencedPackageChildren} 时默认 {@code true}（含套餐明细子项）。</p>
     *
     * @param query   分类/关键字；{@code null} 表示仅未删除且含明细子项
     * @param maxRows 导出行上限（含）；小于 1 时按 1 保护
     * @return 基础读模型列表（按 busNo 倒序；有关键字时 Spec 内精确优先）
     */
    public List<ActivityDefinitionBaseQueryModel> listServiceCatalogForExport(
        ActivityDefinitionPageQuery query,
        int maxRows
    ) {
        // Gate：导出行上限至少为 1
        int limit = maxRows;
        if (limit < 1) {
            limit = 1;
        }
        ActivityDefinitionPageQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new ActivityDefinitionPageQuery();
        }
        if (safeQuery.getIncludeReferencedPackageChildren() == null) {
            // 目录维护导出默认含明细子项，与 pageServiceCatalog IF 主场景一致
            safeQuery.setIncludeReferencedPackageChildren(Boolean.TRUE);
        }
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        Pageable pageable = PageRequest.of(0, limit, sort);
        boolean hasKeyword = StringUtils.isNotEmpty(safeQuery.getKeyword());
        Pageable effective = pageable;
        if (hasKeyword) {
            // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        Specification<ActivityDefinition> specification = this.buildPageSpecification(safeQuery);
        Page<ActivityDefinition> page = activityDefinitionRepository.findAll(specification, effective);
        List<ActivityDefinitionBaseQueryModel> rows = new ArrayList<>();
        for (ActivityDefinition definition : page.getContent()) {
            rows.add(this.toBaseModel(definition));
        }
        return rows;
    }

    /**
     * 查询医保对码状态。
     */
    public ActivityYbMappingStatusQueryModel getActivityYbMappingStatus(Long activityId) {
        ActivityDefinition definition = activityDefinitionRepository.findById(activityId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.ACTIVITY_DEFINITION_NOT_FOUND, "诊疗项目定义不存在"));
        ActivityYbMappingStatusQueryModel model = new ActivityYbMappingStatusQueryModel();
        model.setActivityId(definition.getId());
        model.setYbMatchFlag(definition.getYbMatchFlag());
        model.setYbNo(definition.getYbNo());
        model.setChrgitmLv(definition.getChrgitmLv());
        return model;
    }

    /**
     * 私有方法：toPageModel。
     */
    private ActivityDefinitionPageQueryModel toPageModel(ActivityDefinition definition) {
        ActivityDefinitionPageQueryModel model = new ActivityDefinitionPageQueryModel();
        model.setId(definition.getId());
        model.setBusNo(definition.getBusNo());
        model.setName(definition.getName());
        model.setCategoryCode(definition.getCategoryCode());
        model.setPackageUnitCode(definition.getPackageUnitCode());
        model.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        model.setTypeEnum(definition.getTypeEnum());
        model.setStatusEnum(definition.getStatusEnum());
        model.setYbFlag(definition.getYbFlag());
        model.setChildrenFlag(definition.getChildrenFlag());
        return model;
    }

    /**
     * 私有方法：toBaseModel。
     */
    private ActivityDefinitionBaseQueryModel toBaseModel(ActivityDefinition definition) {
        ActivityDefinitionBaseQueryModel model = new ActivityDefinitionBaseQueryModel();
        model.setId(definition.getId());
        model.setCategoryCode(definition.getCategoryCode());
        model.setBusNo(definition.getBusNo());
        model.setName(definition.getName());
        model.setPyStr(definition.getPyStr());
        model.setWbStr(definition.getWbStr());
        model.setPackageUnitCode(definition.getPackageUnitCode());
        model.setActivityVersion(definition.getActivityVersion());
        model.setMerchandiseName(definition.getMerchandiseName());
        model.setMerchandisePyStr(definition.getMerchandisePyStr());
        model.setMerchandiseWbStr(definition.getMerchandiseWbStr());
        model.setDefEncounterUnitCode(definition.getDefEncounterUnitCode());
        model.setDefInhospitalUnitCode(definition.getDefInhospitalUnitCode());
        model.setTypeEnum(definition.getTypeEnum());
        model.setStatusEnum(definition.getStatusEnum());
        model.setDescriptionText(definition.getDescriptionText());
        model.setBodySiteCode(definition.getBodySiteCode());
        model.setSpecimenCode(definition.getSpecimenCode());
        model.setOrgId(definition.getOrgId());
        model.setLocationId(definition.getLocationId());
        model.setRuleId(definition.getRuleId());
        model.setPricingFlag(definition.getPricingFlag());
        model.setYbFlag(definition.getYbFlag());
        model.setYbNo(definition.getYbNo());
        model.setYbMatchFlag(definition.getYbMatchFlag());
        model.setChrgitmLv(definition.getChrgitmLv());
        model.setMrSettlementTypeCode(definition.getMrSettlementTypeCode());
        model.setChildrenJson(definition.getChildrenJson());
        model.setChildrenFlag(definition.getChildrenFlag());
        return model;
    }

    /**
     * 私有方法：toChildModel。
     */
    private ActivityDefinitionChildQueryModel toChildModel(ActivityDefinitionChild child) {
        ActivityDefinitionChildQueryModel model = new ActivityDefinitionChildQueryModel();
        model.setId(child.getId());
        model.setActivityId(child.getActivityId());
        model.setChildrenId(child.getChildrenId());
        model.setChildrenNum(child.getChildrenNum());
        model.setChildrenName(child.getChildrenName());
        return model;
    }

    /**
     * 私有方法：toSearchModel。
     */
    private ActivityDefinitionSearchQueryModel toSearchModel(ActivityDefinition definition) {
        ActivityDefinitionSearchQueryModel model = new ActivityDefinitionSearchQueryModel();
        model.setId(definition.getId());
        model.setBusNo(definition.getBusNo());
        model.setName(definition.getName());
        model.setPyStr(definition.getPyStr());
        model.setWbStr(definition.getWbStr());
        return model;
    }

    /**
     * 私有方法：buildPageSpecification。
     *
     * <p>未显式 {@code includeReferencedPackageChildren=true} 时排除套餐明细子项。</p>
     */
    private Specification<ActivityDefinition> buildPageSpecification(ActivityDefinitionPageQuery query) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 默认隐藏套餐子项；目录维护/体检/导出传 includeReferencedPackageChildren=true
            boolean includePackageChildren = query != null
                && Boolean.TRUE.equals(query.getIncludeReferencedPackageChildren());
            if (!includePackageChildren) {
                predicates.add(this.buildNotReferencedPackageChildPredicate(root, cq, cb, null));
            }
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (query.getTypeEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum()));
                }
                // 体检目录区分单项与套餐时按 childrenFlag 过滤
                if (query.getChildrenFlag() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CHILDREN_FLAG), query.getChildrenFlag()));
                }
                // 关键字同时匹配名称、业务编号与检索码，提升临床检索命中率
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                    ));
                    // B.2.1：维护分页关键字精确全等置顶
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT))),
                        root.get(FieldNameConstants.NAME),
                        root.get(FieldNameConstants.BUS_NO),
                        root.get(FieldNameConstants.PY_STR),
                        root.get(FieldNameConstants.WB_STR));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 私有方法：buildExportSpecification。
     */
    private Specification<ActivityDefinition> buildExportSpecification(ActivityDefinitionExportQuery query) {
        // 导出须含明细子项；query 为空时按全量导出（含明细）构建 Spec
        if (query == null) {
            ActivityDefinitionPageQuery all = new ActivityDefinitionPageQuery();
            all.setIncludeReferencedPackageChildren(Boolean.TRUE);
            return this.buildPageSpecification(all);
        }
        return this.buildPageSpecification(this.toPageQuery(query));
    }

    /**
     * 私有方法：toPageQuery。
     */
    private ActivityDefinitionPageQuery toPageQuery(ActivityDefinitionExportQuery query) {
        ActivityDefinitionPageQuery pageQuery = new ActivityDefinitionPageQuery();
        pageQuery.setCategoryCode(query.getCategoryCode());
        pageQuery.setKeyword(query.getKeyword());
        pageQuery.setStatusEnum(query.getStatusEnum());
        pageQuery.setTypeEnum(query.getTypeEnum());
        // 目录导出需含明细子项行，便于全量备份/对账
        pageQuery.setIncludeReferencedPackageChildren(Boolean.TRUE);
        return pageQuery;
    }

    /**
     * 按主键集合批量加载诊疗项目定义（医嘱列表预热等场景）。
     *
     * @param ids 主键集合
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<ActivityDefinition> findAllByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return activityDefinitionRepository.findByIdIn(new ArrayList<>(ids));
    }

    /**
     * 医嘱开立目录分页（读 {@code wkf_activity_definition}）。
     *
     * <p>开立/划价选品不返回已被套餐引用为子项的目录行（如「肝功五项」下的五个子项）；
     * 仅父项可检索。组套/历史行 {@code prepickedProductKeys} 回填仍可按 id 命中子项。
     * 目录维护 / 体检配置经 {@link #pageServiceCatalog} 且
     * {@code includeReferencedPackageChildren=true} 时可含明细子项；医保对码走独立查询。</p>
     */
    public Page<ActivityDefinition> pageForCatalog(ProductDefinitionCatalogPageQuery query, Pageable pageable) {
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return activityDefinitionRepository.findAll(this.buildCatalogOrderSpecification(query), effective);
    }

    /**
     * 医嘱开立目录 Specification：已发布 + 可选类目/关键字/预选产品键 OR 组合；排除套餐子项引用。
     *
     * @param query 分页查询条件
     * @return JPA Specification
     */
    private Specification<ActivityDefinition> buildCatalogOrderSpecification(ProductDefinitionCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 医嘱开立目录仅暴露已发布项（PublicationStatus.ACTIVE code）。
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            // 开立/划价：子项只能随父套餐展开，禁止单独出现在选品列表
            Predicate prepickedExemption = null;
            if (query != null) {
                prepickedExemption = this.buildActivityPrepickedPredicate(root, cb, query.getPrepickedProductKeys());
            }
            predicates.add(this.buildNotReferencedPackageChildPredicate(root, cq, cb, prepickedExemption));
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                // 门诊等场景排除嘱托类目（BR-ORD-032 / AS-IP-DOR-003 BR-IP-ORD-028②）
                if (query.getExcludeCategoryCodes() != null && !query.getExcludeCategoryCodes().isEmpty()) {
                    List<String> excluded = new ArrayList<>();
                    for (String code : query.getExcludeCategoryCodes()) {
                        if (StringUtils.isNotEmpty(code)) {
                            excluded.add(code.trim());
                        }
                    }
                    if (!excluded.isEmpty()) {
                        predicates.add(cb.not(root.get(FieldNameConstants.CATEGORY_CODE).in(excluded)));
                    }
                    // 双锚：排除类目 52 时同步排除目录名精确「嘱托」，防类目未维护仍可检索到
                    if (excluded.contains(ServiceRequestCategoryConstants.SERVICE_CATEGORY_ENTRUST)) {
                        predicates.add(cb.not(cb.equal(
                            root.get(FieldNameConstants.NAME),
                            GeneralSharedConstants.ENTRUST_ACTIVITY_NAME)));
                    }
                }
                Predicate keywordPred = this.buildCatalogKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildActivityPrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(predicates, cb, keywordPred, prepPred);
                // B.2.1：开立目录关键字精确命中置顶
                this.applyCatalogKeywordExactFirstOrder(root, cq, cb, query.getKeyword());
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * 排除「已被某父套餐引用为子项」的诊疗定义行。
     *
     * <p>依据 {@code wkf_activity_definition_children.children_id}（未软删）判定；
     * 若 id 出现在 {@code prepickedProductKeys} 中则豁免，保证组套/历史回填仍可按主键拉到子项。</p>
     *
     * @param root               活动定义根
     * @param cq                 外层 CriteriaQuery（承载 subquery）
     * @param cb                 CriteriaBuilder
     * @param prepickedExemption 预选键 id OR 谓词；无预选时传 null
     * @return 非套餐子项（或预选豁免）谓词
     */
    private Predicate buildNotReferencedPackageChildPredicate(
        Root<ActivityDefinition> root,
        jakarta.persistence.criteria.CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        Predicate prepickedExemption
    ) {
        Subquery<Long> childRefSubquery = cq.subquery(Long.class);
        Root<ActivityDefinitionChild> childRoot = childRefSubquery.from(ActivityDefinitionChild.class);
        childRefSubquery.select(childRoot.get(FieldNameConstants.ID));
        childRefSubquery.where(
            cb.equal(childRoot.get(FieldNameConstants.CHILDREN_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(childRoot.get(FieldNameConstants.IS_DELETED))
        );
        Predicate notPackageChild = cb.not(cb.exists(childRefSubquery));
        // 组套/历史 prepicked：允许按 id 回填子项，否则「目录中未找到」
        if (prepickedExemption != null) {
            return cb.or(notPackageChild, prepickedExemption);
        }
        return notPackageChild;
    }

    /**
     * 目录关键字谓词：名称/商品名/py/wb/业务号不区分大小写 LIKE（B.2.1）。
     *
     * @param root    活动定义根
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空则 null
     * @return 关键字 OR 谓词；无关键字时 null
     */
    private Predicate buildCatalogKeywordPredicate(Root<ActivityDefinition> root, CriteriaBuilder cb, String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return null;
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        // 纯拼音/五笔字母输入：只扫检索码列
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
     * 开立目录关键字：SQL 层「精确全等在前、模糊在后」（B.2.1）。
     *
     * @param root    活动定义根
     * @param cq      CriteriaQuery
     * @param cb      CriteriaBuilder
     * @param keyword 用户关键字；空则不写排序
     */
    private void applyCatalogKeywordExactFirstOrder(
        Root<ActivityDefinition> root,
        jakarta.persistence.criteria.CriteriaQuery<?> cq,
        CriteriaBuilder cb,
        String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return;
        }
        String trimmed = keyword.trim();
        List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.CREATED_AT)));
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
     * 预选产品键谓词：非 wkf_activity_definition 表键忽略，其余按 id OR 匹配。
     *
     * @param root 活动定义根
     * @param cb   CriteriaBuilder
     * @param keys 批量 lookup 键
     * @return id OR 谓词；无有效键时 null
     */
    private Predicate buildActivityPrepickedPredicate(
        Root<ActivityDefinition> root,
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
                && !TableNameConstants.WKF_ACTIVITY_DEFINITION.equals(lookup.getProductTableName().trim())) {
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
     * 关键字与预选键组合：两者均非空时 OR；否则追加非空的一方。
     *
     * @param predicates   主谓词列表（副作用追加）
     * @param cb           CriteriaBuilder
     * @param keywordPred  关键字谓词
     * @param prepPred     预选键谓词
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
     * 按业务编码批量加载诊疗项目定义（外部目录同步后解析实例主键）。
     *
     * @param busNos 业务编码列表
     * @return 命中实体列表；入参为空时返回空列表
     */
    public List<ActivityDefinition> findByBusNos(List<String> busNos) {
        if (busNos == null || busNos.isEmpty()) {
            return Collections.emptyList();
        }
        return activityDefinitionRepository.findByBusNoIn(busNos);
    }

    /**
     * 按父诊疗项目主键加载子项事实行（BR-ACTDEF-005；套餐展开优先于 childrenJson 快照）。
     *
     * @param activityId 父诊疗项目主键
     * @return 子项查询模型列表
     */
    public List<ActivityDefinitionChildQueryModel> listChildrenByActivityId(Long activityId) {
        if (activityId == null) {
            return Collections.emptyList();
        }
        return this.listChildrenByActivityIds(List.of(activityId)).getOrDefault(activityId, Collections.emptyList());
    }

    /**
     * 按父诊疗项目主键批量加载子项（套餐选品预热，BR-ACTDEF-005）。
     *
     * @param activityIds 父诊疗项目主键集合
     * @return activityId → 子项列表
     */
    public Map<Long, List<ActivityDefinitionChildQueryModel>> listChildrenByActivityIds(Collection<Long> activityIds) {
        if (activityIds == null || activityIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ActivityDefinitionChild> children =
            activityDefinitionChildRepository.findByActivityIdIn(new ArrayList<>(activityIds));
        Map<Long, List<ActivityDefinitionChildQueryModel>> grouped = new LinkedHashMap<>();
        for (ActivityDefinitionChild child : children) {
            if (child == null || Boolean.TRUE.equals(child.getIsDeleted()) || child.getActivityId() == null) {
                continue;
            }
            grouped.computeIfAbsent(child.getActivityId(), k -> new ArrayList<>()).add(this.toChildModel(child));
        }
        return grouped;
    }

    /**
     * 批量查询库内已存在的诊疗唯一键 token（同机构、未删除、未废弃）。
     *
     * <p>token 格式：{@code trim(name) + '\\u0001' + trim(categoryCode)}。</p>
     *
     * @param orgId  机构主键
     * @param tokens 待比对 token 集合
     * @return 库内已存在的 token 子集；入参无效时返回空集
     */
    public Set<String> findExistingDuplicateKeyTokens(Long orgId, Collection<String> tokens) {
        if (orgId == null || tokens == null || tokens.isEmpty()) {
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
        Specification<ActivityDefinition> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orgId));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM),
                PublicationStatus.DISCARDED.getCode()));
            predicates.add(root.get(FieldNameConstants.NAME).in(names));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        List<ActivityDefinition> candidates = activityDefinitionRepository.findAll(specification);
        Set<String> existing = new HashSet<>();
        for (ActivityDefinition row : candidates) {
            if (row == null) {
                continue;
            }
            String candidateToken = this.buildDuplicateKeyToken(row.getName(), row.getCategoryCode());
            if (tokenSet.contains(candidateToken)) {
                existing.add(candidateToken);
            }
        }
        return existing;
    }

    /**
     * 构造与导入校验一致的唯一键 token。
     *
     * @param name         诊疗名称
     * @param categoryCode 目录分类编码
     * @return token 字符串
     */
    private String buildDuplicateKeyToken(String name, String categoryCode) {
        String n = "";
        if (name != null) {
            n = name.trim();
        }
        String c = "";
        if (categoryCode != null) {
            c = categoryCode.trim();
        }
        return n + '\u0001' + c;
    }
}
