/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.service;

import com.openhis.health.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.openhis.health.domain.clinical.proceduredefinition.queries.ProcedureDefinitionPageQuery;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionPageItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionSearchItemQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ProcedureDefinitionConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
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
 * 手术操作定义读侧查询服务。
 */
@Service
@RequiredArgsConstructor
public class ProcedureDefinitionQueryService {

    private final ProcedureDefinitionRepository repository;

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

    public ProcedureDefinitionDetailQueryModel getOne(Long id) {
        ProcedureDefinition entity = repository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.PROCEDURE_DEFINITION_NOT_FOUND, "手术定义不存在"));
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
        return model;
    }

    public List<ProcedureDefinitionSearchItemQueryModel> searchClinicalProcV3ByKeyword(String keyword) {
        return searchByKeywordAndSource(keyword, ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3);
    }

    /**
     * 国临手术码 → 医保手术码（4401 用）。
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
        if (existingClinical && !currentClinical) {
            best.put(key, entity);
        }
    }

    private List<ProcedureDefinitionSearchItemQueryModel> searchByKeywordAndSource(String keyword, String sourceEnum) {
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<>();
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        Specification<ProcedureDefinition> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), PublicationStatus.ACTIVE.getCode()));
            if (StringUtils.isNotBlank(sourceEnum)) {
                predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_ENUM), sourceEnum.trim()));
            }
            predicates.add(cb.or(
                cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.PROCEDURE_CODE)), likeIc),
                cb.like(cb.lower(root.get(FieldNameConstants.CLINIC_CODE)), likeIc),
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
                root.get(FieldNameConstants.PY_STR),
                root.get(FieldNameConstants.WB_STR));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<ProcedureDefinitionSearchItemQueryModel> models = new ArrayList<>();
        for (ProcedureDefinition entity : repository.findAll(specification)) {
            ProcedureDefinitionSearchItemQueryModel model = new ProcedureDefinitionSearchItemQueryModel();
            model.setId(entity.getId());
            model.setProcedureCode(entity.getProcedureCode());
            model.setName(entity.getName());
            model.setPyStr(entity.getPyStr());
            model.setWbStr(entity.getWbStr());
            model.setStatusEnum(entity.getStatusEnum());
            models.add(model);
        }
        return models;
    }

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
                    cb.like(cb.lower(root.get(FieldNameConstants.YB_NO)), likeIc)
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
                    root.get(FieldNameConstants.YB_NO));
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
        return model;
    }
}
