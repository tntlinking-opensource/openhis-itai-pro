/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.labdevice.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.administration.labdevice.entity.LabDevice;
import com.openhis.health.domain.administration.labdevice.queries.PageLabDeviceQuery;
import com.openhis.health.domain.administration.labdevice.queries.SearchLabDeviceQuery;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDevicePageItemQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceSearchItemQueryModel;
import com.openhis.health.domain.administration.labdevice.repository.LabDeviceRepository;
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
import java.util.List;

/**
 * 检验设备读侧查询服务（DM-ADM-015）。
 *
 * <p>聚合边界：仅本聚合 {@link LabDeviceRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class LabDeviceQueryService {

    private static final int DEFAULT_SEARCH_LIMIT = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final LabDeviceRepository repository;

    /**
     * 检验设备分页查询。
     *
     * @param query 筛选条件
     * @param pageable 分页参数
     * @return 分页结果
     */
    public Page<LabDevicePageItemQueryModel> pageLabDevice(PageLabDeviceQuery query, Pageable pageable) {
        PageLabDeviceQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new PageLabDeviceQuery();
        }
        final String keywordTrim = trimToEmpty(safeQuery.getKeyword());
        final String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keywordTrim);
        String statusEnumFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getStatusEnum())) {
            statusEnumFilter = safeQuery.getStatusEnum().trim();
        }
        String categoryCodeFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getCategoryCode())) {
            categoryCodeFilter = safeQuery.getCategoryCode().trim();
        }
        final String statusEnumForSpec = statusEnumFilter;
        final String categoryCodeForSpec = categoryCodeFilter;
        final Long orgIdForSpec = safeQuery.getOrgId();
        Specification<LabDevice> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (statusEnumForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnumForSpec));
            }
            if (categoryCodeForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), categoryCodeForSpec));
            }
            // 归属科室筛选：维护页按科室查设备台账
            if (orgIdForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.ORG_ID), orgIdForSpec));
            }
            if (StringUtils.isNotEmpty(keywordTrim)) {
                preds.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
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
        return repository.findAll(spec, effectivePageable).map(this::toPageItem);
    }

    /**
     * 列出启用中的检验设备（合管配置左栏等场景）。
     *
     * @param keyword 关键字，可空
     * @param limit 条数上限
     * @return 设备列表
     */
    public List<LabDevicePageItemQueryModel> listActiveDevices(String keyword, Integer limit) {
        SearchLabDeviceQuery query = new SearchLabDeviceQuery();
        query.setKeyword(keyword);
        query.setStatusEnum(PublicationStatus.ACTIVE.getCode());
        query.setLimit(limit);
        List<LabDeviceSearchItemQueryModel> searchItems = this.searchByKeyWord(query);
        List<LabDevicePageItemQueryModel> result = new ArrayList<>();
        for (LabDeviceSearchItemQueryModel item : searchItems) {
            LabDevicePageItemQueryModel model = new LabDevicePageItemQueryModel();
            model.setId(item.getId());
            model.setBusNo(item.getBusNo());
            model.setName(item.getName());
            model.setStatusEnum(item.getStatusEnum());
            model.setCategoryCode(item.getCategoryCode());
            model.setPyStr(item.getPyStr());
            model.setWbStr(item.getWbStr());
            result.add(model);
        }
        return result;
    }

    /**
     * 按主键查询检验设备详情。
     *
     * @param id 主键
     * @return 详情；不存在或已删时返回 {@code null}
     */
    public LabDeviceDetailQueryModel getLabDevice(Long id) {
        if (id == null) {
            return null;
        }
        LabDevice entity = repository.findById(id).orElse(null);
        if (entity == null || Boolean.TRUE.equals(entity.getIsDeleted())) {
            return null;
        }
        return toDetail(entity);
    }

    /**
     * 关键字检索检验设备；默认仅返回启用态。
     *
     * @param query 检索条件
     * @return 命中列表
     */
    public List<LabDeviceSearchItemQueryModel> searchByKeyWord(SearchLabDeviceQuery query) {
        SearchLabDeviceQuery safeQuery = query;
        if (safeQuery == null) {
            safeQuery = new SearchLabDeviceQuery();
        }
        int limit = DEFAULT_SEARCH_LIMIT;
        if (safeQuery.getLimit() != null && safeQuery.getLimit() > 0) {
            limit = safeQuery.getLimit();
        }
        String statusEnumFilter = PublicationStatus.ACTIVE.getCode();
        if (StringUtils.isNotEmpty(safeQuery.getStatusEnum())) {
            statusEnumFilter = safeQuery.getStatusEnum().trim();
        }
        final String statusEnumForSpec = statusEnumFilter;
        final String keywordTrim = trimToEmpty(safeQuery.getKeyword());
        final String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keywordTrim);
        String categoryCodeFilter = null;
        if (StringUtils.isNotEmpty(safeQuery.getCategoryCode())) {
            categoryCodeFilter = safeQuery.getCategoryCode().trim();
        }
        final String categoryCodeForSpec = categoryCodeFilter;
        Specification<LabDevice> spec = (root, cq, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            preds.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnumForSpec));
            if (categoryCodeForSpec != null) {
                preds.add(cb.equal(root.get(FieldNameConstants.CATEGORY_CODE), categoryCodeForSpec));
            }
            if (StringUtils.isNotEmpty(keywordTrim)) {
                preds.add(cb.or(
                    cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), likeIc),
                    cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), likeIc)
                ));
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
        Pageable searchPageable;
        if (StringUtils.isNotEmpty(keywordTrim)) {
            searchPageable = PageRequest.of(0, limit);
        } else {
            searchPageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.ASC, FieldNameConstants.ID));
        }
        Page<LabDevice> page = repository.findAll(spec, searchPageable);
        List<LabDeviceSearchItemQueryModel> result = new ArrayList<>();
        for (LabDevice entity : page.getContent()) {
            result.add(toSearchItem(entity));
        }
        return result;
    }

    /**
     * 关键字检索时去掉 Spring 默认 Sort，避免覆盖精确优先排序。
     *
     * @param keywordPresent 是否有关键字
     * @param pageable 原始分页
     * @return 去 Sort 后的分页；无关键字时原样返回
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
     * 实体投影为分页列表读模型。
     *
     * @param entity 检验设备实体
     * @return 分页行读模型
     */
    private LabDevicePageItemQueryModel toPageItem(LabDevice entity) {
        LabDevicePageItemQueryModel model = new LabDevicePageItemQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryCode(entity.getCategoryCode());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setOrgId(entity.getOrgId());
        model.setLocationId(entity.getLocationId());
        model.setModelNumber(entity.getModelNumber());
        model.setSerialNumber(entity.getSerialNumber());
        model.setManufacturerId(entity.getManufacturerId());
        model.setManufactureDate(entity.getManufactureDate());
        model.setExpirationDate(entity.getExpirationDate());
        model.setDescription(entity.getDescription());
        model.setUpdatedAt(entity.getUpdatedAt());
        return model;
    }

    /**
     * 实体投影为详情读模型。
     *
     * @param entity 检验设备实体
     * @return 详情读模型
     */
    private LabDeviceDetailQueryModel toDetail(LabDevice entity) {
        LabDeviceDetailQueryModel model = new LabDeviceDetailQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryCode(entity.getCategoryCode());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        model.setOrgId(entity.getOrgId());
        model.setLocationId(entity.getLocationId());
        model.setModelNumber(entity.getModelNumber());
        model.setSerialNumber(entity.getSerialNumber());
        model.setManufacturerId(entity.getManufacturerId());
        model.setManufactureDate(entity.getManufactureDate());
        model.setExpirationDate(entity.getExpirationDate());
        model.setDescription(entity.getDescription());
        return model;
    }

    /**
     * 实体投影为关键字检索读模型。
     *
     * @param entity 检验设备实体
     * @return 检索行读模型
     */
    private LabDeviceSearchItemQueryModel toSearchItem(LabDevice entity) {
        LabDeviceSearchItemQueryModel model = new LabDeviceSearchItemQueryModel();
        model.setId(entity.getId());
        model.setBusNo(entity.getBusNo());
        model.setName(entity.getName());
        model.setStatusEnum(entity.getStatusEnum());
        model.setCategoryCode(entity.getCategoryCode());
        model.setPyStr(entity.getPyStr());
        model.setWbStr(entity.getWbStr());
        return model;
    }
}
