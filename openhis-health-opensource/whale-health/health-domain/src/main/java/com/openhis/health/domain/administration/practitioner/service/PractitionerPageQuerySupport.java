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
import com.openhis.health.domain.share.constants.FieldNameConstants;
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

    static Specification<Practitioner> activePractitioners(
        String status,
        String searchPattern,
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
            if (searchPattern != null) {
                predicates.add(keywordPredicate(root, cb, searchPattern));
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
