/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.caserecord.homepage.service;

import com.openhis.health.application.mrms.caserecord.homepage.dto.CaseRecordDetailDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.CaseRecordListItemDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepageDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.HomepagePageDto;
import com.openhis.health.application.mrms.caserecord.homepage.dto.PdfGenerateResultDto;
import com.openhis.health.application.mrms.caserecord.homepage.mapper.CaseRecordDetailMapper;
import com.openhis.health.application.mrms.caserecord.homepage.mapper.CaseRecordListMapper;
import com.openhis.health.application.mrms.caserecord.homepage.mapper.HomepageMapper;
import com.openhis.health.application.mrms.caserecord.homepage.request.GenerateHomepagePdfRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.GetByEncounterIdRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.LoadPageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.QueryCaseRecordListRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.ReceiveHomepageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.RefreshAutoFillRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.ReturnToDoctorRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.SaveHomepageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.request.SubmitHomepageRequest;
import com.openhis.health.application.mrms.caserecord.homepage.support.HomepageOpenPdfRenderer;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.application.shared.financial.EncounterContractDisplayAssembler;
import com.openhis.health.application.shared.financial.EncounterInsutypeDisplayAssembler;
import com.openhis.health.application.shared.inpatient.AdmissionPrimaryDiagnosisAssembler;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.mrms.caserecord.command.RefreshAutoFillCommand;
import com.openhis.health.domain.mrms.caserecord.command.SubmitCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.command.TransitionCaseRecordCommand;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordPageQuery;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordQueryService;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.CaseRecordStatus;
import com.openhis.health.domain.share.enums.CaseRecordTransitionAction;
import com.openhis.health.domain.share.enums.WorkflowRole;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页应用服务（列表 / 编辑 / 医生站查档）。
 *
 * <p>入院登记成功后 {@code bootstrapIfAbsent} 建主档并发病案号；首页打开仅兜底历史未建档就诊。
 * 质控等工作台传病案状态仍查主档。路由前缀 {@code POST /api/app/mrmsCaseRecordHomepage/*}。</p>
 *
 * <p>「住院病例查询」菜单独立走 {@code MrmsInpatientEmrQueryAppService}（包 {@code mrms.inpatientemr}），
 * 不经本服务；本服务 {@link #queryPage} 仅服务病案首页工作台与质控等带状态过滤的列表。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordHomepageAppService {

    private final CaseRecordDomainService caseRecordDomainService;
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;
    private final CaseRecordQueryService caseRecordQueryService;
    private final CaseRecordDetailMapper caseRecordDetailMapper;
    private final CaseRecordListMapper caseRecordListMapper;
    private final HomepageMapper homepageMapper;
    private final HomepageOpenPdfRenderer homepageOpenPdfRenderer;
    private final MrmsOperatorResolver operatorResolver;
    private final PageWindowGate pageWindowGate;
    private final EncounterContextReader encounterContextReader;
    private final AdmissionPrimaryDiagnosisAssembler admissionPrimaryDiagnosisAssembler;
    private final PatientQueryService patientQueryService;
    private final EncounterContractDisplayAssembler encounterContractDisplayAssembler;
    private final EncounterInsutypeDisplayAssembler encounterInsutypeDisplayAssembler;

    /**
     * 分页检索病案列表（首页工作台 / 质控等工作台共用）。
     *
     * <p>有 {@code statusCode}/{@code statusCodes}（质控等）时查已建档主档；
     * 否则以住院就诊为行（首页工作台），已建档则 left join 回填病案号/上传状态。</p>
     *
     * @param request 检索条件
     * @return 分页结果
     */
    public PagedResult<CaseRecordListItemDto> queryPage(QueryCaseRecordListRequest request) {
        // Gate：分页窗口合法，防止大页拖垮列表
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        int pageSize = request.getPageSize();
        // 硬上限：异常大页拖垮主档/就诊分页
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // 质控等工作台按病案状态过滤 → 主档路径；首页工作台无状态码 → 住院就诊路径
        if (this.hasCaseRecordStatusFilter(request)) {
            return this.queryPageByCaseRecord(request);
        }
        return this.queryPageByInpatientEncounter(request);
    }

    /**
     * 是否按病案工作流状态过滤（质控台传 statusCodes；首页工作台不传）。
     *
     * @param request 列表请求
     * @return true 表示走主档分页
     */
    private boolean hasCaseRecordStatusFilter(QueryCaseRecordListRequest request) {
        if (trimToNull(request.getStatusCode()) != null) {
            return true;
        }
        List<String> codes = request.getStatusCodes();
        return codes != null && !codes.isEmpty();
    }

    /**
     * 已建档主档分页（质控等工作台）。
     *
     * @param request 检索条件（含病案状态）
     * @return 分页结果
     */
    private PagedResult<CaseRecordListItemDto> queryPageByCaseRecord(QueryCaseRecordListRequest request) {
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        // Pre：组装领域分页查询（入院/出院日按主档 adm_time、dscg_time 闭区间）
        CaseRecordPageQuery query = new CaseRecordPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setKeyword(request.getKeyword());
        query.setStatusCode(request.getStatusCode());
        query.setStatusCodes(request.getStatusCodes());
        query.setAdmCatyName(request.getAdmCatyName());
        query.setAttendingPractitionerId(request.getAttendingPractitionerId());
        query.setDischargedCode(request.getDischargedCode());
        String uploadStatus = trimToNull(request.getChsUploadStatusCode());
        // 兼容前端 homepageUploadStatus 别名
        if (uploadStatus == null) {
            uploadStatus = trimToNull(request.getHomepageUploadStatus());
        }
        query.setChsUploadStatusCode(uploadStatus);
        query.setSettleUploadStatus(request.getSettleUploadStatus());
        query.setSettledCode(request.getSettledCode());
        // 入院日闭区间 → 主档 adm_time
        if (request.getAdmFrom() != null) {
            query.setAdmFrom(request.getAdmFrom().atStartOfDay());
        }
        if (request.getAdmTo() != null) {
            query.setAdmTo(request.getAdmTo().atTime(LocalTime.MAX));
        }
        // 出院日闭区间 → 主档 dscg_time
        if (request.getDscgFrom() != null) {
            query.setDscgFrom(request.getDscgFrom().atStartOfDay());
        }
        if (request.getDscgTo() != null) {
            query.setDscgTo(request.getDscgTo().atTime(LocalTime.MAX));
        }
        Page<CaseRecordListItemQueryModel> page = caseRecordQueryService.queryPage(
                query,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "dscgTime", "id")));
        // Asm：映射列表 DTO
        List<CaseRecordListItemDto> items = new ArrayList<>();
        for (CaseRecordListItemQueryModel row : page.getContent()) {
            items.add(caseRecordListMapper.toListItem(row));
        }
        return PagedResultBuilder.build(new PagedResult<>(), items, page);
    }

    /**
     * 住院就诊分页（首页工作台）：未建档也可列出；打开/保存时再 bootstrap。
     *
     * <p>Asm 阶段写入 {@code insuranceTypeText}（合同名·险种短标，如市医保·职工）。</p>
     *
     * @param request 检索条件（关键字、出院标记、入院日、科室等）
     * @return 分页结果
     */
    private PagedResult<CaseRecordListItemDto> queryPageByInpatientEncounter(QueryCaseRecordListRequest request) {
        Long tenantId = operatorResolver.requireTenantId();
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
        String homepageUploadFilter = trimToNull(request.getChsUploadStatusCode());
        if (homepageUploadFilter == null) {
            homepageUploadFilter = trimToNull(request.getHomepageUploadStatus());
        }
        String settleUploadFilter = trimToNull(request.getSettleUploadStatus());
        // Asm：就诊行 → 列表 DTO；科室/主治/上传态本页内存过滤（底层无对应下推字段）
        List<CaseRecordListItemDto> items = new ArrayList<>();
        for (InpatientEncounterContextQueryModel ctx : contexts) {
            if (!this.matchesDeptAndAttending(ctx, deptFilter, attendingFilter)) {
                continue;
            }
            CaseRecord caseRecord = null;
            if (ctx.getEncounterId() != null) {
                caseRecord = caseRecordByEncounter.get(ctx.getEncounterId());
            }
            // 首页/结算上传状态筛选（none=未上传；其余精确匹配主档冗余码）
            if (!this.matchesUploadStatusFilter(caseRecord, homepageUploadFilter, settleUploadFilter)) {
                continue;
            }
            Patient patient = null;
            if (ctx.getPatientId() != null) {
                patient = patientById.get(ctx.getPatientId());
            }
            AdmissionPrimaryDiagnosisAssembler.PrimaryDiagnosisDisplay primary =
                    admissionPrimaryDiagnosisAssembler.resolvePrimaryDiagnosis(ctx.getDiagnosisJson());
            CaseRecordListItemDto item =
                    caseRecordListMapper.fromInpatientContext(ctx, caseRecord, patient, primary);
            // 费用性质：省市合同名 + 职工/居民（如「市医保·职工」）
            if (ctx.getEncounterId() != null) {
                item.setInsuranceTypeText(composeInsuranceTypeText(
                        contractDisplayByEncounterId.get(ctx.getEncounterId()),
                        insutypeByEncounterId.get(ctx.getEncounterId())));
            }
            items.add(item);
        }
        return PagedResultBuilder.build(new PagedResult<>(), items, page);
    }

    /**
     * 按就诊查病案主档（住院医生站查档；不自动建档）。
     *
     * @param request 含就诊主键
     * @return 主档详情；不存在时 null
     */
    public CaseRecordDetailDto getByEncounterId(GetByEncounterIdRequest request) {
        // Gate：就诊主键必填
        if (request.getEncounterId() == null) {
            throw new ValidationException("就诊主键不能为空");
        }
        // Pre：按租户+就诊查主档
        Long tenantId = operatorResolver.requireTenantId();
        CaseRecord record = caseRecordDomainService.findByEncounterId(tenantId, request.getEncounterId()).orElse(null);
        // 就诊尚未建档，查档返回空而非抛错
        if (record == null) {
            return null;
        }
        // Asm：映射详情 DTO
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 加载首页编辑页（主档 + 首页 + 诊断/手术子表）。
     *
     * <p>仅有 {@code encounterId} 时幂等建档（入院登记主路径已建则直接返回；含新建 autoFill）；
     * 已有 {@code caseRecordId} 时加载已有档。草稿/退回且未冻结时打开页软刷住院信息与费用
     * （出院科别/账单汇总等，尊重 manual），避免必须再点「导入」。</p>
     *
     * @param request 病案主键与/或就诊主键
     * @return 页面聚合 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepagePageDto loadPage(LoadPageRequest request) {
        // Gate：caseRecordId 与 encounterId 至少一个，否则无法定位编辑对象
        if (request.getCaseRecordId() == null && request.getEncounterId() == null) {
            throw new ValidationException("病案主档主键与就诊主键不能同时为空");
        }
        Long caseRecordId = request.getCaseRecordId();
        // Cmd：仅有就诊时幂等建档（新建含 autoFill），得到主档主键后再加载
        if (caseRecordId == null) {
            caseRecordId = this.bootstrapByEncounterId(request.getEncounterId());
        }
        // Pre：加载主档；首页不存在则懒创建空壳
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        Long operatorId = operatorResolver.resolveOperatorIdOrNull();
        // 打开页软刷无登录态时用系统占位操作人写审计
        if (operatorId == null) {
            operatorId = MrmsConstants.SYSTEM_OPERATOR_ID;
        }
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorId);
        // Cmd：可编辑态打开即软刷住院信息+费用（出院科别/账单；已归档/冻结只读库）
        if (this.shouldSoftRefreshHisOnOpen(record, homepage)) {
            homepage = caseRecordHomepageDomainService.mergeAutoFillFromHis(
                    record, List.of("inpatient", "fee"), false, operatorId);
        }
        // Asm：映射主档详情与首页 DTO
        HomepagePageDto page = new HomepagePageDto();
        page.setCaseRecord(caseRecordDetailMapper.toDetail(record));
        page.setHomepage(homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId())));
        return page;
    }

    /**
     * 打开页是否软刷 HIS 分区：医生可编辑态（draft/returned）且首页未冻结。
     *
     * @param record   病案主档
     * @param homepage 首页快照
     * @return true 表示应合并住院/费用等分区
     */
    private boolean shouldSoftRefreshHisOnOpen(CaseRecord record, CaseRecordHomepage homepage) {
        // 无首页或已冻结：只读库内快照
        if (homepage == null || Boolean.TRUE.equals(homepage.getFrozen())) {
            return false;
        }
        CaseRecordStatus status = record.getStatusEnum();
        // 仅草稿/退回允许打开时写回自动取值字段
        return status == CaseRecordStatus.DRAFT || status == CaseRecordStatus.RETURNED;
    }

    /**
     * 保存首页标量、扩展 JSON 与诊断/手术子表。
     *
     * <p>无 {@code caseRecordId} 时须有 {@code encounterId}，先 bootstrap 再保存。</p>
     *
     * @param request 保存请求
     * @return 保存后的首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto saveHomepage(SaveHomepageRequest request) {
        // Gate：无主档主键时必须带就诊主键以便建档
        if (request.getCaseRecordId() == null) {
            if (request.getEncounterId() == null) {
                throw new ValidationException("病案主档主键为空时就诊主键不能为空");
            }
            // Cmd：首次保存前幂等建档，把得到的 id 写入请求再走保存
            Long bootstrappedId = this.bootstrapByEncounterId(request.getEncounterId());
            request.setCaseRecordId(bootstrappedId);
        }
        Long operatorId = operatorResolver.requireOperatorId();
        // Pre：HTTP 请求转领域保存命令
        // Cmd：领域校验可编辑态并持久化首页与子表
        CaseRecordHomepage homepage = caseRecordDomainService.saveHomepage(
                homepageMapper.toSaveCommand(request, operatorId));
        // Asm：重新加载子表并映射出参
        return homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 按选定段从 HIS 刷新自动取值字段。
     *
     * @param request 刷新段列表与是否强制覆盖
     * @return 刷新后的首页 DTO
     */
    @Transactional(rollbackFor = Exception.class)
    public HomepageDto refreshAutoFill(RefreshAutoFillRequest request) {
        // Gate：病案主键与刷新段非空
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (request.getSections() == null || request.getSections().isEmpty()) {
            // 未指定刷新段则无法合并 HIS 快照
            throw new ValidationException("刷新段不能为空");
        }
        // Pre：组装刷新命令
        RefreshAutoFillCommand command = new RefreshAutoFillCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setSections(request.getSections());
        command.setForceOverwrite(request.isForceOverwrite());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域合并 HIS 快照写回首页
        CaseRecordHomepage homepage = caseRecordDomainService.refreshAutoFill(command);
        // Asm：映射出参
        return homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(request.getCaseRecordId()),
                caseRecordHomepageDomainService.listProcedures(request.getCaseRecordId()));
    }

    /**
     * 住院医师提交首页至病案室（draft → submitted）。
     *
     * @param request 提交请求
     * @return 提交后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto submit(SubmitHomepageRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：组装提交命令
        SubmitCaseRecordCommand command = new SubmitCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域校验首页完整性并迁移状态
        CaseRecord record = caseRecordDomainService.submit(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 病案室接收已提交病案（submitted → received）。
     *
     * @param request 接收请求
     * @return 接收后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto receive(ReceiveHomepageRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：组装病案室接收迁移命令
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RECEIVE);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域状态机迁移
        CaseRecord record = caseRecordDomainService.transition(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 病案室退回医生修改（submitted/received → draft）。
     *
     * @param request 含退回原因
     * @return 退回后主档详情
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordDetailDto returnToDoctor(ReturnToDoctorRequest request) {
        // Gate：病案主键与退回原因必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        if (trimToNull(request.getReturnReason()) == null) {
            throw new ValidationException("退回原因不能为空");
        }
        // Pre：组装退回迁移命令
        TransitionCaseRecordCommand command = new TransitionCaseRecordCommand();
        command.setCaseRecordId(request.getCaseRecordId());
        command.setAction(CaseRecordTransitionAction.RETURN);
        command.setRole(WorkflowRole.RECORD_ROOM);
        command.setReturnReason(request.getReturnReason());
        command.setOperatorId(operatorResolver.requireOperatorId());
        // Cmd：领域状态机迁移并记录退回原因
        CaseRecord record = caseRecordDomainService.transition(command);
        // Asm：映射主档详情
        return caseRecordDetailMapper.toDetail(record);
    }

    /**
     * 生成病案首页 PDF 预览（OpenPDF 简化版式；医生站查档共用）。
     *
     * @param request 含病案主键
     * @return PDF Base64 与页数
     */
    public PdfGenerateResultDto generateHomepagePdf(GenerateHomepagePdfRequest request) {
        // Gate：病案主键必填
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主档主键不能为空");
        }
        // Pre：加载主档与首页子表
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.ensureHomepage(
                record.getId(), record.getTenantId(), operatorResolver.resolveOperatorIdOrNull());
        HomepageDto dto = homepageMapper.toDto(
                homepage,
                caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                caseRecordHomepageDomainService.listProcedures(record.getId()));
        // Asm：渲染 PDF
        return homepageOpenPdfRenderer.render(dto);
    }

    /**
     * 组装住院就诊列表查询：关键字、诊断快照、出院态/结算态并集、可选入院/出院日闭区间。
     *
     * <p>不设 {@code doctorStationQueue}，避免仅剩在科队列。出院态与结算态经登记状态码
     * 求交集后下推 {@code registrationListStatuses}/{@code statusEnumIn}。
     * 入院日起止 → {@code admissionDateFrom}/{@code admissionDateTo}；
     * 出院日起止 → {@code dischargeDateFrom}/{@code dischargeDateTo}。</p>
     *
     * @param request 列表检索请求
     * @return 住院就诊分页查询
     */
    private InpatientEncounterPageQuery buildInpatientListQuery(QueryCaseRecordListRequest request) {
        InpatientEncounterPageQuery query = new InpatientEncounterPageQuery();
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        query.setKeyword(request.getKeyword());
        query.setIncludeDiagnosisSnapshot(Boolean.TRUE);
        List<String> registrationStatuses = this.resolveRegistrationListStatuses(request.getDischargedCode());
        // Pre：与「是否结算」筛选求交，避免已结算患者仍被未结算口径漏掉
        registrationStatuses = this.intersectWithSettledFilter(registrationStatuses, request.getSettledCode());
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
     * 将出院态并集与「是否结算」筛选求交；未传结算筛选时原样返回。
     *
     * @param registrationStatuses 出院态解析结果
     * @param settledCode          {@code "0"}/ {@code "1"}/空
     * @return 交集后的登记状态码；矛盾条件时可能为空列表
     */
    private List<String> intersectWithSettledFilter(List<String> registrationStatuses, String settledCode) {
        List<String> settledStatuses = this.resolveSettledRegistrationStatuses(settledCode);
        // 未筛选结算：不收窄出院态并集
        if (settledStatuses == null) {
            return registrationStatuses;
        }
        List<String> intersected = new ArrayList<>();
        // 只保留同时满足出院筛选与结算筛选的状态
        for (String status : registrationStatuses) {
            if (settledStatuses.contains(status)) {
                intersected.add(status);
            }
        }
        return intersected;
    }

    /**
     * 按结算标记解析登记列表状态码（与护士站「已结算」口径一致）。
     *
     * @param settledCode {@code "0"}/ {@code "1"}/空
     * @return 结算相关状态码；空表示不下推结算过滤
     */
    private List<String> resolveSettledRegistrationStatuses(String settledCode) {
        String code = trimToNull(settledCode);
        // 已结算：COMPLETED（待清床）+ DISCHARGED（已离院）
        if (MrmsConstants.LIST_FLAG_YES.equals(code)) {
            return Arrays.asList(
                    InpatientRegistrationConstants.LIST_STATUS_COMPLETED,
                    InpatientRegistrationConstants.LIST_STATUS_DISCHARGED);
        }
        // 未结算：在院轨至待结算（不含已结）
        if (MrmsConstants.LIST_FLAG_NO.equals(code)) {
            return Arrays.asList(
                    InpatientRegistrationConstants.LIST_STATUS_WAIT_ADMISSION,
                    InpatientRegistrationConstants.LIST_STATUS_ADMITTED,
                    InpatientRegistrationConstants.LIST_STATUS_ON_HOLD,
                    InpatientRegistrationConstants.LIST_STATUS_PENDING);
        }
        return null;
    }

    /**
     * 登记列表状态码 → 就诊 {@link com.openhis.health.domain.share.enums.EncounterStatus#getCode()} 集合（供 statusEnumIn）。
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
     * <p>示例：{@code 市医保·职工}；仅合同或仅险种时返回单段；皆空返回 {@code null}。</p>
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
        // 有合同无险种：只展示合同名（与护士站卡片同口径）
        if (shortType == null) {
            return contract;
        }
        return contract + "·" + shortType;
    }

    /**
     * 险种短标签：与前端 {@code formatInsutypeShort} 同口径（310→职工，390→居民，340→离休）。
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

    /**
     * 本页内存匹配首页/结算上传状态（与主档路径 {@code chs_upload_status_code} 同口径）。
     *
     * <p>{@code none}=未建档或上传码为空；其余精确匹配 {@link com.openhis.health.domain.share.enums.ChsUploadStatus}。
     * 结算上传本期主档无冗余列，实际恒为空，非 {@code none} 时不命中。</p>
     *
     * @param caseRecord           主档；未建档时 null
     * @param homepageUploadFilter 首页上传筛选；空不过滤
     * @param settleUploadFilter   结算上传筛选；空不过滤
     * @return true 表示保留本行
     */
    private boolean matchesUploadStatusFilter(
            CaseRecord caseRecord, String homepageUploadFilter, String settleUploadFilter) {
        if (homepageUploadFilter != null) {
            String actual = null;
            if (caseRecord != null) {
                actual = trimToNull(caseRecord.getChsUploadStatusCode());
            }
            // 首页上传码与筛选不一致则剔除
            if (!this.matchesSingleUploadStatus(actual, homepageUploadFilter)) {
                return false;
            }
        }
        if (settleUploadFilter != null) {
            String actual = null;
            if (caseRecord != null) {
                actual = trimToNull(caseRecord.getSettleUploadStatusCode());
            }
            // 结算上传：none=未传；1=已传未提交；2=已提交（与 MrmsSettleUploadFlags 对齐）
            if (!this.matchesSettleUploadStatus(actual, settleUploadFilter)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 结算清单上传状态匹配（0/空→none；亦可直接匹配 0/1/2）。
     *
     * @param actualCode 主档 settle_upload_status_code
     * @param filterCode none / 0 / 1 / 2 / success 等
     * @return true 命中
     */
    private boolean matchesSettleUploadStatus(String actualCode, String filterCode) {
        String filter = trimToNull(filterCode);
        if (filter == null) {
            return true;
        }
        String actual = trimToNull(actualCode);
        if ("none".equals(filter) || "0".equals(filter)) {
            return actual == null || "0".equals(actual);
        }
        return filter.equals(actual);
    }

    /**
     * 单字段上传状态匹配。
     *
     * @param actualCode 主档冗余码；空表示未上传
     * @param filterCode {@code none} 或 ChsUploadStatus.code
     * @return true 表示命中
     */
    private boolean matchesSingleUploadStatus(String actualCode, String filterCode) {
        // none：未上传（null/空）
        if ("none".equals(filterCode)) {
            return actualCode == null;
        }
        return filterCode.equals(actualCode);
    }

    /**
     * 按就诊幂等建档：读住院上下文取 patientId，再 {@code bootstrapIfAbsent}。
     *
     * @param encounterId 住院就诊主键
     * @return 病案主档主键
     */
    private Long bootstrapByEncounterId(Long encounterId) {
        // Pre：就诊须存在且能解析患者，否则无法建档
        InpatientEncounterContextQueryModel context =
                encounterContextReader.getInpatientContextByEncounterId(encounterId).orElse(null);
        if (context == null) {
            throw new ValidationException("住院就诊不存在或不可见");
        }
        if (context.getPatientId() == null) {
            throw new ValidationException("就诊缺少患者主键，无法建档");
        }
        Long tenantId = operatorResolver.requireTenantId();
        Long operatorId = operatorResolver.resolveOperatorIdOrNull();
        // Cmd：幂等建档（新建含 autoFill）
        return caseRecordDomainService.bootstrapIfAbsent(
                tenantId, context.getPatientId(), encounterId, operatorId);
    }
}
