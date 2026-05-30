/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.practitioner.service;

import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitionerrole.entity.PractitionerRole;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 医务人员分页读侧 {@link Specification} 组装（B.2：仓储禁 {@code @Query}）。
 *
 * <p>由 {@link PractitionerQueryService} 调用，将关键字、在岗状态、系统用户绑定、业务角色等条件
 * 下推到数据库侧过滤。</p>
 */
final class PractitionerPageQuerySupport {

    private PractitionerPageQuerySupport() {
    }

    /**
     * 组装在岗医务人员分页查询条件。
     *
     * @param status 人员状态枚举 code（如 {@code ACTIVE}）
     * @param searchPattern 已小写化的 LIKE 模式；{@code null} 时不加关键字谓词
     * @param requireBoundSystemUser {@code true} 时要求已绑定系统用户（挂号/开立等场景）
     * @param businessRoleCode 业务角色码；{@code null} 或 {@code 1} 时不按角色子查询过滤
     * @return 可交给 {@code PractitionerRepository#findAll(Specification, Pageable)} 的规约
     */
    static Specification<Practitioner> activePractitioners(
        String status,
        String searchPattern,
        boolean requireBoundSystemUser,
        Integer businessRoleCode) {
        return (root, query, cb) -> {
            if (query != null) {
                // 角色子查询可能关联多行，去重避免分页总数膨胀
                query.distinct(true);
            }
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), status));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // 仅展示已绑定登录账号的执业者（窗口挂号选医生等）
            if (requireBoundSystemUser) {
                predicates.add(cb.isNotNull(root.get("userId")));
            }
            if (searchPattern != null) {
                predicates.add(keywordPredicate(root, cb, searchPattern));
            }
            // 业务角色 1 表示「不限角色」，其余码须 EXISTS 匹配在岗角色行
            if (businessRoleCode != null && businessRoleCode != 1) {
                predicates.add(hasActiveRoleCode(root, query, cb, businessRoleCode));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 姓名/工号/电话/地址/拼音/五笔/医保号等多列 OR 模糊匹配（模式已小写化）。
     *
     * @param root 医务人员根
     * @param cb Criteria 构建器
     * @param searchPattern LIKE 模式（含通配符）
     * @return 多列 OR 谓词
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

    /**
     * 要求存在指定业务角色码的在岗 {@link PractitionerRole} 行。
     *
     * @param root 医务人员根
     * @param query 外层查询（用于子查询）
     * @param cb Criteria 构建器
     * @param roleCode 角色码（与 {@code adm_practitioner_role.role_code} 一致）
     * @return EXISTS 子查询谓词
     */
    private static Predicate hasActiveRoleCode(
        Root<Practitioner> root,
        jakarta.persistence.criteria.CriteriaQuery<?> query,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        int roleCode) {
        Subquery<Long> roleSubquery = query.subquery(Long.class);
        Root<PractitionerRole> roleRoot = roleSubquery.from(PractitionerRole.class);
        roleSubquery.select(cb.literal(1L));
        roleSubquery.where(
            cb.equal(roleRoot.get(FieldNameConstants.PRACTITIONER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(roleRoot.get(FieldNameConstants.IS_DELETED)),
            cb.isTrue(roleRoot.get(FieldNameConstants.ACTIVE_FLAG)),
            cb.equal(roleRoot.get("roleCode"), roleCode));
        return cb.exists(roleSubquery);
    }
}
