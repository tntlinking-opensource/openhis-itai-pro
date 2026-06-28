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

package com.openhis.health.domain.custom.priceadjustment.support;

import com.openhis.health.domain.custom.priceadjustment.entity.PriceAdjustment;
import com.openhis.health.domain.custom.priceadjustment.queries.PriceAdjustmentDocumentListQuery;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 调价单列表动态 WHERE 片段（{@code cus_price_adjustment}，按单号聚合列表复用）。
 */
public final class PriceAdjustmentDocumentListPredicateSupport {

    private PriceAdjustmentDocumentListPredicateSupport() {
    }

    /**
     * 组装软删、机构、状态、列表时刻窗、关键字（单号）谓词。
     *
     * @param root  调价主行根
     * @param cb    条件构建器
     * @param query 过滤入参；仅软删时 query 可为 null
     * @return 谓词列表
     */
    public static List<Predicate> collectPredicates(
        Root<PriceAdjustment> root,
        CriteriaBuilder cb,
        PriceAdjustmentDocumentListQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (query.getOrgId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ORG_ID), query.getOrgId()));
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
        }
        Date start = toDate(query.getStartDate());
        Date end = toDate(query.getEndDate());
        if (start != null || end != null) {
            // 列表时刻：已提交用 submit_time，草稿/未提交回落 adjustment_time（对齐进货单 apply_time / occurrence_time 分列展示）
            jakarta.persistence.criteria.Expression<Date> listTime =
                cb.coalesce(root.get(FieldNameConstants.SUBMIT_TIME), root.get(FieldNameConstants.ADJUSTMENT_TIME));
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(listTime, start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(listTime, end));
            }
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
        }
        return predicates;
    }

    private static Date toDate(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return Date.from(value.atZone(ZoneId.systemDefault()).toInstant());
    }
}
