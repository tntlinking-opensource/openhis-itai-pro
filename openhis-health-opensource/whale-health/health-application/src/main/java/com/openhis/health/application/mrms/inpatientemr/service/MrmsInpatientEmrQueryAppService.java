/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.inpatientemr.service;

import com.openhis.health.application.mrms.inpatientemr.dto.InpatientEmrListItemDto;
import com.openhis.health.application.mrms.inpatientemr.mapper.InpatientEmrListMapper;
import com.openhis.health.application.mrms.inpatientemr.request.ArchiveInpatientClinicalCaseRequest;
import com.openhis.health.application.mrms.inpatientemr.request.QueryInpatientEmrListRequest;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.financial.EncounterInsutypeDisplayAssembler;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.shared.mrms.MrmsClinicalCaseArchiveSupport;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordQueryService;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院病例查询应用服务（病案管理菜单；列表检索，同入院科室大夫可编辑临床）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link EncounterContextReader} — 住院就诊分页（勿开医生站在科队列）</li>
 *   <li>{@link CaseRecordQueryService} — 本页就诊批量映射主档（可未建档）</li>
 *   <li>{@link PatientQueryService} — 证件号回填</li>
 *   <li>{@link EncounterContractDisplayAssembler} / {@link EncounterInsutypeDisplayAssembler} — 费用性质</li>
 *   <li>{@link InpatientEmrListMapper} — 就诊上下文 → 页面 DTO（含 organizationId）</li>
 * </ul>
 *
 * <p>与 {@code MrmsCaseRecordHomepageAppService#queryPage} 区分：本服务仅服务「住院病例查询」菜单；
 * 病案首页/质控列表仍走 homepage。接入：
 * {@code AppServiceControllerRegistrar → POST /api/app/mrmsInpatientEmrQuery/{method}}。</p>
 *
 * <p>列表与临床打开均以<strong>住院就诊 encounter</strong>为锚点（非整患者时间线）。
 * 前端病历弹层须 {@code encounterScoped}：侧栏仅本就诊文书。</p>
 *
 * <p>前端编辑门禁：同入院科室大夫可写；已「归档病例」只读；
 * 「归档病例 / 解档病例」仅 {@code ROLE_MRMS_ADMIN}。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsInpatientEmrQueryAppService {

    /** 病案操作人/租户解析。 */
    private final MrmsOperatorResolver operatorResolver;

    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 住院就诊上下文读侧。 */
    private final EncounterContextReader encounterContextReader;

    /** 病案主档读侧（按就诊批量映射）。 */
    private final CaseRecordQueryService caseRecordQueryService;

    /** 患者读侧。 */
    private final PatientQueryService patientQueryService;

    /** 入院主诊断快照解析。 */
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;

    /** 合同展示名装配。 */
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;

    /** 险种码装配。 */
    private final EncounterInsutypeDisplayAssembler encounterInsutypeDisplayAssembler;

    /** 列表行映射。 */
    private final InpatientEmrListMapper inpatientEmrListMapper;

    /** 临床病例归档/解档编排。 */
    private final MrmsClinicalCaseArchiveSupport clinicalCaseArchiveSupport;

    /**
     * 分页检索住院病例（以住院就诊为行；已建档则回填病案号/状态）。
     *
     * @param request 关键词/科室/入院出院日/是否出院与分页
     * @return 分页列表
     */
    public PagedResult<InpatientEmrListItemDto> queryPage(QueryInpatientEmrListRequest request) {
        // Gate：分页窗口合法；校验登录租户可读
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        Long tenantId = operatorResolver.requireTenantId();
        int pageSize = request.getPageSize();
        // 分页上限 200，防止大页拖垮列表
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：组装住院就诊分页（勿开 doctorStationQueue，否则可能只剩在科可见范围）
        InpatientEncounterPageQuery encounterQuery = this.buildInpatientListQuery(request);
        Page<InpatientEncounterContextQueryModel> page =
                encounterContextReader.pageInpatientEncounterContexts(encounterQuery);
        List<InpatientEncounterContextQueryModel> contexts = page.getContent();
        List<Long> encounterIds = new ArrayList<>(contexts.size());
        List<Long> patientIds = new ArrayList<>(contexts.size());
        for (InpatientEncounterContextQueryModel ctx : contexts) {
            if (ctx.getEncounterId() != null) {
                encounterIds.add(ctx.getEncounterId());
            }
            if (ctx.getPatientId() != null) {
                patientIds.add(ctx.getPatientId());
            }
        }
        // Pre：本页就诊批量映射主档 + 患者证件号 + 合同展示名 + 险种
        Map<Long, CaseRecord> caseRecordByEncounter =
                caseRecordQueryService.mapByEncounterIds(tenantId, encounterIds);
        Map<Long, Patient> patientById = patientQueryService.loadPatientEntityMapByIds(patientIds);
        Map<Long, String> contractDisplayByEncounterId =
                encounterContractDisplayAssembler.mapContractDisplayByEncounterIds(encounterIds);
        Map<Long, String> insutypeByEncounterId =
                encounterInsutypeDisplayAssembler.mapInsutypeByEncounterIds(encounterIds);
        String deptFilter = trimToNull(request.getAdmCatyName());
        Long attendingFilter = request.getAttendingPractitionerId();
        // Asm：就诊行 → 列表 DTO；科室/主治本页内存过滤（底层无对应下推字段）
        List<InpatientEmrListItemDto> items = new ArrayList<>();
        for (InpatientEncounterContextQueryModel ctx : contexts) {
            if (!this.matchesDeptAndAttending(ctx, deptFilter, attendingFilter)) {
                continue;
            }
            CaseRecord caseRecord = null;
            if (ctx.getEncounterId() != null) {
                caseRecord = caseRecordByEncounter.get(ctx.getEncounterId());
            }
            Patient patient = null;
            if (ctx.getPatientId() != null) {
                patient = patientById.get(ctx.getPatientId());
            }
            AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primary =
                    admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosis(ctx.getDiagnosisJson());
            InpatientEmrListItemDto item =
                    inpatientEmrListMapper.fromInpatientContext(ctx, caseRecord, patient, primary);
            // 费用性质：省市合同名 + 职工/居民（如「市医保·职工」）
            if (ctx.getEncounterId() != null) {
                item.setInsuranceTypeText(composeInsuranceTypeText(
                        contractDisplayByEncounterId.get(ctx.getEncounterId()),
                        insutypeByEncounterId.get(ctx.getEncounterId())));
            }
            items.add(item);
        }
        // Asm：回填病例归档态（就诊下 LOCKED 文书）
        Set<Long> clinicallyArchived = clinicalCaseArchiveSupport.filterClinicallyArchivedEncounterIds(encounterIds);
        for (InpatientEmrListItemDto item : items) {
            if (item.getEncounterId() != null && clinicallyArchived.contains(item.getEncounterId())) {
                item.setClinicalArchived(true);
            }
        }
        return PagedResultBuilder.build(new PagedResult<>(), items, page);
    }

    /**
     * 归档病例：锁定该次住院就诊下 EMR 文书（仅病案管理员）。
     *
     * @param request 含就诊主键
     */
    public void archiveClinicalCase(ArchiveInpatientClinicalCaseRequest request) {
        // Gate：登录租户
        Long tenantId = operatorResolver.requireTenantId();
        // Cmd：按就诊锁定文书
        clinicalCaseArchiveSupport.archiveClinicalCase(request.getEncounterId(), tenantId);
    }

    /**
     * 解档病例：解锁 LOCKED 文书（仅病案管理员；病案主档已归档时拒绝）。
     *
     * @param request 含就诊主键
     */
    public void unarchiveClinicalCase(ArchiveInpatientClinicalCaseRequest request) {
        // Gate：登录租户
        Long tenantId = operatorResolver.requireTenantId();
        // Cmd：按就诊解锁文书
        clinicalCaseArchiveSupport.unarchiveClinicalCase(request.getEncounterId(), tenantId);
    }

    /**
     * 组装住院就诊列表查询：关键字、诊断快照、出院态并集、可选入院/出院日闭区间。
     *
     * <p>不设 {@code doctorStationQueue}，避免仅剩在科队列。</p>
     *
     * @param request 列表检索请求
     * @return 住院就诊分页查询
     */
    private InpatientEncounterPageQuery buildInpatientListQuery(QueryInpatientEmrListRequest request) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setKeyword(request.getKeyword());
        query.setIncludeDiagnosisSnapshot(Boolean.TRUE);
        List<String> registrationStatuses = this.resolveRegistrationListStatuses(request.getDischargedCode());
        query.setRegistrationListStatuses(registrationStatuses);
        query.setStatusEnumIn(this.toEncounterStatusCodes(registrationStatuses));
        // 入院日闭区间 → startTime
        if (request.getAdmFrom() != null) {
            query.setAdmissionDateFrom(request.getAdmFrom());
        }
        if (request.getAdmTo() != null) {
            query.setAdmissionDateTo(request.getAdmTo());
        }
        // 出院日闭区间 → endTime（在院无出院时间不命中）
        if (request.getDscgFrom() != null) {
            query.setDischargeDateFrom(request.getDscgFrom());
        }
        if (request.getDscgTo() != null) {
            query.setDischargeDateTo(request.getDscgTo());
        }
        return query;
    }

    /**
     * 按出院标记解析登记列表状态码并集（映射到就诊 status_enum）。
     *
     * @param dischargedCode {@code "0"}/ {@code "1"}/空
     * @return 登记列表状态码列表
     */
    private List<String> resolveRegistrationListStatuses(String dischargedCode) {
        String code = trimToNull(dischargedCode);
        // 未出院：在科/暂离/待结算
        if (MrmsConstants.LIST_FLAG_NO.equals(code)) {
            return Arrays.asList(
                    InpatientRegistrationConstants.LIST_STATUS_ADMITTED,
                    InpatientRegistrationConstants.LIST_STATUS_ON_HOLD,
                    InpatientRegistrationConstants.LIST_STATUS_PENDING);
        }
        // 已出院：已结算待清床/已出院
        if (MrmsConstants.LIST_FLAG_YES.equals(code)) {
            return Arrays.asList(
                    InpatientRegistrationConstants.LIST_STATUS_COMPLETED,
                    InpatientRegistrationConstants.LIST_STATUS_DISCHARGED);
        }
        // 全部：入院后可见态（含待入科）+ 出院态，排除取消/错误录入
        return Arrays.asList(
                InpatientRegistrationConstants.LIST_STATUS_WAIT_ADMISSION,
                InpatientRegistrationConstants.LIST_STATUS_ADMITTED,
                InpatientRegistrationConstants.LIST_STATUS_ON_HOLD,
                InpatientRegistrationConstants.LIST_STATUS_PENDING,
                InpatientRegistrationConstants.LIST_STATUS_COMPLETED,
                InpatientRegistrationConstants.LIST_STATUS_DISCHARGED);
    }

    /**
     * 登记列表状态码 → 就诊 status_enum code 集合（供 statusEnumIn）。
     *
     * @param registrationStatuses 登记列表状态码
     * @return 就诊状态 code 列表
     */
    private List<String> toEncounterStatusCodes(List<String> registrationStatuses) {
        List<String> codes = new ArrayList<>(registrationStatuses.size());
        for (String listStatus : registrationStatuses) {
            String encounterStatus = InpatientRegistrationConstants.toEncounterStatusCode(listStatus);
            if (encounterStatus != null) {
                codes.add(encounterStatus);
            }
        }
        return codes;
    }

    /**
     * 费用性质展示：合同名（省/市医保等）+ 险种短标（职工/居民）。
     *
     * @param contractDisplay 合同展示名
     * @param insutype        险种码（310/390/340）
     * @return 拼接文案；皆空为 null
     */
    private static String composeInsuranceTypeText(String contractDisplay, String insutype) {
        String contract = trimToNull(contractDisplay);
        String shortType = toInsutypeShortLabel(insutype);
        // 无合同（如自费）时仅展示险种短标；皆空则列表列显示 —
        if (contract == null) {
            return shortType;
        }
        // 有合同无险种：只展示合同名
        if (shortType == null) {
            return contract;
        }
        return contract + "·" + shortType;
    }

    /**
     * 险种短标签：与前端 formatInsutypeShort 同口径（310→职工，390→居民，340→离休）。
     *
     * @param insutype 险种码
     * @return 短标；未知码返回 null（不拼原始码）
     */
    private static String toInsutypeShortLabel(String insutype) {
        String code = trimToNull(insutype);
        if (code == null) {
            return null;
        }
        // 职工基本医疗保险
        if ("310".equals(code)) {
            return "职工";
        }
        // 城乡居民基本医疗保险
        if ("390".equals(code)) {
            return "居民";
        }
        // 离休人员医疗保障
        if ("340".equals(code)) {
            return "离休";
        }
        return null;
    }

    /**
     * 本页内存过滤入院科室名与主治医师（底层 InpatientEncounterPageQuery 无对应字段）。
     *
     * @param ctx             就诊上下文
     * @param deptFilter      入院科室名精确匹配；空不过滤
     * @param attendingFilter 主治医师主键；空不过滤
     * @return true 表示保留本行
     */
    private boolean matchesDeptAndAttending(
            InpatientEncounterContextQueryModel ctx, String deptFilter, Long attendingFilter) {
        if (deptFilter != null) {
            String orgName = ctx.getOrganizationName();
            // 科室名不匹配则剔除本行
            if (orgName == null || !deptFilter.equals(orgName)) {
                return false;
            }
        }
        if (attendingFilter != null) {
            // 主治不匹配则剔除本行
            if (!attendingFilter.equals(ctx.getAttendingDoctorId())) {
                return false;
            }
        }
        return true;
    }
}
