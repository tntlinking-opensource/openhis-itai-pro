/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargediscountconfig.service;

import com.openhis.health.domain.custom.chargediscountconfig.entity.ChargeDiscountConfig;
import com.openhis.health.domain.custom.chargediscountconfig.queries.ChargeDiscountConfigPageQuery;
import com.openhis.health.domain.custom.chargediscountconfig.queries.DiscountProductResolveItem;
import com.openhis.health.domain.custom.chargediscountconfig.queries.model.ChargeDiscountConfigQueryModel;
import com.openhis.health.domain.custom.chargediscountconfig.repository.ChargeDiscountConfigRepository;
import com.openhis.health.domain.share.constants.ChargeDiscountConfigConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.ChargeDiscountConfigStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 收费折扣配置读侧查询服务（DM-CUS-016 §6.2）。
 *
 * <p>职责：分页维护列表、按 id 加载、能力门控探测、单项&gt;大类命中解析（含批量）。</p>
 * <p>聚合边界：仅本聚合 {@link ChargeDiscountConfigRepository}；不注入外域 Repository。</p>
 */
@Service
@RequiredArgsConstructor
public class ChargeDiscountConfigQueryService {

    /** 本聚合仓储；禁止注入外域 Repository（B.6.4）。 */
    private final ChargeDiscountConfigRepository repository;

    /**
     * 分页查询配置（默认未删；可选表名/状态/行形态/关键字）。
     *
     * @param query 筛选条件；可空
     * @return 分页读模型
     */
    public Page<ChargeDiscountConfigQueryModel> pageChargeDiscountConfigs(ChargeDiscountConfigPageQuery query) {
        ChargeDiscountConfigPageQuery effectiveQuery;
        // 无条件时按默认分页拉全量未删列表
        if (query == null) {
            effectiveQuery = new ChargeDiscountConfigPageQuery();
        } else {
            effectiveQuery = query;
        }
        Pageable pageable = PageRequest.of(
            effectiveQuery.springPageIndex(),
            effectiveQuery.getBoundedPageSize(),
            Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT)
        );
        Specification<ChargeDiscountConfig> specification = this.buildPageSpecification(effectiveQuery);
        Page<ChargeDiscountConfig> page = repository.findAll(specification, pageable);
        return page.map(this::toQueryModel);
    }

    /**
     * 按主键加载未删配置。
     *
     * @param id 配置主键
     * @return 读模型
     * @throws BusinessException 不存在时
     */
    public ChargeDiscountConfigQueryModel getChargeDiscountConfig(Long id) {
        // 主键缺失无法定位配置行
        if (id == null) {
            throw new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_INVALID_ARGUMENT, "配置主键不能为空");
        }
        ChargeDiscountConfig entity = repository.findByIdAndIsDeletedFalse(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.CHARGE_DISCOUNT_CONFIG_NOT_FOUND, "收费折扣配置不存在"));
        return this.toQueryModel(entity);
    }

    /**
     * 本院是否启用打折能力（BR-CDC-006 真源）：存在 ≥1 条未删 ACTIVE 配置。
     *
     * @return true 表示启用打折 UI/落账相关能力
     */
    public boolean isDiscountFeatureEnabled() {
        long activeCount = repository.countByStatusEnumAndIsDeletedFalse(
            ChargeDiscountConfigStatus.ACTIVE.getCode());
        return activeCount > 0L;
    }

    /**
     * 解析单品命中配置：先单项、再大类；均未命中返回 empty（BR-CDC-005）。
     *
     * @param productTable 产品表名
     * @param productId    产品主键
     * @param categoryCode 目录大类码；可空则仅试单项
     * @return 命中读模型或 empty
     */
    public Optional<ChargeDiscountConfigQueryModel> resolveDiscountConfigForProduct(
        String productTable,
        Long productId,
        String categoryCode
    ) {
        // 缺表名或产品 id 时无法命中，视为不可打折
        if (StringUtils.isEmpty(productTable) || productId == null) {
            return Optional.empty();
        }
        String active = ChargeDiscountConfigStatus.ACTIVE.getCode();
        // 单项优先：同表同 product_id 且 ACTIVE
        Optional<ChargeDiscountConfig> productHit =
            repository.findFirstByProductTableAndProductIdAndCategoryCodeIsNullAndStatusEnumAndIsDeletedFalse(
                productTable, productId, active);
        if (productHit.isPresent()) {
            return Optional.of(this.toQueryModel(productHit.get()));
        }
        // 无大类码则无法回落大类行
        if (StringUtils.isEmpty(categoryCode)) {
            return Optional.empty();
        }
        // 大类回落：同表同 category_code 且 ACTIVE
        Optional<ChargeDiscountConfig> categoryHit =
            repository.findFirstByProductTableAndCategoryCodeAndProductIdIsNullAndStatusEnumAndIsDeletedFalse(
                productTable, categoryCode.trim(), active);
        // 大类也未配置 → 不可打折
        if (categoryHit.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(this.toQueryModel(categoryHit.get()));
    }

    /**
     * 批量解析命中，避免收银按行 N+1（算法同 §4.1）。
     *
     * <p>返回 Map 键为 {@code productTable + "#" + productId}；未命中的产品不出现在 Map 中。</p>
     *
     * @param items 产品行（表+id+可选大类）
     * @return 命中配置映射
     */
    public Map<String, ChargeDiscountConfigQueryModel> mapDiscountConfigsForProducts(
        List<DiscountProductResolveItem> items
    ) {
        Map<String, ChargeDiscountConfigQueryModel> result = new HashMap<>();
        // 空入参直接返回，避免无意义打库
        if (items == null || items.isEmpty()) {
            return result;
        }

        Set<String> tables = new HashSet<>();
        for (DiscountProductResolveItem item : items) {
            // 跳过缺键行，不参与批量索引
            if (item == null || StringUtils.isEmpty(item.getProductTable()) || item.getProductId() == null) {
                continue;
            }
            tables.add(item.getProductTable());
        }
        // 无有效产品键则无需查库
        if (tables.isEmpty()) {
            return result;
        }

        // 一次拉回相关表下全部 ACTIVE 配置，内存按单项>大类择优
        List<ChargeDiscountConfig> candidates = repository.findByProductTableInAndStatusEnumAndIsDeletedFalse(
            tables, ChargeDiscountConfigStatus.ACTIVE.getCode());

        Map<String, ChargeDiscountConfig> productRowIndex = new HashMap<>();
        Map<String, ChargeDiscountConfig> categoryRowIndex = new HashMap<>();
        for (ChargeDiscountConfig row : candidates) {
            // 建单项索引
            if (row.isProductRow()) {
                productRowIndex.put(this.buildProductKey(row.getProductTable(), row.getProductId()), row);
            } else if (row.isCategoryRow()) {
                // 建大类索引（与单项并存时由调用侧择优）
                categoryRowIndex.put(this.buildCategoryKey(row.getProductTable(), row.getCategoryCode()), row);
            }
        }

        for (DiscountProductResolveItem item : items) {
            // 跳过缺键行
            if (item == null || StringUtils.isEmpty(item.getProductTable()) || item.getProductId() == null) {
                continue;
            }
            String productKey = this.buildProductKey(item.getProductTable(), item.getProductId());
            ChargeDiscountConfig hit = productRowIndex.get(productKey);
            // 单项未命中且带大类码时回落大类
            if (hit == null && !StringUtils.isEmpty(item.getCategoryCode())) {
                hit = categoryRowIndex.get(
                    this.buildCategoryKey(item.getProductTable(), item.getCategoryCode().trim()));
            }
            // 命中则写入结果；未命中不占位，调用方按 missing key 视为原价
            if (hit != null) {
                result.put(productKey, this.toQueryModel(hit));
            }
        }
        return result;
    }

    /**
     * 组装分页 Specification（软删 + 可选筛选）。
     *
     * @param query 查询条件
     * @return Specification
     */
    private Specification<ChargeDiscountConfig> buildPageSpecification(ChargeDiscountConfigPageQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 按产品表过滤（配置页目录 Tab）
            if (!StringUtils.isEmpty(query.getProductTable())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PRODUCT_TABLE), query.getProductTable()));
            }
            // 按启停状态过滤
            if (!StringUtils.isEmpty(query.getStatusEnum())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
            }
            // 按院区过滤（首版多数为空）
            if (query.getOrgId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), query.getOrgId()));
            }
            // 行形态：大类=product_id 空；单项=category_code 空（不依赖 scope 列）
            if (ChargeDiscountConfigConstants.ROW_SHAPE_CATEGORY.equals(query.getRowShape())) {
                predicates.add(cb.isNull(root.get(FieldNameConstants.PRODUCT_ID)));
            } else if (ChargeDiscountConfigConstants.ROW_SHAPE_PRODUCT.equals(query.getRowShape())) {
                predicates.add(cb.isNull(root.get(FieldNameConstants.CATEGORY_CODE)));
            }
            // 关键字：匹配大类码或备注
            if (!StringUtils.isEmpty(query.getKeyword())) {
                String pattern = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                    cb.like(root.get(FieldNameConstants.CATEGORY_CODE), pattern),
                    cb.like(root.get(FieldNameConstants.REMARK), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 实体转读模型（目录名等展示 enrich 由应用层补）。
     *
     * @param entity 实体
     * @return 读模型
     */
    private ChargeDiscountConfigQueryModel toQueryModel(ChargeDiscountConfig entity) {
        ChargeDiscountConfigQueryModel model = new ChargeDiscountConfigQueryModel();
        model.setId(entity.getId());
        model.setProductTable(entity.getProductTable());
        model.setCategoryCode(entity.getCategoryCode());
        model.setProductId(entity.getProductId());
        model.setDiscountRate(entity.getDiscountRate());
        model.setStatusEnum(entity.getStatusEnum());
        model.setRemark(entity.getRemark());
        model.setOrgId(entity.getOrgId());
        return model;
    }

    /**
     * 构造产品键（批量 Map 键）。
     *
     * @param productTable 产品表
     * @param productId    产品 id
     * @return table#id
     */
    private String buildProductKey(String productTable, Long productId) {
        return productTable + ChargeDiscountConfigConstants.PRODUCT_KEY_SEPARATOR + productId;
    }

    /**
     * 构造大类索引键。
     *
     * @param productTable 产品表
     * @param categoryCode 大类码
     * @return table#category
     */
    private String buildCategoryKey(String productTable, String categoryCode) {
        return productTable + ChargeDiscountConfigConstants.PRODUCT_KEY_SEPARATOR + categoryCode;
    }
}
