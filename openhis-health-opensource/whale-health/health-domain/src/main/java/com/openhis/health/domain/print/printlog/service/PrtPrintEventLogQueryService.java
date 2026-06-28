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

package com.openhis.health.domain.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.queries.PrtPrintEventLogPageQuery;
import com.openhis.health.domain.print.printlog.repository.PrtPrintEventLogRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 打印审计日志读侧查询服务（DM-PRT-002 · PrtPrintEventLog）。
 *
 * <p>仅注入本聚合 {@link PrtPrintEventLogRepository}；跨聚合读不在此类扩展（B.6.4）。</p>
 * <p>读侧由本 QueryService 收口；条件下推 DB，禁止 App 直注 Repository。</p>
 * <p>DOC-CONFLICT：SDD §5.2 写 App 直用 Specification；health 规范 B.6.4 收口至 QueryService。</p>
 */
@Service
@RequiredArgsConstructor
public class PrtPrintEventLogQueryService {

    private final PrtPrintEventLogRepository prtPrintEventLogRepository;

    /**
     * 按分页查询条件筛选打印日志。
     *
     * @param query    筛选条件；tenantId 必填
     * @param pageable 分页与排序
     * @return 日志分页
     */
    public Page<PrtPrintEventLog> pageByFilter(PrtPrintEventLogPageQuery query, Pageable pageable) {
        if (query == null || query.getTenantId() == null) {
            return Page.empty(pageable);
        }
        Specification<PrtPrintEventLog> spec = buildFilterSpecification(query);
        return prtPrintEventLogRepository.findAll(spec, pageable);
    }

    /**
     * 按分页查询条件扫描打印日志（日汇总用；忽略 status）。
     *
     * @param query    筛选条件；tenantId 必填
     * @param pageable 分页与排序（调用方控制扫描上限）
     * @return 日志分页
     */
    public Page<PrtPrintEventLog> pageForDailySummary(PrtPrintEventLogPageQuery query, Pageable pageable) {
        if (query == null || query.getTenantId() == null) {
            return Page.empty(pageable);
        }
        PrtPrintEventLogPageQuery scanQuery = new PrtPrintEventLogPageQuery();
        scanQuery.setTenantId(query.getTenantId());
        scanQuery.setBusNo(query.getBusNo());
        scanQuery.setDateFrom(query.getDateFrom());
        scanQuery.setDateTo(query.getDateTo());
        Specification<PrtPrintEventLog> spec = buildFilterSpecification(scanQuery);
        return prtPrintEventLogRepository.findAll(spec, pageable);
    }

    /**
     * 组装日志筛选 Specification（条件下推 DB，含软删过滤）。
     *
     * @param query 筛选条件
     * @return JPA Specification
     */
    private Specification<PrtPrintEventLog> buildFilterSpecification(PrtPrintEventLogPageQuery query) {
        String resolvedBusNo = trimToNull(query.getBusNo());
        String resolvedStatus = trimToNull(query.getStatus());
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), query.getTenantId()));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (resolvedBusNo != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.BUS_NO), resolvedBusNo));
            }
            if (resolvedStatus != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS), resolvedStatus));
            }
            if (query.getDateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.CREATED_AT), query.getDateFrom().atStartOfDay()));
            }
            if (query.getDateTo() != null) {
                predicates.add(cb.lessThan(
                    root.get(FieldNameConstants.CREATED_AT),
                    query.getDateTo().plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
