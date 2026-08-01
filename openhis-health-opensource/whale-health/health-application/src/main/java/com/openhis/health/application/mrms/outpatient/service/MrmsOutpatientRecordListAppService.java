/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatient.service;

import com.openhis.health.application.mrms.outpatient.dto.OutpatientRecordListItemDto;
import com.openhis.health.application.mrms.outpatient.mapper.OutpatientRecordListMapper;
import com.openhis.health.application.mrms.outpatient.request.QueryOutpatientRecordListRequest;
import com.openhis.health.application.mrms.support.MrmsOperatorResolver;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.EmrDocumentTypeCodes;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrOutpatientDocumentPageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentItemExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentPageExtensionOut;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 门诊病历查询应用服务（病案管理菜单，只读）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EmrDocumentExtension} — 跨患者门诊文书分页（EMR Module）</li>
 *   <li>{@link PatientQueryService} / {@link EncounterQueryService} — 关键词与就诊范围收敛</li>
 *   <li>{@link OrganizationQueryService} — 科室名称解析</li>
 *   <li>{@link OutpatientRecordListMapper} — Extension 行 → 页面 DTO</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar → POST /api/app/mrmsOutpatientRecordList/{method}；不另写 RestController}。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsOutpatientRecordListAppService {

    /** EMR 文书读侧 Extension。 */
    private final EmrDocumentExtension emrDocumentExtension;

    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;

    /** 就诊读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 机构读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 列表行映射。 */
    private final OutpatientRecordListMapper outpatientRecordListMapper;

    /** 病案操作人/租户解析。 */
    private final MrmsOperatorResolver operatorResolver;

    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;

    /**
     * 分页检索门诊病历文书。
     *
     * @param request 关键词/科室/就诊日期/状态与分页
     * @return 分页列表
     */
    public PagedResult<OutpatientRecordListItemDto> queryPage(QueryOutpatientRecordListRequest request) {
        // Gate：分页窗口合法；校验登录租户可读
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        operatorResolver.requireTenantId();
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        // 分页上限 200，防止大页拖垮列表
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：将姓名/门诊号/日期/科室收敛为就诊主键集合（有过滤且无命中则空页）
        EncounterFilterResolution resolution = resolveEncounterFilter(request);
        if (resolution.constrained() && resolution.encounterIds().isEmpty()) {
            return PagedResultBuilder.build(new PagedResult<>(), List.of(), 0L, pageNo, pageSize);
        }
        EmrOutpatientDocumentPageExtensionIn extensionIn = new EmrOutpatientDocumentPageExtensionIn();
        extensionIn.setDocumentTypeCode(EmrDocumentTypeCodes.OUTPATIENT_RECORD);
        extensionIn.setStatus(StringUtils.trimToNull(request.getStatusCode()));
        extensionIn.setPageNo(pageNo);
        extensionIn.setPageSize(pageSize);
        if (resolution.constrained()) {
            extensionIn.setEncounterIds(new ArrayList<>(resolution.encounterIds()));
        }
        // Cmd：经 Extension 分页读 EMR 文书
        EmrOutpatientDocumentPageExtensionOut page = emrDocumentExtension.queryOutpatientDocumentPage(extensionIn);
        List<EmrOutpatientDocumentItemExtensionOut> rows =
                page.getContent() == null ? List.of() : page.getContent();
        // Asm：映射 DTO 并回填患者/就诊/科室
        List<OutpatientRecordListItemDto> items = enrichListItems(rows);
        return PagedResultBuilder.build(
                new PagedResult<>(), items, page.getTotalCount(), page.getPageNo(), page.getPageSize());
    }

    /**
     * 解析筛选条件为就诊主键集合。
     *
     * @param request 查询请求
     * @return 是否施加约束及命中就诊主键
     */
    private EncounterFilterResolution resolveEncounterFilter(QueryOutpatientRecordListRequest request) {
        LinkedHashSet<Long> encounterIds = null;
        String keyword = StringUtils.trimToNull(request.getKeyword());
        // 关键词：姓名/病案号/证件 → 患者 → 就诊；门诊号 → 就诊 busNo
        if (keyword != null) {
            LinkedHashSet<Long> byKeyword = new LinkedHashSet<>();
            byKeyword.addAll(encounterQueryService.listEncounterIdsByBusNoKeyword(keyword));
            List<Long> patientIds = patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
            byKeyword.addAll(encounterQueryService.listEncounterIdsByPatientIds(patientIds));
            encounterIds = byKeyword;
        }
        // 就诊日期闭区间
        if (request.getEncounterDateFrom() != null || request.getEncounterDateTo() != null) {
            List<Long> byDate = encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
                    null,
                    request.getEncounterDateFrom() == null
                            ? null
                            : request.getEncounterDateFrom().atStartOfDay(),
                    request.getEncounterDateTo() == null
                            ? null
                            : request.getEncounterDateTo().atTime(LocalTime.MAX));
            encounterIds = intersectOrReplace(encounterIds, byDate);
        }
        // 科室名称模糊 → 门诊 AMB 就诊
        String deptName = StringUtils.trimToNull(request.getDeptName());
        if (deptName != null) {
            List<Long> byDept = listAmbEncounterIdsByDeptName(deptName);
            encounterIds = intersectOrReplace(encounterIds, byDept);
        }
        if (encounterIds == null) {
            return new EncounterFilterResolution(false, Set.of());
        }
        return new EncounterFilterResolution(true, encounterIds);
    }

    /**
     * 按科室名称模糊匹配责任科室，并收集对应 AMB 就诊主键。
     *
     * @param deptName 科室名称关键字
     * @return 就诊主键列表
     */
    private List<Long> listAmbEncounterIdsByDeptName(String deptName) {
        String needle = deptName.toLowerCase(Locale.ROOT);
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (Organization organization : organizationQueryService.getOrganizationTreeForResponsibilityDept()) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            String name = organization.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).contains(needle)) {
                orgIds.add(organization.getId());
            }
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (Long orgId : orgIds) {
            encounterIds.addAll(encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(orgId));
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 与已有集合求交；若尚无集合则替换为新集合。
     *
     * @param current 当前集合；{@code null} 表示尚未约束
     * @param next 新命中列表
     * @return 约束后的集合
     */
    private static LinkedHashSet<Long> intersectOrReplace(LinkedHashSet<Long> current, List<Long> next) {
        LinkedHashSet<Long> nextSet = new LinkedHashSet<>();
        if (next != null) {
            for (Long id : next) {
                if (id != null) {
                    nextSet.add(id);
                }
            }
        }
        if (current == null) {
            return nextSet;
        }
        current.retainAll(nextSet);
        return current;
    }

    /**
     * Extension 行映射并批量回填 HIS 展示字段。
     *
     * @param rows Extension 摘要行
     * @return 页面列表 DTO
     */
    private List<OutpatientRecordListItemDto> enrichListItems(List<EmrOutpatientDocumentItemExtensionOut> rows) {
        List<OutpatientRecordListItemDto> items = new ArrayList<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        for (EmrOutpatientDocumentItemExtensionOut row : rows) {
            OutpatientRecordListItemDto dto = outpatientRecordListMapper.toListItem(row);
            if (dto == null) {
                continue;
            }
            items.add(dto);
            if (dto.getEncounterId() != null) {
                encounterIds.add(dto.getEncounterId());
            }
            if (dto.getPatientId() != null) {
                patientIds.add(dto.getPatientId());
            }
        }
        List<Encounter> encounters = encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        Map<Long, Encounter> encounterById = new HashMap<>();
        LinkedHashSet<Long> orgIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter.getId() == null) {
                continue;
            }
            encounterById.put(encounter.getId(), encounter);
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
            }
            if (encounter.getOrganizationId() != null) {
                orgIds.add(encounter.getOrganizationId());
            }
        }
        Map<Long, String> patientNames = patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(orgIds);
        for (OutpatientRecordListItemDto dto : items) {
            Encounter encounter = encounterById.get(dto.getEncounterId());
            if (encounter != null) {
                // 文书未带 patientId 时用就诊回填
                if (dto.getPatientId() == null) {
                    dto.setPatientId(encounter.getPatientId());
                }
                dto.setOutpatientNo(encounter.getBusNo());
                dto.setEncounterTime(encounter.getStartTime());
                if (encounter.getOrganizationId() != null) {
                    dto.setDeptName(orgNames.get(encounter.getOrganizationId()));
                }
            }
            if (dto.getPatientId() != null) {
                dto.setPatientName(patientNames.get(dto.getPatientId()));
            }
        }
        return items;
    }

    /**
     * 就诊过滤解析结果。
     *
     * @param constrained 是否已施加任一筛选
     * @param encounterIds 命中就诊主键
     */
    private record EncounterFilterResolution(boolean constrained, Set<Long> encounterIds) {
    }
}
