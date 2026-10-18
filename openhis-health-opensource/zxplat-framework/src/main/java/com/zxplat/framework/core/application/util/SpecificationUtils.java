/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application.util;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification工具类
 */
public class SpecificationUtils {

    /**
     * 构建关键字查询条件（模糊匹配多个字段）
     * @param keyword 关键字
     * @param fields 要搜索的字段名
     * @return Specification
     */
    public static <T> Specification<T> keywordLike(String keyword, String... fields) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.trim().isEmpty()) {
                return cb.conjunction();
            }

            String pattern = "%" + keyword.trim() + "%";
            List<Predicate> predicates = new ArrayList<>();

            for (String field : fields) {
                predicates.add(cb.like(root.get(field), pattern));
            }

            return cb.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 构建等值查询条件
     * @param field 字段名
     * @param value 值
     * @return Specification
     */
    public static <T> Specification<T> equal(String field, Object value) {
        return (root, query, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value);
        };
    }

    /**
     * 构建软删除过滤条件
     * @return Specification
     */
    public static <T> Specification<T> notDeleted() {
        return (root, query, cb) -> cb.equal(root.get("isDeleted"), false);
    }

    /**
     * 构建租户过滤条件（多租户数据隔离）
     * 租户用户：只看自己租户的数据 + 系统数据(tenantId=null)
     * 宿主用户：只看系统数据
     * @param <T> 实体类型
     * @return Specification
     */
    public static <T> Specification<T> tenantFilter() {
        return (root, query, cb) -> {
            Long tenantId = com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils.getCurrentTenantId();

            if (tenantId != null) {
                // 租户用户：自己租户的 + 系统的
                return cb.or(
                    cb.equal(root.get("tenantId"), tenantId),
                    cb.isNull(root.get("tenantId"))
                );
            } else {
                // 宿主用户：只看系统数据
                return cb.isNull(root.get("tenantId"));
            }
        };
    }

    /**
     * 组合多个Specification
     * @param specs Specification数组
     * @return 组合后的Specification
     */
    @SafeVarargs
    public static <T> Specification<T> and(Specification<T>... specs) {
        Specification<T> result = null;
        for (Specification<T> spec : specs) {
            if (spec != null) {
                if (result == null) {
                    result = spec;
                } else {
                    result = result.and(spec);
                }
            }
        }
        return result != null ? result : (root, query, cb) -> cb.conjunction();
    }
}
