/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * 多租户过滤器
 * 自动为查询添加tenant_id条件
 */
@Component
public class TenantFilter {

    @Autowired
    private TenantContext tenantContext;

    /**
     * 启用多租户过滤
     * 在查询前调用，自动添加tenant_id条件
     */
    public void enableFilter(EntityManager entityManager) {
        Long tenantId = tenantContext.getCurrentTenantId();

        if (tenantId != null) {
            Session session = entityManager.unwrap(Session.class);
            Filter filter = session.enableFilter("tenantFilter");
            filter.setParameter("tenantId", tenantId);
        }
    }

    /**
     * 禁用多租户过滤
     */
    public void disableFilter(EntityManager entityManager) {
        Session session = entityManager.unwrap(Session.class);
        session.disableFilter("tenantFilter");
    }

    /**
     * 构建多租户查询条件
     * 用于JPA Specification
     */
    public <T> Predicate buildTenantPredicate(Root<T> root, CriteriaBuilder cb) {
        Long tenantId = tenantContext.getCurrentTenantId();

        if (tenantId != null) {
            // 只查询当前租户的数据
            return cb.equal(root.get("tenantId"), tenantId);
        } else {
            // 宿主用户可以查询所有数据（tenantId为null的数据）
            return cb.isNull(root.get("tenantId"));
        }
    }
}
