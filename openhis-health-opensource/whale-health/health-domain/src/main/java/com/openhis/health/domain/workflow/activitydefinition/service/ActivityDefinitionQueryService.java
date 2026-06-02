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

package com.openhis.health.domain.workflow.activitydefinition.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.query.ProductDefinitionCatalogPageQuery;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.enums.PublicationStatus;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinitionChild;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionExportQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.*;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionChildRepository;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 诊疗项目定义读侧查询服务。
 *
 * <p>约束：只读查询，不写库；过滤口径（含软删）必须下推到数据库侧完成。</p>
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
     * @param query 查询条件
     * @param pageable 分页参数（默认排序由调用方决定；若未传排序，上层应使用 createdAt DESC）
     * @return 分页结果
     */
    public Page<ActivityDefinitionPageQueryModel> pageServiceCatalog(ActivityDefinitionPageQuery query, Pageable pageable) {
        Specification<ActivityDefinition> specification = this.buildPageSpecification(query);
        return activityDefinitionRepository.findAll(specification, pageable).map(this::toPageModel);
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
     * 关键字检索。
     *
     * @param keyword 关键字
     * @param limit 最大返回条数
     * @return 命中列表
     */
    public List<ActivityDefinitionSearchQueryModel> findBySearchKey(String keyword, Integer limit) {
        // 无关键字则不做全表扫描式检索，直接空结果
        if (StringUtils.isBlank(keyword)) {
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
     * 查询启用列表。
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
        if (PublicationStatus.RETIRED.getCode().equals(definition.getStatusEnum())) {
            model.setEditable(Boolean.FALSE);
            model.setReasonCode("RETIRED");
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
     */
    private Specification<ActivityDefinition> buildPageSpecification(ActivityDefinitionPageQuery query) {
        return (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (query.getTypeEnum() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum()));
                }
                // 关键字同时匹配名称、业务编号与检索码，提升临床检索命中率
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                        cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                    ));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /**
     * 私有方法：buildExportSpecification。
     */
    private Specification<ActivityDefinition> buildExportSpecification(ActivityDefinitionExportQuery query) {
        // 导出查询参数为 null 时不附加过滤条件；否则转换为分页查询对象后构建 Specification。
        if (query == null) {
            return this.buildPageSpecification(null);
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
     */
    public Page<ActivityDefinition> pageForCatalog(ProductDefinitionCatalogPageQuery query, Pageable pageable) {
        return activityDefinitionRepository.findAll(this.buildCatalogOrderSpecification(query), pageable);
    }

    private Specification<ActivityDefinition> buildCatalogOrderSpecification(ProductDefinitionCatalogPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 医嘱开立目录仅暴露已发布项（PublicationStatus.ACTIVE code）。
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                Predicate keywordPred = this.buildCatalogKeywordPredicate(root, cb, query.getKeyword());
                Predicate prepPred = this.buildActivityPrepickedPredicate(root, cb, query.getPrepickedProductKeys());
                this.appendKeywordOrPrepicked(predicates, cb, keywordPred, prepPred);
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Predicate buildCatalogKeywordPredicate(Root<ActivityDefinition> root, CriteriaBuilder cb, String keyword) {
        if (StringUtils.isBlank(keyword)) {
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
            if (StringUtils.isNotBlank(lookup.getProductTableName())
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
}
