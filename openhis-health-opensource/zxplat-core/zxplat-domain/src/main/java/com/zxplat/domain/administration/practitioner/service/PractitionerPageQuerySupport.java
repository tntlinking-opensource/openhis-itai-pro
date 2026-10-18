/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.practitioner.service;

import com.zxplat.domain.administration.practitioner.entity.Practitioner;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.framework.core.application.util.StringUtils;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 医务人员分页读侧 {@link Specification} 组装（仅 {@code adm_practitioner} 谓词）。
 *
 * <p>岗位/角色收窄须由应用层 {@code PractitionerRoleScopeLoader} 预解析主键后传入 {@code resolvedPractitionerIdsForRole}，
 * 禁止在此类 EXISTS {@code adm_pr_assign}（B.6.1.3.1）。</p>
 */
final class PractitionerPageQuerySupport {

    private PractitionerPageQuerySupport() {
    }

    /**
     * 在职医务人员分页 Spec；{@code trimmedKeyword} 非空时追加 LIKE 与精确优先 ORDER BY（B.2.1）。
     *
     * @param status                         发布状态 code
     * @param trimmedKeyword                 已 trim 关键字；空白则不追加关键字谓词/排序
     * @param requireBoundSystemUser         是否要求已绑定系统用户
     * @param resolvedPractitionerIdsForRole 角色过滤后的 id；null 表示不追加
     * @return Specification
     */
    static Specification<Practitioner> activePractitioners(
        String status,
        String trimmedKeyword,
        boolean requireBoundSystemUser,
        Collection<Long> resolvedPractitionerIdsForRole) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), status));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (requireBoundSystemUser) {
                predicates.add(cb.isNotNull(root.get("userId")));
            }
            if (StringUtils.isNotEmpty(trimmedKeyword)) {
                String searchPattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmedKeyword);
                predicates.add(keywordPredicate(root, cb, searchPattern));
                // B.2.1：精确全等置顶，次级主键倒序
                List<Order> secondary = List.of(cb.desc(root.get(FieldNameConstants.ID)));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    query,
                    cb,
                    trimmedKeyword,
                    secondary,
                    root.get(FieldNameConstants.NAME),
                    root.get(FieldNameConstants.BUS_NO),
                    root.get("phone"),
                    root.get("addressStreet"),
                    root.get(FieldNameConstants.PY_STR),
                    root.get(FieldNameConstants.WB_STR),
                    root.get("ybNo"));
            }
            applyResolvedPractitionerIdsPredicate(predicates, root, cb, resolvedPractitionerIdsForRole);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 门诊挂号可选医生：在职且 id 落在应用层预解析的岗位人员集合内。
     */
    static Specification<Practitioner> activeOutpatientRegistrationDoctors(
        String status,
        Collection<Long> resolvedPractitionerIdsForRole) {
        return (root, query, cb) -> {
            if (query != null) {
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), status));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            applyResolvedPractitionerIdsPredicate(predicates, root, cb, resolvedPractitionerIdsForRole);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 医务人员分页：追加角色解析后的 id 范围谓词；空集合时构造恒 false。
     *
     * @param predicates                    待追加谓词列表
     * @param root                          Practitioner 根
     * @param cb                            CriteriaBuilder
     * @param resolvedPractitionerIdsForRole 角色过滤后的 id；null 表示不追加
     */
    private static void applyResolvedPractitionerIdsPredicate(
        List<Predicate> predicates,
        Root<Practitioner> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Collection<Long> resolvedPractitionerIdsForRole) {
        if (resolvedPractitionerIdsForRole == null) {
            return;
        }
        if (resolvedPractitionerIdsForRole.isEmpty()) {
            predicates.add(cb.equal(cb.literal(1), cb.literal(0)));
            return;
        }
        predicates.add(root.get(FieldNameConstants.ID).in(resolvedPractitionerIdsForRole));
    }

    /**
     * 医务人员关键字 OR 谓词：姓名、工号、电话、地址、拼音、五笔、医保号。
     *
     * @param root          Practitioner 根
     * @param cb            CriteriaBuilder
     * @param searchPattern 已小写化的 LIKE 模式
     * @return OR 组合谓词
     */
    private static Predicate keywordPredicate(
        Root<Practitioner> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        String searchPattern) {
        return cb.or(
            cb.like(cb.lower(root.get(FieldNameConstants.NAME)), searchPattern),
            cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), searchPattern),
            cb.like(cb.lower(root.get("phone")), searchPattern),
            cb.like(cb.lower(root.get("addressStreet")), searchPattern),
            cb.like(cb.lower(root.get(FieldNameConstants.PY_STR)), searchPattern),
            cb.like(cb.lower(root.get(FieldNameConstants.WB_STR)), searchPattern),
            cb.like(cb.lower(root.get("ybNo")), searchPattern));
    }
}
