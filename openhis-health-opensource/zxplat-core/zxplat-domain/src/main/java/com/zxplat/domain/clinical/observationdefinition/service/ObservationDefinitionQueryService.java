/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.observationdefinition.service;

import com.zxplat.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.zxplat.domain.clinical.observationdefinition.queries.ObservationDefinitionQuery;
import com.zxplat.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.zxplat.domain.clinical.observationdefinition.repository.ObservationDefinitionRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.ObservationDefinitionConstants;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 临床观察定义读侧查询服务（DM-CLN-005）。
 *
 * <p>仅本聚合 {@link ObservationDefinitionRepository}；跨聚合组装（如 AOL 展示 enrich）由 AppService 编排（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class ObservationDefinitionQueryService {

    private final ObservationDefinitionRepository repository;

    /**
     * 按主键读取观察定义；不存在则拒绝。
     *
     * @param id 主键
     * @return 读模型
     */
    public ObservationDefinitionModel getById(Long id) {
        ObservationDefinition entity = repository.findByIdAndIsDeletedFalse(id).orElseThrow(
            () -> new BusinessException(
                ErrorCodeConstants.OBSERVATION_DEFINITION_NOT_FOUND, "观察定义不存在"));
        return toModel(entity);
    }

    /**
     * 按主键可选加载（写侧/他聚合 DomainService 门禁用，B.6.4）。
     *
     * @param id 主键
     * @return 未软删实体
     */
    public Optional<ObservationDefinition> findOptionalById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findByIdAndIsDeletedFalse(id);
    }

    /**
     * 分页检索观察定义（CFG-03）。
     *
     * @param query    过滤条件
     * @param pageable 分页
     * @return 分页读模型
     */
    public Page<ObservationDefinitionModel> search(ObservationDefinitionQuery query, Pageable pageable) {
        Specification<ObservationDefinition> spec = buildSearchSpec(query);
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内「精确优先」ORDER BY（B.2.1）
        Pageable effective = this.unsortPageableIfKeyword(
            query != null && StringUtils.isNotEmpty(query.getKeyword()), pageable);
        return repository.findAll(spec, effective).map(this::toModel);
    }

    /**
     * 按分类列出启用中的观察定义（如 DES 分科结论下拉）。
     *
     * @param categoryCode 分类码
     * @return 按 id 升序列表
     */
    public List<ObservationDefinitionModel> listByCategory(String categoryCode) {
        if (StringUtils.isEmpty(categoryCode)) {
            return new ArrayList<>();
        }
        List<ObservationDefinition> rows = repository.findByCategoryCodeAndIsDeletedFalse(categoryCode.trim());
        List<ObservationDefinitionModel> models = new ArrayList<>();
        for (ObservationDefinition row : rows) {
            if (!ObservationDefinitionConstants.isActiveStatus(row.getStatusEnum())) {
                continue;
            }
            models.add(toModel(row));
        }
        models.sort(Comparator.comparing(ObservationDefinitionModel::getId, Comparator.nullsLast(Long::compareTo)));
        return models;
    }

    /**
     * 批量按主键读取未软删行（AppService enrich 用）。
     *
     * @param ids 主键集合
     * @return 读模型列表（跳过已删）
     */
    public List<ObservationDefinitionModel> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        List<ObservationDefinitionModel> models = new ArrayList<>();
        for (ObservationDefinition row : repository.findByIdIn(ids)) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            models.add(toModel(row));
        }
        return models;
    }

    private Specification<ObservationDefinition> buildSearchSpec(ObservationDefinitionQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getTenantId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
                }
                if (StringUtils.isNotEmpty(query.getCategoryCode())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), query.getCategoryCode().trim()));
                }
                if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String trimmed = query.getKeyword().trim();
                    String like = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get(FieldNameConstants.CODE)), like),
                        cb.like(cb.lower(root.get(FieldNameConstants.DISPLAY)), like)
                    ));
                    // B.2.1：观察定义关键字精确全等置顶
                    List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.ID)));
                    KeywordExactFirstOrderSupport.applyExactFirstOrder(
                        cq,
                        cb,
                        trimmed,
                        secondary,
                        root.get(FieldNameConstants.CODE),
                        root.get(FieldNameConstants.DISPLAY));
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
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
     * 将实体投影为读模型（含执行科室，供 PEIS 目录列表展示）。
     *
     * @param entity 观察定义实体
     * @return 读模型；entity 为 null 时不应调用
     */
    private ObservationDefinitionModel toModel(ObservationDefinition entity) {
        ObservationDefinitionModel model = new ObservationDefinitionModel();
        model.setId(entity.getId());
        model.setCode(entity.getCode());
        model.setDisplay(entity.getDisplay());
        model.setValueTypeEnum(entity.getValueTypeEnum());
        model.setUnitCode(entity.getUnitCode());
        model.setCategoryCode(entity.getCategoryCode());
        model.setStatusEnum(entity.getStatusEnum());
        model.setOrgId(entity.getOrgId());
        return model;
    }
}
