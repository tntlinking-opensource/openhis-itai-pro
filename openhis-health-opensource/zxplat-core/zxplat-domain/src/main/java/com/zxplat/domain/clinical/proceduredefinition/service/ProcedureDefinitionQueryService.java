/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.service;

import com.zxplat.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.zxplat.domain.clinical.proceduredefinition.queries.ProcedureDefinitionPageQuery;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionPageItemQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.ProcedureDefinitionConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 手术操作定义读侧查询服务（DM-CLN-009 §6.2）。
 *
 * <p>仅注入本聚合 {@link ProcedureDefinitionRepository}（B.6.4）；不做跨聚合写、不编排他聚合 QueryService。</p>
 *
 * <p>默认过滤软删行（BR-PD-008）；维护分页关键字覆盖名称/主编码/国临码/医保码/拼音/五笔（对齐 AS BR-POC-011 与 DM §2 高频检索）。</p>
 */
@Service
@RequiredArgsConstructor
public class ProcedureDefinitionQueryService {

    /** 本聚合仓储：分页 Spec 与主键/批量派生查询。 */
    private final ProcedureDefinitionRepository repository;

    /**
     * 维护端分页查询手术操作目录。
     *
     * @param query    关键字与来源筛选；可为 null
     * @param pageable 分页与排序；有关键字时剥掉 Sort 以保留精确优先 ORDER BY
     * @return 分页行模型（含挂靠 AD）
     */
    public Page<ProcedureDefinitionPageItemQueryModel> page(ProcedureDefinitionPageQuery query, Pageable pageable) {
        String keyword = "";
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())) {
            keyword = query.getKeyword().trim();
        }
        String sourceEnum = null;
        if (query != null && StringUtils.isNotBlank(query.getSourceEnum())) {
            sourceEnum = query.getSourceEnum().trim();
        }
        // B.2.1：有关键字时剥掉 Sort，保留 Spec 精确优先 ORDER BY
        Pageable effective = this.unsortPageableIfKeyword(StringUtils.isNotEmpty(keyword), pageable);
        return repository.findAll(buildPageSpec(keyword, sourceEnum), effective).map(this::toPageItem);
    }

    /**
     * 按主键加载详情；已软删视为不存在（BR-PD-008）。
     *
     * @param id 主键
     * @return 详情模型（含挂靠 AD）
     * @throws BusinessException 不存在或已删
     */
    public ProcedureDefinitionDetailQueryModel getOne(Long id) {
        ProcedureDefinition entity = repository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()))
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术定义不存在"));
        ProcedureDefinitionDetailQueryModel model = new ProcedureDefinitionDetailQueryModel();
        model.setId(entity.getId());
        model.setSourceEnum(entity.getSourceEnum());
        model.setProcedureCode(entity.getProcedureCode());
        model.setName(entity.getName());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setTypeCode(entity.getTypeCode());
        model.setDescription(entity.getDescription());
        model.setYbFlag(entity.getYbFlag());
        model.setYbNo(entity.getYbNo());
        model.setYbMatchFlag(entity.getYbMatchFlag());
        model.setClinicCode(entity.getClinicCode());
        model.setStatusEnum(entity.getStatusEnum());
        model.setActivityDefinitionId(entity.getActivityDefinitionId());
        return model;
    }

    /**
     * 按主键批量装载检索项（手术开立展示 / 病案带入；认 SR.procedure_definition_id）。
     *
     * @param ids 手术定义主键集合
     * @return id → 检索项；软删或不存在不入图
     */
    public Map<Long, ProcedureDefinitionSearchItemQueryModel> mapSearchItemsByIds(Collection<Long> ids) {
        Map<Long, ProcedureDefinitionSearchItemQueryModel> result = new LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        List<Long> idList = new ArrayList<>();
        for (Long id : ids) {
            if (id != null) {
                idList.add(id);
            }
        }
        if (idList.isEmpty()) {
            return result;
        }
        for (ProcedureDefinition entity : repository.findAllById(idList)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted()) || entity.getId() == null) {
                continue;
            }
            result.put(entity.getId(), this.toSearchItem(entity));
        }
        return result;
    }

    /**
     * 医生站：按关键字检索已启用国临手术操作（病案编码等仍限 clinical_proc_v3）。
     *
     * @param keyword 关键字；空则空列表
     * @return 检索项列表
     */
    public List<ProcedureDefinitionSearchItemQueryModel> searchClinicalProcV3ByKeyword(String keyword) {
        return searchByKeywordAndSource(keyword, ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3);
    }

    /**
     * 病案首页手术选码：按关键字分页检索已启用国临手术（clinical_proc_v3）。
     *
     * <p>关键字可空——空则按 id 倒序浏览 ACTIVE 国临项（打开下拉即可出内容）。</p>
     *
     * @param keyword  关键字；可空
     * @param pageable 分页
     * @return 分页检索项
     */
    public Page<ProcedureDefinitionSearchItemQueryModel> pageClinicalProcV3ByKeyword(
        String keyword, Pageable pageable) {
        return pageByKeywordAndSource(
            keyword, ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3, pageable);
    }

    /**
     * 医生站手术开立：按关键字检索已启用手术目录（不限来源；无分页，关键字空则空列表）。
     *
     * @param keyword 关键字；空则空列表
     * @return 检索项列表
     */
    public List<ProcedureDefinitionSearchItemQueryModel> searchActiveByKeyword(String keyword) {
        return searchByKeywordAndSource(keyword, null);
    }

    /**
     * 医生站手术开立选品分页：已启用手术目录（不限来源）。
     *
     * <p>关键字可空——空则按 id 倒序浏览 ACTIVE 项（与医嘱目录下拉同口径：打开即出内容）。</p>
     *
     * @param keyword  关键字；可空
     * @param pageable 分页
     * @return 分页检索项
     */
    public Page<ProcedureDefinitionSearchItemQueryModel> pageActiveByKeyword(String keyword, Pageable pageable) {
        // 不限 source_enum：国临 3.0 与手术操作目录均可开立
        return pageByKeywordAndSource(keyword, null, pageable);
    }

    /**
     * 按挂靠 AD 反查已启用国临手术定义（兼容旧路径：SR.activityId → PD）。
     *
     * <p>术式开立现以 {@code ServiceRequest.procedureDefinitionId} 为临床真源；本方法仍供
     * 历史行/加收挂靠反查展示，不作为术式开立落库主键来源。</p>
     *
     * @param activityDefinitionId 诊疗项目主键
     * @return 命中项；无挂靠或未启用则 {@code null}
     */
    public ProcedureDefinitionSearchItemQueryModel findActiveClinicalByActivityDefinitionId(Long activityDefinitionId) {
        if (activityDefinitionId == null) {
            return null;
        }
        ProcedureDefinition best = null;
        for (ProcedureDefinition entity : repository.findByActivityDefinitionId(activityDefinitionId)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            if (!ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(entity.getSourceEnum())) {
                continue;
            }
            best = entity;
            break;
        }
        if (best == null) {
            return null;
        }
        return this.toSearchItem(best);
    }

    /**
     * 批量按挂靠 AD 反查已启用国临手术定义。
     *
     * @param activityDefinitionIds 诊疗项目主键集合
     * @return activityDefinitionId → 国临检索项；无命中不入图
     */
    public Map<Long, ProcedureDefinitionSearchItemQueryModel> mapActiveClinicalByActivityDefinitionIds(
        Collection<Long> activityDefinitionIds
    ) {
        Map<Long, ProcedureDefinitionSearchItemQueryModel> result = new LinkedHashMap<>();
        if (activityDefinitionIds == null || activityDefinitionIds.isEmpty()) {
            return result;
        }
        List<Long> ids = new ArrayList<>();
        for (Long id : activityDefinitionIds) {
            if (id != null) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return result;
        }
        for (ProcedureDefinition entity : repository.findByActivityDefinitionIdIn(ids)) {
            if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            if (!PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())) {
                continue;
            }
            if (!ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(entity.getSourceEnum())) {
                continue;
            }
            Long adId = entity.getActivityDefinitionId();
            if (adId == null || result.containsKey(adId)) {
                continue;
            }
            result.put(adId, this.toSearchItem(entity));
        }
        return result;
    }

    /**
     * 按首页国临码找到同一条手术目录，取出该行 {@code yb_no}（4401/4101A 出站）。
     *
     * <p>一条 {@code cln_procedure_definition} 同时有国临码与医保码，不是诊断那种跨目录对码。
     * 优先国临来源行上已填的 {@code yb_no}。</p>
     *
     * @param clinicCodes 国临码集合
     * @return clinicCode → 该行 ybNo；空 ybNo 不入图
     */
    public Map<String, String> mapYbNoByClinicCodes(Collection<String> clinicCodes) {
        Map<String, String> result = new LinkedHashMap<>();
        if (clinicCodes == null || clinicCodes.isEmpty()) {
            return result;
        }
        List<String> codes = new ArrayList<>();
        for (String code : clinicCodes) {
            if (StringUtils.isNotBlank(code)) {
                codes.add(code.trim());
            }
        }
        if (codes.isEmpty()) {
            return result;
        }
        Map<String, ProcedureDefinition> best = new HashMap<>();
        for (ProcedureDefinition entity : repository.findByClinicCodeIn(codes)) {
            putBest(best, entity, StringUtils.trimToNull(entity.getClinicCode()));
        }
        for (ProcedureDefinition entity : repository.findByProcedureCodeIn(codes)) {
            putBest(best, entity, StringUtils.trimToNull(entity.getProcedureCode()));
        }
        for (Map.Entry<String, ProcedureDefinition> entry : best.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getYbNo().trim());
        }
        return result;
    }

    /**
     * 同一键多行时择优：优先国临行上的缺省 {@code yb_no}（BR-PD-006），否则用医保对端。
     *
     * @param best   累计最优映射
     * @param entity 候选实体
     * @param key    国临码键
     */
    private void putBest(Map<String, ProcedureDefinition> best, ProcedureDefinition entity, String key) {
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted()) || key == null) {
            return;
        }
        String ybNo = StringUtils.trimToNull(entity.getYbNo());
        if (ybNo == null) {
            return;
        }
        ProcedureDefinition existing = best.get(key);
        if (existing == null) {
            best.put(key, entity);
            return;
        }
        boolean existingClinical = ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(existing.getSourceEnum());
        boolean currentClinical = ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3.equals(entity.getSourceEnum());
        // 国临缺省上传码优先于医保侧任意一条
        if (currentClinical && !existingClinical) {
            best.put(key, entity);
        }
    }

    /**
     * 按来源+关键字检索已启用目录（无分页；关键字空则空列表，供病案等精确检索）。
     *
     * @param keyword    关键字
     * @param sourceEnum 来源码
     * @return 检索项列表
     */
    private List<ProcedureDefinitionSearchItemQueryModel> searchByKeywordAndSource(String keyword, String sourceEnum) {
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        List<ProcedureDefinitionSearchItemQueryModel> models = new ArrayList<>();
        for (ProcedureDefinition entity : repository.findAll(buildKeywordSourceSpec(keyword, sourceEnum))) {
            models.add(this.toSearchItem(entity));
        }
        return models;
    }

    /**
     * 按来源+可选关键字分页检索已启用目录（医生站下拉：空关键字亦可浏览）。
     *
     * @param keyword    关键字；可空
     * @param sourceEnum 来源码；可空表示不限
     * @param pageable   分页
     * @return 分页检索项
     */
    private Page<ProcedureDefinitionSearchItemQueryModel> pageByKeywordAndSource(
        String keyword, String sourceEnum, Pageable pageable) {
        Pageable effective = pageable;
        if (effective == null) {
            effective = PageRequest.of(0, 20);
        }
        // 有关键字时剥 Sort，保留 Spec 内精确优先 ORDER BY
        if (StringUtils.isNotBlank(keyword) && effective.getSort().isSorted()) {
            effective = PageRequest.of(effective.getPageNumber(), effective.getPageSize());
        }
        Page<ProcedureDefinition> page = repository.findAll(buildKeywordSourceSpec(keyword, sourceEnum), effective);
        return page.map(this::toSearchItem);
    }

    /**
     * 已启用 + 可选来源 + 可选关键字 Spec。
     *
     * @param keyword    关键字；空则不加 like（浏览）
     * @param sourceEnum 来源；空则不限
     * @return Specification
     */
    private Specification<ProcedureDefinition> buildKeywordSourceSpec(String keyword, String sourceEnum) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String trimmed = keyword.trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                // 名称 / 主编码 / 国临码 / 医保码 / 拼音 / 五笔
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PROCEDURE_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.CLINIC_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
                // B.2.1：检索下拉精确全等置顶
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.PROCEDURE_CODE),
                    root.get(FieldNameConstants.CLINIC_CODE),
                    root.get(FieldNameConstants.YB_NO),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
            } else if (cq != null) {
                // 无关键字：主键倒序浏览
                cq.orderBy(cb.desc(root.get(FieldNameConstants.ID)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 实体 → 国临检索项（含挂靠 AD）。
     *
     * @param entity 手术定义
     * @return 检索项
     */
    private ProcedureDefinitionSearchItemQueryModel toSearchItem(ProcedureDefinition entity) {
        ProcedureDefinitionSearchItemQueryModel model = new ProcedureDefinitionSearchItemQueryModel();
        model.setId(entity.getId());
        model.setProcedureCode(entity.getProcedureCode());
        model.setName(entity.getName());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setStatusEnum(entity.getStatusEnum());
        model.setYbNo(StringUtils.trimToNull(entity.getYbNo()));
        model.setActivityDefinitionId(entity.getActivityDefinitionId());
        return model;
    }

    /**
     * 维护分页 Spec：软删过滤 + 可选来源 + 关键字（含拼音五笔）。
     *
     * <p>DOC-CONFLICT：DM §6.2.1 表列关键字未写拼音五笔，但 DM §2 高频检索与 AS BR-POC-011 要求纳入；
     * 本实现采纳 §2 + AS 口径。</p>
     *
     * @param keyword    关键字
     * @param sourceEnum 来源筛选
     * @return Specification
     */
    private Specification<ProcedureDefinition> buildPageSpec(String keyword, String sourceEnum) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            if (StringUtils.isNotBlank(keyword)) {
                String trimmed = keyword.trim();
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PROCEDURE_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.CLINIC_CODE)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
                // B.2.1：维护分页精确全等置顶，次级主键倒序
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    trimmed,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.PROCEDURE_CODE),
                    root.get(FieldNameConstants.CLINIC_CODE),
                    root.get(FieldNameConstants.YB_NO),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
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
     * 实体 → 维护分页行（含挂靠 AD）。
     *
     * @param entity 手术定义
     * @return 分页行模型
     */
    private ProcedureDefinitionPageItemQueryModel toPageItem(ProcedureDefinition entity) {
        ProcedureDefinitionPageItemQueryModel model = new ProcedureDefinitionPageItemQueryModel();
        model.setId(entity.getId());
        model.setProcedureCode(entity.getProcedureCode());
        model.setName(entity.getName());
        model.setSourceEnum(entity.getSourceEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setYbFlag(entity.getYbFlag());
        model.setYbNo(entity.getYbNo());
        model.setClinicCode(entity.getClinicCode());
        model.setTypeCode(entity.getTypeCode());
        model.setDescription(entity.getDescription());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setYbMatchFlag(entity.getYbMatchFlag());
        model.setActivityDefinitionId(entity.getActivityDefinitionId());
        return model;
    }
}
