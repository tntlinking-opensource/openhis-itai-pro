/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.outpatientemr.service;

import com.openhis.health.application.mrms.outpatientemr.dto.OutpatientEmrListItemDto;
import com.openhis.health.application.mrms.outpatientemr.mapper.OutpatientEmrListMapper;
import com.openhis.health.application.mrms.outpatientemr.request.ArchiveOutpatientClinicalCaseRequest;
import com.openhis.health.application.mrms.outpatientemr.request.CreateOutpatientDocumentEditApplicationRequest;
import com.openhis.health.application.mrms.outpatientemr.request.QueryOutpatientEmrListRequest;
import com.openhis.health.application.mrms.outpatientemr.request.SubmitOutpatientDocumentArchiveBatchRequest;
import com.openhis.health.application.mrms.outpatientemr.request.SubmitOutpatientDocumentArchiveRequest;
import com.openhis.health.application.shared.mrms.MrmsClinicalCaseArchiveSupport;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.EmrDocumentTypeCodes;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentArchiveWorkflowExtension;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentEditApplicationExtension;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveBatchExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveWriteExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditCreateExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveWriteExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditApplicationExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrOutpatientDocumentPageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentItemExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrOutpatientDocumentPageExtensionOut;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
 *   <li>{@link PractitionerQueryService} — 接诊医生姓名解析</li>
 *   <li>{@link OutpatientEmrListMapper} — Extension 行 → 页面 DTO</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar → POST /api/app/mrmsOutpatientEmrQuery/{method}；不另写 RestController}。</p>
 *
 * <p>列表与临床打开均以<strong>就诊 encounter</strong>为锚点（非整患者时间线）。
 * 前端编辑弹层须 {@code encounterScoped}：侧栏仅本就诊文书，禁止患者跨就诊「历史病历」。</p>
 *
 * <p>前端编辑门禁：登录人须为接诊医生（活动 ADMITTER）且病例未归档；
 * 「归档病例 / 解档病例」仅 {@code ROLE_MRMS_ADMIN}。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsOutpatientEmrQueryAppService {

    /** EMR 文书读侧 Extension。 */
    private final EmrDocumentExtension emrDocumentExtension;

    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;

    /** 就诊读侧。 */
    private final EncounterQueryService encounterQueryService;

    /** 机构读侧。 */
    private final OrganizationQueryService organizationQueryService;

    /** 医务人员读侧（接诊医生姓名筛选与回填）。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 列表行映射。 */
    private final OutpatientEmrListMapper outpatientEmrListMapper;

    /** 病案操作人/租户解析。 */
    private final MrmsOperatorResolver operatorResolver;

    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 临床病例归档/解档编排。 */
    private final MrmsClinicalCaseArchiveSupport clinicalCaseArchiveSupport;

    /** 文书归档三态工作流（增量提交归档）。 */
    private final EmrDocumentArchiveWorkflowExtension documentArchiveWorkflowExtension;

    /** 文书编辑申请（增量创建申请）。 */
    private final EmrDocumentEditApplicationExtension documentEditApplicationExtension;

    /**
     * 分页检索门诊病历文书。
     *
     * @param request 关键词/科室主键/接诊医生主键/就诊日期/状态与分页
     * @return 分页列表
     */
    public PagedResult<OutpatientEmrListItemDto> queryPage(QueryOutpatientEmrListRequest request) {
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
        List<EmrOutpatientDocumentItemExtensionOut> rows = page.getContent();
        // Extension 未返回 content 时按空页处理
        if (rows == null) {
            rows = List.of();
        }
        // Asm：映射 DTO 并回填患者/就诊/科室
        List<OutpatientEmrListItemDto> items = enrichListItems(rows);
        return PagedResultBuilder.build(
                new PagedResult<>(), items, page.getTotalCount(), page.getPageNo(), page.getPageSize());
    }

    /**
     * 归档病例：锁定该次门诊就诊下 EMR 文书（仅病案管理员）。
     *
     * @param request 含就诊主键
     */
    public void archiveClinicalCase(ArchiveOutpatientClinicalCaseRequest request) {
        // Gate：登录租户
        Long tenantId = operatorResolver.requireTenantId();
        // Cmd：按就诊锁定文书
        clinicalCaseArchiveSupport.archiveClinicalCase(request.getEncounterId(), tenantId);
    }

    /**
     * 解档病例：ARCHIVED → DRAFT（仅病案管理员；病案主档已归档时拒绝）。
     *
     * @param request 含就诊主键
     */
    public void unarchiveClinicalCase(ArchiveOutpatientClinicalCaseRequest request) {
        // Gate：登录租户
        Long tenantId = operatorResolver.requireTenantId();
        // Cmd：按就诊解锁文书
        clinicalCaseArchiveSupport.unarchiveClinicalCase(request.getEncounterId(), tenantId);
    }

    /**
     * 单份文书提交归档审批（IF-MRMS-OEMR-03）。
     *
     * <p><strong>增量</strong>：不改动 {@link #archiveClinicalCase}/{@link #unarchiveClinicalCase}。</p>
     *
     * @param request 文书主键
     */
    public void submitDocumentArchive(SubmitOutpatientDocumentArchiveRequest request) {
        // Gate：EMR 归档 SPI 可用 + 文书主键 + 登录操作人
        assertArchiveWorkflowEnabled();
        if (request == null || request.getDocumentId() == null) {
            throw new ValidationException("文书主键不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：组装 Extension 写 In
        EmrDocumentArchiveWriteExtensionIn in = new EmrDocumentArchiveWriteExtensionIn();
        in.setDocumentId(request.getDocumentId());
        in.setOperatorId(operatorId);
        // Cmd：DRAFT → PENDING_ARCHIVE
        EmrDocumentArchiveWriteExtensionOut out = documentArchiveWorkflowExtension.submitArchive(in);
        if (!out.isSuccess()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, out.getMessage());
        }
    }

    /**
     * 批量文书提交归档（IF-MRMS-OEMR-04）。
     *
     * @param request 文书 id 列表
     */
    public void submitDocumentArchiveBatch(SubmitOutpatientDocumentArchiveBatchRequest request) {
        // Gate：EMR 可用 + 列表非空
        assertArchiveWorkflowEnabled();
        if (request == null || request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
            throw new ValidationException("文书主键列表不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：批量 In
        EmrDocumentArchiveBatchExtensionIn in = new EmrDocumentArchiveBatchExtensionIn();
        in.setDocumentIds(request.getDocumentIds());
        in.setOperatorId(operatorId);
        // Cmd：批量 DRAFT → PENDING_ARCHIVE
        EmrDocumentArchiveWriteExtensionOut out = documentArchiveWorkflowExtension.submitArchiveBatch(in);
        if (!out.isSuccess()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, out.getMessage());
        }
    }

    /**
     * 创建门诊文书编辑申请（IF-MRMS-OEMR-05）。
     *
     * @param request 文书 + 原因
     * @return 申请主键
     */
    public Long createDocumentEditApplication(CreateOutpatientDocumentEditApplicationRequest request) {
        // Gate：编辑 SPI 可用 + 文书主键 + 申请原因
        if (!documentEditApplicationExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用");
        }
        if (request == null || request.getDocumentId() == null) {
            throw new ValidationException("文书主键不能为空");
        }
        String reason = StringUtils.trimToNull(request.getApplyReason());
        if (reason == null) {
            throw new ValidationException("申请原因不能为空");
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：组装创建 In（门诊 AMB）
        EmrDocumentEditCreateExtensionIn in = new EmrDocumentEditCreateExtensionIn();
        in.setTenantId(operatorResolver.requireTenantId());
        in.setDocumentId(request.getDocumentId());
        in.setEncounterClassCode(EncounterClass.AMB.getCode());
        in.setApplyReason(reason);
        in.setApplicantId(operatorId);
        in.setOperatorId(operatorId);
        // Cmd：SPI 创建申请
        EmrDocumentEditApplicationExtensionOut out = documentEditApplicationExtension.create(in);
        // Asm：返回申请主键
        return out.getApplicationId();
    }

    /**
     * 文书归档工作流可用门禁。
     */
    private void assertArchiveWorkflowEnabled() {
        if (!documentArchiveWorkflowExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用，无法提交文书归档");
        }
    }

    /**
     * 解析筛选条件为就诊主键集合。
     *
     * @param request 查询请求
     * @return 是否施加约束及命中就诊主键
     */
    private EncounterFilterResolution resolveEncounterFilter(QueryOutpatientEmrListRequest request) {
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
            LocalDateTime fromTime = null;
            if (request.getEncounterDateFrom() != null) {
                fromTime = request.getEncounterDateFrom().atStartOfDay();
            }
            LocalDateTime toTime = null;
            if (request.getEncounterDateTo() != null) {
                toTime = request.getEncounterDateTo().atTime(LocalTime.MAX);
            }
            List<Long> byDate = encounterQueryService.listOutpatientAmbEncounterIdsForCashierScope(
                    null, fromTime, toTime);
            encounterIds = intersectOrReplace(encounterIds, byDate);
        }
        // 就诊科室主键 → 门诊 AMB 就诊
        if (request.getOrganizationId() != null) {
            List<Long> byDept =
                encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(request.getOrganizationId());
            encounterIds = intersectOrReplace(encounterIds, byDept);
        }
        // 接诊医生主键 → 活动 ADMITTER 参与者对应就诊
        if (request.getAdmitterPractitionerId() != null) {
            List<Long> byDoctor = encounterQueryService.listEncounterIdsByActiveAdmitterPractitionerIds(
                List.of(request.getAdmitterPractitionerId()));
            encounterIds = intersectOrReplace(encounterIds, byDoctor);
        }
        if (encounterIds == null) {
            return new EncounterFilterResolution(false, Set.of());
        }
        return new EncounterFilterResolution(true, encounterIds);
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
    private List<OutpatientEmrListItemDto> enrichListItems(List<EmrOutpatientDocumentItemExtensionOut> rows) {
        List<OutpatientEmrListItemDto> items = new ArrayList<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        for (EmrOutpatientDocumentItemExtensionOut row : rows) {
            OutpatientEmrListItemDto dto = outpatientEmrListMapper.toListItem(row);
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
        // 活动接诊医生 ADMITTER → 姓名
        Map<Long, Long> admitterByEncounter =
            encounterQueryService.mapActiveAdmitterPractitionerIdByEncounterIds(encounterIds);
        LinkedHashSet<Long> doctorIds = new LinkedHashSet<>();
        for (Long doctorId : admitterByEncounter.values()) {
            if (doctorId != null) {
                doctorIds.add(doctorId);
            }
        }
        Map<Long, String> doctorNames = practitionerQueryService.mapNamesByIds(doctorIds);
        for (OutpatientEmrListItemDto dto : items) {
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
            Long admitterId = admitterByEncounter.get(dto.getEncounterId());
            if (admitterId != null) {
                // 回填接诊医生主键与姓名，供病案菜单按「本人接诊」控制可编辑
                dto.setAdmitterPractitionerId(admitterId);
                dto.setDoctorName(doctorNames.get(admitterId));
            }
            // 文书待归档审批或已归档 → 病例临床只读
            String status = StringUtils.trimToNull(dto.getStatusCode());
            boolean clinicallyArchived = false;
            if ("PENDING_ARCHIVE".equals(status) || "ARCHIVED".equals(status)) {
                clinicallyArchived = true;
            }
            dto.setClinicalArchived(clinicallyArchived);
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
