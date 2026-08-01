/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.audit.service;

import com.openhis.chs.application.audit.request.DetailAuditStatsLinesRequest;
import com.openhis.chs.application.audit.request.DetailAuditStatsPageRequest;
import com.openhis.chs.application.audit.response.DetailAuditStatsHitRow;
import com.openhis.chs.application.audit.response.DetailAuditStatsLineRow;
import com.openhis.chs.application.audit.response.DetailAuditStatsPageResult;
import com.openhis.chs.domain.audit.entity.YbDetailAuditHit;
import com.openhis.chs.domain.audit.entity.YbDetailAuditHitLine;
import com.openhis.chs.domain.audit.queries.DetailAuditStatsPageQuery;
import com.openhis.chs.domain.audit.result.DetailAuditStatsSummary;
import com.openhis.chs.domain.audit.service.YbDetailAuditStatsQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 事前提醒违规统计研讨（{@code POST /api/app/insuranceDetailAuditStats/*}）。
 *
 * <p>只读查 {@code yb_detail_audit_*}，不调 3101/3103。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceDetailAuditStatsAppService {

    /** 时间窗最长天数（含起止日） */
    private static final long MAX_RANGE_DAYS = 93L;

    private final YbDetailAuditStatsQueryService statsQueryService;

    /**
     * 分页查询违规头 + 同条件汇总。
     *
     * @param request 筛选
     * @return 分页与汇总
     */
    public DetailAuditStatsPageResult pageHits(DetailAuditStatsPageRequest request) {
        // Gate：请求非空；时间窗必填且不超过 93 天
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "统计查询请求不能为空");
        }
        assertTimeWindow(request.getCreatedFrom(), request.getCreatedTo());
        // Pre：租户 + 领域查询条件
        Long tenantId = TenantUtils.getCurrentTenantId();
        DetailAuditStatsPageQuery query = toQuery(request);
        // Cmd：分页违规头 + 同条件汇总（汇总扫全量筛选结果，非仅当前页）
        Page<YbDetailAuditHit> page = statsQueryService.page(tenantId, query);
        DetailAuditStatsSummary summary = statsQueryService.summarize(tenantId, query);

        // Pre：收集本页 jrId，批量取末次 3103 处置
        Set<String> jrIds = new HashSet<>();
        for (YbDetailAuditHit hit : page.getContent()) {
            if (hit != null && hit.getJrId() != null) {
                jrIds.add(hit.getJrId());
            }
        }
        Map<String, String> dspoByJr = statsQueryService.latestDspoWayByJrId(tenantId, jrIds);

        // Asm：实体 → 行 DTO，回填 latestDspoWay
        List<DetailAuditStatsHitRow> rows = new ArrayList<>(page.getContent().size());
        for (YbDetailAuditHit hit : page.getContent()) {
            if (hit == null) {
                continue;
            }
            rows.add(new DetailAuditStatsHitRow()
                .setJrId(hit.getJrId())
                .setCreatedAt(hit.getCreatedAt())
                .setDrCodg(hit.getDrCodg())
                .setDrName(hit.getDrName())
                .setDeptCodg(hit.getDeptCodg())
                .setDeptName(hit.getDeptName())
                .setMdtrtId(hit.getMdtrtId())
                .setPatnId(hit.getPatnId())
                .setRuleId(hit.getRuleId())
                .setRuleName(hit.getRuleName())
                .setSevDeg(hit.getSevDeg())
                .setVolaCont(hit.getVolaCont())
                .setVolaAmt(hit.getVolaAmt())
                .setTrigScen(hit.getTrigScen())
                .setContractBusNo(hit.getContractBusNo())
                .setLatestDspoWay(dspoByJr.get(hit.getJrId())));
        }

        PagedResult<DetailAuditStatsHitRow> paged = PagedResult.of(
            rows, page.getTotalElements(), page.getNumber() + 1, page.getSize());
        return new DetailAuditStatsPageResult()
            .setPage(paged)
            .setHitCount(summary.getHitCount())
            .setMdtrtCount(summary.getMdtrtCount())
            .setDoctorCount(summary.getDoctorCount())
            .setVolaAmtSum(summary.getVolaAmtSum());
    }

    /**
     * 违规明细行（抽屉）。
     *
     * @param request jrId
     * @return 明细列表
     */
    public List<DetailAuditStatsLineRow> listHitLines(DetailAuditStatsLinesRequest request) {
        // Gate：jrId 必填
        if (request == null || request.getJrId() == null || request.getJrId().isBlank()) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "jrId 不能为空");
        }
        // Pre/Cmd：按头 ID 查明细行
        Long tenantId = TenantUtils.getCurrentTenantId();
        List<YbDetailAuditHitLine> lines = statsQueryService.listLines(tenantId, request.getJrId().trim());
        // Asm：实体 → 行 DTO
        List<DetailAuditStatsLineRow> rows = new ArrayList<>(lines.size());
        for (YbDetailAuditHitLine line : lines) {
            if (line == null) {
                continue;
            }
            rows.add(new DetailAuditStatsLineRow()
                .setJrdId(line.getJrdId())
                .setJrId(line.getJrId())
                .setRxId(line.getRxId())
                .setVolaItemType(line.getVolaItemType())
                .setVolaAmt(line.getVolaAmt())
                .setMdtrtId(line.getMdtrtId()));
        }
        return rows;
    }

    /**
     * App 请求 → 领域查询对象（字段一一对应，不做默认时间）。
     *
     * @param request 统计分页请求
     * @return 领域查询
     */
    private static DetailAuditStatsPageQuery toQuery(DetailAuditStatsPageRequest request) {
        return new DetailAuditStatsPageQuery()
            .setCreatedFrom(request.getCreatedFrom())
            .setCreatedTo(request.getCreatedTo())
            .setDrCodg(request.getDrCodg())
            .setDrNameKeyword(request.getDrNameKeyword())
            .setDeptCodg(request.getDeptCodg())
            .setTrigScen(request.getTrigScen())
            .setSevDeg(request.getSevDeg())
            .setDspoWay(request.getDspoWay())
            .setKeyword(request.getKeyword())
            .setPageNo(request.getPageNo())
            .setPageSize(request.getPageSize());
    }

    /**
     * 校验创建时间窗：起止必填、起≤止、跨度不超过 {@link #MAX_RANGE_DAYS} 天（含起止日）。
     *
     * @param from 起（含）
     * @param to   止（含）
     */
    private static void assertTimeWindow(
            java.time.LocalDateTime from, java.time.LocalDateTime to) {
        if (from == null || to == null) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "请选择创建时间起止");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ChsErrorCodes.AUDIT_PARAM_INVALID, "结束时间不能早于开始时间");
        }
        long days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(
                ChsErrorCodes.AUDIT_PARAM_INVALID, "时间范围最长 " + MAX_RANGE_DAYS + " 天");
        }
    }
}
