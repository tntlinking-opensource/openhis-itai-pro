/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printlog.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printlog.entity.PrtPrintEventLog;
import com.openhis.health.domain.print.printlog.queries.PrtPrintEventLogPageQuery;
import com.openhis.health.domain.print.printlog.repository.PrtPrintEventLogRepository;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.PrintConstants;
import com.openhis.health.domain.share.enums.PrintLogStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

/**
 * 打印审计日志读侧查询服务（DM-PRT-002 · PrtPrintEventLog）。
 *
 * <p>仅注入本聚合 {@link PrtPrintEventLogRepository}；跨聚合读不在此类扩展（B.6.4）。</p>
 * <p>读侧由本 QueryService 收口；条件下推 DB，禁止 App 直注 Repository。</p>
 * <p>DOC-CONFLICT：SDD §5.2 写 App 直用 Specification；health 规范 B.6.4 收口至 QueryService。</p>
 *
 * <p>聚合边界：仅本聚合 {@link PrtPrintEventLogRepository}。</p>
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
     * 扫描指定场景 SUCCESS 日志（供业务主键回查；条数上限内按下推条件过滤）。
     *
     * @param tenantId     租户
     * @param bindingCode  场景码（confirm 写入 binding_code）
     * @param dateFrom     创建日起（含）；可空
     * @param dateTo       创建日止（含）；可空
     * @return SUCCESS 日志列表（按 createdAt 降序，最多 {@link PrintConstants#QUERY_PRINTED_LOG_SCAN_MAX}）
     */
    public List<PrtPrintEventLog> listSuccessByBindingCode(
        Long tenantId,
        String bindingCode,
        LocalDate dateFrom,
        LocalDate dateTo) {
        if (tenantId == null || bindingCode == null || bindingCode.isBlank()) {
            return List.of();
        }
        String resolvedBinding = trimToNull(bindingCode);
        if (resolvedBinding == null) {
            return List.of();
        }
        PageRequest pageable = PageRequest.of(
            0,
            PrintConstants.QUERY_PRINTED_LOG_SCAN_MAX,
            Sort.by(Sort.Direction.DESC, FieldNameConstants.CREATED_AT));
        Specification<PrtPrintEventLog> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.TENANT_ID), tenantId));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS), PrintLogStatus.SUCCESS.getCode()));
            // binding_code 存 scenarioCode（confirm 写入）
            predicates.add(cb.equal(root.get("bindingCode"), resolvedBinding));
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.CREATED_AT), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(
                    root.get(FieldNameConstants.CREATED_AT),
                    dateTo.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return prtPrintEventLogRepository.findAll(spec, pageable).getContent();
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
