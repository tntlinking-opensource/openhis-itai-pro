/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.commonresult.service;

import com.zxplat.plugin.peis.domain.commonresult.entity.PeisCommonResult;
import com.zxplat.plugin.peis.domain.commonresult.queries.PeisCommonResultQuery;
import com.zxplat.plugin.peis.domain.commonresult.queries.model.PeisCommonResultModel;
import com.zxplat.plugin.peis.domain.commonresult.repository.PeisCommonResultRepository;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * PEIS 常见结果读侧查询服务（DM-PEIS-003）。
 *
 * <p>仅本聚合 {@link PeisCommonResultRepository}（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisCommonResultQueryService {

    private final PeisCommonResultRepository repository;

    /**
     * 按主键读取常见结果模板。
     *
     * @param id 主键
     * @return 读模型
     */
    public Optional<PeisCommonResultModel> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByIdAndIsDeletedFalse(id).map(this::toSearchModel);
    }

    /**
     * 按小项列出启用中的常见结果（DES-03）。
     *
     * @param odId 小项观察定义主键
     * @return 按 sortNum 升序列表
     */
    public List<PeisCommonResultModel> listByOdId(Long odId) {
        // status=active + 软删 → 查询 → Model
        if (odId == null) {
            return new ArrayList<>();
        }
        List<PeisCommonResult> rows = repository.findByObservationDefinitionIdAndStatusEnumAndIsDeletedFalse(
            odId, PeisCatalogConstants.COMMON_RESULT_STATUS_ACTIVE);
        List<PeisCommonResultModel> models = new ArrayList<>();
        for (PeisCommonResult row : rows) {
            models.add(toListModel(row));
        }
        models.sort(Comparator.comparing(
            PeisCommonResultModel::getSortNum, Comparator.nullsLast(Integer::compareTo)));
        return models;
    }

    /**
     * 分页检索常见结果（CFG 维护）。
     *
     * @param query    过滤条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<PeisCommonResultModel> search(PeisCommonResultQuery query, Pageable pageable) {
        // status=active + 软删 → 查询 → Model；关键字时剥掉 Sort 保留精确优先 ORDER BY（B.2.1）
        Specification<PeisCommonResult> spec = buildSearchSpec(query);
        Pageable effective = pageable;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())
            && pageable != null && pageable.getSort().isSorted()) {
            effective = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.findAll(spec, effective).map(this::toSearchModel);
    }

    private Specification<PeisCommonResult> buildSearchSpec(PeisCommonResultQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getTenantId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
                }
                if (query.getObservationDefinitionId() != null) {
                    predicates.add(cb.equal(root.get("observationDefinitionId"), query.getObservationDefinitionId()));
                }
                if (query.getDeptOrgId() != null) {
                    predicates.add(cb.equal(root.get("deptOrgId"), query.getDeptOrgId()));
                }
                if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.DISPLAY)), like));
                    // B.2.1：展示名精确全等置顶
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        List.of(cb.asc(root.get("sortNum")), cb.desc(root.get(FieldNameConstants.ID))),
                        root.get(FieldNameConstants.DISPLAY));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PeisCommonResultModel toListModel(PeisCommonResult entity) {
        PeisCommonResultModel model = new PeisCommonResultModel();
        model.setId(entity.getId());
        model.setDisplay(entity.getDisplay());
        model.setValueString(entity.getValueString());
        model.setInterpretationCode(entity.getInterpretationCode());
        model.setSortNum(entity.getSortNum());
        return model;
    }

    private PeisCommonResultModel toSearchModel(PeisCommonResult entity) {
        PeisCommonResultModel model = toListModel(entity);
        model.setStatusEnum(entity.getStatusEnum());
        model.setDeptOrgId(entity.getDeptOrgId());
        model.setObservationDefinitionId(entity.getObservationDefinitionId());
        return model;
    }
}
