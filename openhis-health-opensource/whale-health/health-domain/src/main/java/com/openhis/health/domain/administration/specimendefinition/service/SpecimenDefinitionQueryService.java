/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.specimendefinition.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.administration.specimendefinition.entity.SpecimenDefinition;
import com.openhis.health.domain.administration.specimendefinition.queries.PageSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.SearchSpecimenDefinitionQuery;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionBatchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionByBusNoQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionDetailQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionPageItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.repository.SpecimenDefinitionRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 样本定义读侧查询服务（DM-ADM-014）。
 *
 * <p>提供维护页分页、详情、关键字检索、按业务编码解析与批量装载；默认排除逻辑删除记录，
 * 关键字检索默认仅返回启用态（BR-SPDEF-005）。</p>
 * <p>聚合边界：仅本聚合 {@link SpecimenDefinitionRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class SpecimenDefinitionQueryService {

    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final SpecimenDefinitionRepository repository;

    /**
     * 样本定义分页查询；未指定排序时按最近更新时间、主键降序。
     *
     * @param query 筛选条件，可空
     * @param pageable 分页参数，可空
     * @return 分页结果
     */
    public Page<SpecimenDefinitionPageItemQueryModel> pageSpecimenDefinition(PageSpecimenDefinitionQuery query, Pageable pageable) {
        PageSpecimenDefinitionQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new PageSpecimenDefinitionQuery();
        }
        // 构建查询条件：解析状态、种类与关键字过滤口径
        final String keywordTrim = trimToEmpty(safeQuery.getKeyword());
        final String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keywordTrim);
        String statusEnumFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getStatusEnum())) {
            statusEnumFilter = safeQuery.getStatusEnum().trim();
        }
        String typeEnumFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getTypeEnum())) {
            typeEnumFilter = safeQuery.getTypeEnum().trim();
        }
        final String statusEnumForSpec = statusEnumFilter;
        final String typeEnumForSpec = typeEnumFilter;
        Specification<SpecimenDefinition> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            // 默认排除逻辑删除记录
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (statusEnumForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnumForSpec));
            }
            if (typeEnumForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), typeEnumForSpec));
            }
            if (StringUtils.isNotEmpty(keywordTrim)) {
                preds.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
                // B.2.1：精确全等置顶，次级仍按更新时间、主键倒序
                List<Order> secondary = List.of(
                    cb.desc(root.get(FieldNameConstants.UPDATED_AT)),
                    cb.desc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    keywordTrim,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.BUS_NO),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
        // 单次 SQL 分页：无关键字时默认 updatedAt、id 降序；有关键字时 Spec 写精确优先（B.2.1）
        Pageable effectivePageable = pageable;
        Sort defaultSort = Sort.by(
            Sort.Order.desc(FieldNameConstants.UPDATED_AT),
            Sort.Order.desc(FieldNameConstants.ID));
        if (StringUtils.isNotEmpty(keywordTrim)) {
            if (pageable == null) {
                effectivePageable = PageRequest.of(0, DEFAULT_PAGE_SIZE);
            } else {
                effectivePageable = this.unsortPageableIfKeyword(true, pageable);
            }
        } else if (pageable == null) {
            effectivePageable = PageRequest.of(0, DEFAULT_PAGE_SIZE, defaultSort);
        } else if (pageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
        }
        // 组装 QueryModel：无写库
        return repository.findAll(spec, effectivePageable).map(this::toPageItem);
    }

    /**
     * 按主键查询样本定义详情。
     *
     * @param id 主键
     * @return 详情；不存在或已删时返回 {@code null}
     */
    public SpecimenDefinitionDetailQueryModel getSpecimenDefinition(Long id) {
        if (id == null) {
            return null;
        }
        // 构建查询条件：主键精确匹配
        SpecimenDefinition entity = repository.findById(id).orElse(null);
        // 业务分支：已逻辑删除视为不存在
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            return null;
        }
        return toDetail(entity);
    }

    /**
     * 关键字检索样本定义；默认仅返回启用态（BR-SPDEF-005）。
     *
     * @param query 检索条件，可空
     * @return 命中列表，无命中返回空集合
     */
    public List<SpecimenDefinitionSearchItemQueryModel> searchByKeyWord(SearchSpecimenDefinitionQuery query) {
        SearchSpecimenDefinitionQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new SearchSpecimenDefinitionQuery();
        }
        int limit = DEFAULT_SEARCH_LIMIT;
        if (safeQuery.getLimit() != null && safeQuery.getLimit() > 0) {
            limit = safeQuery.getLimit();
        }
        // 构建查询条件：默认仅 ACTIVE，调用方可显式放宽状态
        String statusEnumFilter = PublicationStatus.ACTIVE.getCode();
        if (StringUtils.isNotEmpty(safeQuery.getStatusEnum())) {
            statusEnumFilter = safeQuery.getStatusEnum().trim();
        }
        final String statusEnumForSpec = statusEnumFilter;
        final String keywordTrim = trimToEmpty(safeQuery.getKeyword());
        final String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keywordTrim);
        String typeEnumFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getTypeEnum())) {
            typeEnumFilter = safeQuery.getTypeEnum().trim();
        }
        final String typeEnumForSpec = typeEnumFilter;
        Specification<SpecimenDefinition> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            preds.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnumForSpec));
            if (typeEnumForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), typeEnumForSpec));
            }
            if (StringUtils.isNotEmpty(keywordTrim)) {
                preds.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
                // B.2.1：检索下拉精确全等置顶，次级主键升序
                List<Order> secondary = List.of(cb.asc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    keywordTrim,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.BUS_NO),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR));
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
        // 有关键字时无 Sort，保留 Spec 精确优先；无关键字时按主键升序截断
        Pageable searchPageable;
        if (StringUtils.isNotEmpty(keywordTrim)) {
            searchPageable = PageRequest.of(0, limit);
        } else {
            searchPageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        }
        Page<SpecimenDefinition> page = repository.findAll(spec, searchPageable);
        List<SpecimenDefinitionSearchItemQueryModel> result = new ArrayList<>();
        for (SpecimenDefinition entity : page.getContent()) {
            result.add(toSearchItem(entity));
        }
        return result;
    }

    /**
     * 按业务编码精确查询，供下游绑定校验与名称回填。
     *
     * @param busNo 业务编码
     * @return 投影；不存在或已删时返回 {@code null}
     */
    public SpecimenDefinitionByBusNoQueryModel getByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return null;
        }
        // 构建查询条件：业务编码 trim 后精确匹配
        SpecimenDefinition entity = repository.findByBusNo(busNo.trim()).orElse(null);
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            return null;
        }
        SpecimenDefinitionByBusNoQueryModel model = new SpecimenDefinitionByBusNoQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        return model;
    }

    /**
     * 按主键集合批量装载；部分缺失时返回命中子集。
     *
     * @param ids 主键集合
     * @return 未删记录列表，入参空时返回空集合
     */
    public List<SpecimenDefinitionBatchItemQueryModel> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        // 单次 SQL：id in (...) 批量装载
        List<SpecimenDefinition> entities = repository.findByIdIn(ids);
        List<SpecimenDefinitionBatchItemQueryModel> result = new ArrayList<>();
        for (SpecimenDefinition entity : entities) {
            // 业务分支：跳过已逻辑删除记录
            if (Boolean.TRUE.equals(entity.getIsDeleted())) {
                continue;
            }
            SpecimenDefinitionBatchItemQueryModel model = new SpecimenDefinitionBatchItemQueryModel();
            model.setId(entity.getId());
            model.setBusNo(entity.getBusNo());
            model.setName(entity.getName());
            model.setTypeEnum(entity.getTypeEnum());
            model.setStatusEnum(entity.getStatusEnum());
            result.add(model);
        }
        return result;
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
     * 标本定义实体 → 分页列表项读模型。
     *
     * @param entity JPA 实体
     * @return 分页行 QueryModel
     */
    private SpecimenDefinitionPageItemQueryModel toPageItem(SpecimenDefinition entity) {
        SpecimenDefinitionPageItemQueryModel model = new SpecimenDefinitionPageItemQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    /**
     * 标本定义实体 → 详情读模型。
     *
     * @param entity JPA 实体
     * @return 详情 QueryModel
     */
    private SpecimenDefinitionDetailQueryModel toDetail(SpecimenDefinition entity) {
        SpecimenDefinitionDetailQueryModel model = new SpecimenDefinitionDetailQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        return model;
    }

    /**
     * 标本定义实体 → 检索下拉项读模型。
     *
     * @param entity JPA 实体
     * @return 搜索项 QueryModel
     */
    private SpecimenDefinitionSearchItemQueryModel toSearchItem(SpecimenDefinition entity) {
        SpecimenDefinitionSearchItemQueryModel model = new SpecimenDefinitionSearchItemQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setTypeEnum(entity.getTypeEnum());
        model.setStatusEnum(entity.getStatusEnum());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        return model;
    }
}
