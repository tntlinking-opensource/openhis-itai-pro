/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.invokelog.service;

import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.chs.application.invokelog.mapper.InsuranceInvokeLogAppMapper;
import com.openhis.chs.application.invokelog.request.GetInvokeLogDetailRequest;
import com.openhis.chs.application.invokelog.request.QueryInvokeLogPageRequest;
import com.openhis.chs.application.invokelog.response.InvokeLogDetailResult;
import com.openhis.chs.application.invokelog.response.InvokeLogRowResult;
import com.openhis.chs.domain.invoke.entity.YbInvokeLog;
import com.openhis.chs.domain.invoke.queries.InvokeLogPageQuery;
import com.openhis.chs.domain.invoke.service.YbInvokeLogPageQueryService;
import com.openhis.chs.domain.invoke.service.YbInvokeLogQueryService;
import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 医保平台调用日志查看（POST /api/app/insuranceInvokeLog/*）。
 *
 * <p>就诊号/患者姓名筛选与列表回填均通过 Encounter/Patient 读服务拼装，禁止对就诊表 SQL JOIN。</p>
 */
@Service
@RequiredArgsConstructor
public class InsuranceInvokeLogAppService {

    /** 时间窗最长天数（含起止日） */
    private static final long MAX_RANGE_DAYS = 31L;

    /** 姓名模糊命中患者上限 */
    private static final int PATIENT_NAME_MATCH_LIMIT = 100;

    /** 姓名筛选取反查就诊 ID 上限（防 IN 过大） */
    private static final int ENCOUNTER_IDS_FROM_NAME_LIMIT = 500;

    private final YbInvokeLogPageQueryService pageQueryService;
    private final YbInvokeLogQueryService invokeLogQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;

    /**
     * 分页查询调用日志（列表仅返回报文摘要）。
     */
    public PagedResult<InvokeLogRowResult> queryInvokeLogPage(QueryInvokeLogPageRequest request) {
        if (request == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "查询请求不能为空");
        }
        assertTimeWindow(request.getCreatedFrom(), request.getCreatedTo());

        EncounterFilterScope scope = resolveEncounterFilterScope(request);
        if (scope.emptyBecauseUnresolved()) {
            return emptyPage(request);
        }

        InvokeLogPageQuery query = new InvokeLogPageQuery()
            .setCreatedFrom(request.getCreatedFrom())
            .setCreatedTo(request.getCreatedTo())
            .setInfno(request.getInfno())
            .setSuccess(request.getSuccess())
            .setMsgid(request.getMsgid())
            .setContractBusNo(request.getContractBusNo())
            .setEncounterId(scope.singleEncounterId())
            .setEncounterIds(scope.encounterIds())
            .setChrgBchno(request.getChrgBchno())
            .setPaymentDraftId(request.getPaymentDraftId())
            .setSettlementRecordId(request.getSettlementRecordId())
            .setErrMsgKeyword(request.getErrMsgKeyword())
            .setPageNo(request.getPageNo())
            .setPageSize(request.getPageSize());

        Long tenantId = TenantUtils.getCurrentTenantId();
        Page<YbInvokeLog> page = pageQueryService.page(tenantId, query);
        EncounterEnrichment enrichment = loadEncounterEnrichment(page.getContent());

        List<InvokeLogRowResult> rows = new ArrayList<>(page.getContent().size());
        for (YbInvokeLog row : page.getContent()) {
            Long encounterId = row.getEncounterId();
            String busNo = encounterId == null ? null : enrichment.busNoByEncounterId().get(encounterId);
            String patientName =
                encounterId == null ? null : enrichment.patientNameByEncounterId().get(encounterId);
            rows.add(InsuranceInvokeLogAppMapper.toRowResult(row, busNo, patientName));
        }

        PagedResult<InvokeLogRowResult> result = new PagedResult<>();
        result.setContent(rows);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    /**
     * 查询单条调用日志详情（含完整请求/响应原文）。
     */
    public InvokeLogDetailResult getInvokeLogDetail(GetInvokeLogDetailRequest request) {
        if (request == null || request.getId() == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "日志主键不能为空");
        }
        YbInvokeLog row = invokeLogQueryService.findOptionalById(request.getId())
            .orElseThrow(() -> new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "调用日志不存在"));
        String busNo = null;
        String patientName = null;
        if (row.getEncounterId() != null) {
            EncounterEnrichment enrichment = loadEncounterEnrichment(List.of(row));
            busNo = enrichment.busNoByEncounterId().get(row.getEncounterId());
            patientName = enrichment.patientNameByEncounterId().get(row.getEncounterId());
        }
        return InsuranceInvokeLogAppMapper.toDetailResult(row, busNo, patientName);
    }

    /**
     * 收敛就诊筛选范围：就诊号优先精确；患者姓名 → 患者 → 就诊集合；多条件取交集。
     */
    private EncounterFilterScope resolveEncounterFilterScope(QueryInvokeLogPageRequest request) {
        Long fromBusNoOrId = null;
        boolean busNoUnresolved = false;
        String busNo = trimToNull(request.getEncounterBusNo());
        if (busNo != null) {
            Optional<Encounter> encounter = encounterQueryService.findOptionalEncounterEntityByBusNo(busNo);
            if (encounter.isEmpty()) {
                busNoUnresolved = true;
            } else {
                fromBusNoOrId = encounter.get().getId();
            }
        } else if (request.getEncounterId() != null) {
            fromBusNoOrId = request.getEncounterId();
        }

        List<Long> fromPatientName = null;
        boolean patientNameUnresolved = false;
        String patientName = trimToNull(request.getPatientName());
        if (patientName != null) {
            List<Long> patientIds =
                patientQueryService.listPatientIdsByNameKeyword(patientName, PATIENT_NAME_MATCH_LIMIT);
            if (patientIds.isEmpty()) {
                patientNameUnresolved = true;
            } else {
                List<Long> encounterIds = encounterQueryService.listEncounterIdsByPatientIds(patientIds);
                if (encounterIds.isEmpty()) {
                    patientNameUnresolved = true;
                } else if (encounterIds.size() > ENCOUNTER_IDS_FROM_NAME_LIMIT) {
                    fromPatientName = new ArrayList<>(encounterIds.subList(0, ENCOUNTER_IDS_FROM_NAME_LIMIT));
                } else {
                    fromPatientName = encounterIds;
                }
            }
        }

        if (busNoUnresolved || patientNameUnresolved) {
            return EncounterFilterScope.unresolved();
        }
        if (fromBusNoOrId == null && fromPatientName == null) {
            return EncounterFilterScope.none();
        }
        if (fromBusNoOrId != null && fromPatientName == null) {
            return EncounterFilterScope.single(fromBusNoOrId);
        }
        if (fromBusNoOrId == null) {
            return EncounterFilterScope.many(fromPatientName);
        }
        // 交集：精确就诊须落在姓名命中集合内
        if (!fromPatientName.contains(fromBusNoOrId)) {
            return EncounterFilterScope.unresolved();
        }
        return EncounterFilterScope.single(fromBusNoOrId);
    }

    private EncounterEnrichment loadEncounterEnrichment(List<YbInvokeLog> rows) {
        Set<Long> encounterIds = new HashSet<>();
        for (YbInvokeLog row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return EncounterEnrichment.empty();
        }
        List<Encounter> encounters = encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        Map<Long, String> busNoByEncounterId = new HashMap<>();
        Set<Long> patientIds = new HashSet<>();
        Map<Long, Long> patientIdByEncounterId = new HashMap<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (isNotEmpty(encounter.getBusNo())) {
                busNoByEncounterId.put(encounter.getId(), encounter.getBusNo().trim());
            }
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
                patientIdByEncounterId.put(encounter.getId(), encounter.getPatientId());
            }
        }
        Map<Long, String> nameByPatientId = new HashMap<>();
        if (!patientIds.isEmpty()) {
            List<Long> idList = new ArrayList<>(patientIds);
            final int batchSize = 100;
            for (int i = 0; i < idList.size(); i += batchSize) {
                int end = Math.min(i + batchSize, idList.size());
                nameByPatientId.putAll(
                    patientQueryService.findPatientNamesByIds(idList.subList(i, end)));
            }
        }
        Map<Long, String> patientNameByEncounterId = new HashMap<>();
        for (Map.Entry<Long, Long> entry : patientIdByEncounterId.entrySet()) {
            String name = nameByPatientId.get(entry.getValue());
            if (isNotEmpty(name)) {
                patientNameByEncounterId.put(entry.getKey(), name);
            }
        }
        return new EncounterEnrichment(busNoByEncounterId, patientNameByEncounterId);
    }

    private static void assertTimeWindow(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "请选择起止时间");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ChsErrorCodes.CONTRACT_INVALID, "结束时间不能早于开始时间");
        }
        long days = ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
        if (days > MAX_RANGE_DAYS) {
            throw new BusinessException(
                ChsErrorCodes.CONTRACT_INVALID,
                "查询时间跨度不能超过 " + MAX_RANGE_DAYS + " 天");
        }
    }

    private static PagedResult<InvokeLogRowResult> emptyPage(QueryInvokeLogPageRequest request) {
        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;
        PagedResult<InvokeLogRowResult> result = new PagedResult<>();
        result.setContent(List.of());
        result.setTotalCount(0L);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        return result;
    }

    /** 就诊过滤范围（不用 private record：部分编译器要求 record 存取方法必须 public）。 */
    private static final class EncounterFilterScope {
        private final boolean unresolved;
        private final Long singleId;
        private final List<Long> ids;

        private EncounterFilterScope(boolean unresolved, Long singleId, List<Long> ids) {
            this.unresolved = unresolved;
            this.singleId = singleId;
            this.ids = ids;
        }

        static EncounterFilterScope unresolved() {
            return new EncounterFilterScope(true, null, null);
        }

        static EncounterFilterScope none() {
            return new EncounterFilterScope(false, null, null);
        }

        static EncounterFilterScope single(Long id) {
            return new EncounterFilterScope(false, id, null);
        }

        static EncounterFilterScope many(List<Long> ids) {
            return new EncounterFilterScope(false, null, ids);
        }

        boolean emptyBecauseUnresolved() {
            return unresolved;
        }

        Long singleEncounterId() {
            return singleId;
        }

        List<Long> encounterIds() {
            return ids;
        }
    }

    private static final class EncounterEnrichment {
        private final Map<Long, String> busNoByEncounterId;
        private final Map<Long, String> patientNameByEncounterId;

        private EncounterEnrichment(
            Map<Long, String> busNoByEncounterId,
            Map<Long, String> patientNameByEncounterId) {
            this.busNoByEncounterId = busNoByEncounterId;
            this.patientNameByEncounterId = patientNameByEncounterId;
        }

        static EncounterEnrichment empty() {
            return new EncounterEnrichment(Map.of(), Map.of());
        }

        Map<Long, String> busNoByEncounterId() {
            return busNoByEncounterId;
        }

        Map<Long, String> patientNameByEncounterId() {
            return patientNameByEncounterId;
        }
    }
}
