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
import com.openhis.health.domain.workflow.supplyrequest.queries.WarehouseDocumentDetailPageQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 库房单据明细列表过滤条件（查 {@code wkf_supply_request}；与 RM 分页 Specification 语义对齐）。
 */
public final class WarehouseDocumentDetailPagePredicateSupport {

    private WarehouseDocumentDetailPagePredicateSupport() {
    }

    /**
     * 构建与分页列表相同的 WHERE 片段。
     *
     * @param root  供应请求根
     * @param cb    条件构建器
     * @param query 查询入参；为 null 时仅返回软删谓词
     * @return 谓词列表
     */
    public static List<Predicate> collectPagePredicates(
        Root<SupplyRequest> root,
        CriteriaBuilder cb,
        WarehouseDocumentDetailPageQuery query) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        if (query == null) {
            return predicates;
        }
        if (StringUtils.isNotBlank(query.getBusNo())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.BUS_NO), query.getBusNo().trim()));
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
        if (query.getSourceLocationIds() != null) {
            Collection<Long> ids = query.getSourceLocationIds();
            if (ids.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.SOURCE_LOCATION_ID).in(ids));
            }
        } else if (query.getSourceLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.SOURCE_LOCATION_ID), query.getSourceLocationId()));
        }
        if (query.getPurposeLocationIds() != null) {
            Collection<Long> purposeIds = query.getPurposeLocationIds();
            if (purposeIds.isEmpty()) {
                predicates.add(cb.disjunction());
            } else {
                predicates.add(root.get(FieldNameConstants.PURPOSE_LOCATION_ID).in(purposeIds));
            }
        } else if (query.getPurposeLocationId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.PURPOSE_LOCATION_ID), query.getPurposeLocationId()));
        }
        if (query.getItemId() != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ITEM_ID), query.getItemId()));
        }
        if (StringUtils.isNotBlank(query.getLotNumber())) {
            predicates.add(cb.equal(root.get(FieldNameConstants.LOT_NUMBER), query.getLotNumber().trim()));
        }
        // deliveryStatusEnum 为 RM 发放侧列；主表 wkf_supply_request 无对应列，当前三处调用方均未传入，故忽略。
        if (query.getApplyTimeStart() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getApplyTimeStart()));
        }
        if (query.getApplyTimeEnd() != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.APPLY_TIME), query.getApplyTimeEnd()));
        }
        if (StringUtils.isNotBlank(query.getKeyword())) {
            String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
            Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc);
            Predicate reasonLike = cb.like(cb.lower(root.get(FieldNameConstants.REASON)), likeIc);
            Predicate remakeLike = cb.like(cb.lower(root.get(FieldNameConstants.REMAKE)), likeIc);
            predicates.add(cb.or(busNoLike, reasonLike, remakeLike));
        }
        return predicates;
    }
}
