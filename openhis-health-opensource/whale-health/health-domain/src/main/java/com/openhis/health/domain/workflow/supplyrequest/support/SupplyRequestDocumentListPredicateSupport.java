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

package com.openhis.health.domain.workflow.supplyrequest.support;

import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest;
import com.openhis.health.domain.workflow.supplyrequest.queries.SupplyRequestDocumentListQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link SupplyRequest}（{@code wkf_supply_request}）单据列表的动态 WHERE 片段，供各业务类型列表复用。
 *
 * <p>与 {@link SupplyRequestDocumentListQuery} 对齐：关键字、时段取自 {@link com.openhis.health.domain.share.query.AbstractPageQuery}
 *（B.10.11 / B.2.1）。</p>
 */
public final class SupplyRequestDocumentListPredicateSupport {

    private SupplyRequestDocumentListPredicateSupport() {
    }

    /**
     * 组装软删、类型、状态、租户、源/目的位置、申请时刻窗（{@code startDate}/{@code endDate}）、关键字（单号/原因/备注）谓词。
     *
     * @param root  供应请求根
     * @param cb    条件构建器
     * @param query 过滤入参；仅软删时 query 可为 null
     * @return 谓词列表（调用方以 AND 或数组交给 {@code CriteriaQuery#where}）
     */
    public static List<Predicate> collectPredicates(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        SupplyRequestDocumentListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        // 供应写表列表与统计均须排除逻辑删除行，与底座默认口径一致。
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (StringUtils.isNotBlank(query.getTypeEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TYPE_ENUM), query.getTypeEnum().trim()));
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        }
        if (query.getTenantId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
        }
        if (query.getSourceLocationIds() != null && !query.getSourceLocationIds().isEmpty()) {
            predicates.add(root.get(FieldNameConstants.SOURCE_LOCATION_ID).in(query.getSourceLocationIds()));
        } else if (query.getSourceLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getSourceLocationId()));
        }
        if (query.getPurposeLocationIds() != null && !query.getPurposeLocationIds().isEmpty()) {
            predicates.add(root.get(FieldNameConstants.PURPOSE_LOCATION_ID).in(query.getPurposeLocationIds()));
        } else if (query.getPurposeLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getPurposeLocationId()));
        }
        if (query.getStartDate() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getStartDate()));
        }
        if (query.getEndDate() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getEndDate()));
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            // 用户关键字：列侧 lower + 模式侧 SearchCodeUtil，禁止手写 % 拼接（B.2.1）。
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc);
            Predicate reasonLike = cb.like(cb.lower(root.get(FieldNameConstants.REASON)), likeIc);
            Predicate remakeLike = cb.like(cb.lower(root.get(FieldNameConstants.REMAKE)), likeIc);
            predicates.add(cb.or(busNoLike, reasonLike, remakeLike));
        }
        return predicates;
    }
}
