/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.recordedit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zxplat.application.mrms.caserecord.homepage.mapper.HomepageMapper;
import com.zxplat.application.mrms.caserecord.homepage.request.SaveHomepageRequest;
import com.zxplat.application.mrms.recordedit.EditTargetKind;
import com.zxplat.application.mrms.recordedit.dto.RecordEditApplicationDto;
import com.zxplat.application.mrms.recordedit.request.CreateRecordEditApplicationRequest;
import com.zxplat.application.mrms.recordedit.request.ListRecordEditHistoryRequest;
import com.zxplat.application.mrms.recordedit.request.PageRecordEditApplicationRequest;
import com.zxplat.application.mrms.recordedit.request.RecordEditApplicationIdRequest;
import com.zxplat.application.mrms.recordedit.request.RejectRecordEditRequest;
import com.zxplat.application.mrms.recordedit.request.SaveRecordEditDraftRequest;
import com.zxplat.application.shared.pagination.PagedResultBuilder;
import com.zxplat.application.shared.validator.MrmsOperatorResolver;
import com.zxplat.framework.core.application.paging.PageWindowGate;
import com.zxplat.domain.mrms.caserecord.command.HomepageSaveCommand;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecord;
import com.zxplat.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.zxplat.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.zxplat.domain.mrms.homepageedit.command.ApproveHomepageEditCommand;
import com.zxplat.domain.mrms.homepageedit.command.CreateHomepageEditApplicationCommand;
import com.zxplat.domain.mrms.homepageedit.entity.HomepageEditApplication;
import com.zxplat.domain.mrms.homepageedit.queries.HomepageEditApplicationPageQuery;
import com.zxplat.domain.mrms.homepageedit.queries.model.HomepageEditApplicationQueryModel;
import com.zxplat.domain.mrms.homepageedit.service.HomepageEditApplicationDomainService;
import com.zxplat.domain.mrms.homepageedit.service.HomepageEditApplicationQueryService;
import com.zxplat.domain.share.authorization.MrmsAdminAuthorizer;
import com.zxplat.domain.share.constants.MrmsErrorCodes;
import com.zxplat.domain.share.enums.CaseRecordStatus;
import com.zxplat.domain.infrastructure.integration.emr.EmrDocumentEditApplicationExtension;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditCreateExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditIdExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditPageExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditRejectExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditSaveDraftExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditApplicationExtensionOut;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditPageExtensionOut;
import com.zxplat.framework.core.application.dto.PagedResult;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 病历病案编辑申请门面（AS-MRMS-EDIT-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>按 {@code targetKind} 路由：document → {@link EmrDocumentEditApplicationExtension}；
 *       homepage → {@link HomepageEditApplicationDomainService}</li>
 *   <li>审核写路径经 {@link MrmsAdminAuthorizer}</li>
 *   <li><strong>禁止</strong>跨域共用一张编辑表或领域 UNION</li>
 * </ul>
 *
 * <p>接入：{@code POST /api/app/mrmsRecordEditFacade/{method}}；不另写 RestController。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsRecordEditFacadeAppService {

    /** 病案会话操作人/租户。 */
    private final MrmsOperatorResolver operatorResolver;

    /** 分页窗口门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 病案管理员鉴权（审核写路径）。 */
    private final MrmsAdminAuthorizer mrmsAdminAuthorizer;

    /** 文书编辑申请 SPI（document 分支）。 */
    private final EmrDocumentEditApplicationExtension documentEditExtension;

    /** 首页编辑申请写侧（homepage 分支）。 */
    private final HomepageEditApplicationDomainService homepageEditDomainService;

    /** 首页编辑申请读侧。 */
    private final HomepageEditApplicationQueryService homepageEditQueryService;

    /** 病案主档写/读（创建申请前置 archived 校验）。 */
    private final CaseRecordDomainService caseRecordDomainService;

    /** 首页实体与子表读侧（快照组装）。 */
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;

    /** 首页 DTO ↔ 保存命令映射。 */
    private final HomepageMapper homepageMapper;

    /** 工作稿 JSON 序列化/反序列化。 */
    private final ObjectMapper objectMapper;

    /**
     * 创建编辑申请（IF-MRMS-EDIT-01）。
     *
     * @param request 含 targetKind 与锚点
     * @return 申请 DTO
     */
    public RecordEditApplicationDto createEditApplication(CreateRecordEditApplicationRequest request) {
        // Gate：登录租户与操作人；申请原因必填
        Long operatorId = operatorResolver.requireOperatorId();
        Long tenantId = operatorResolver.requireTenantId();
        EditTargetKind kind = requireKind(request.getTargetKind());
        String reason = trimToNull(request.getApplyReason());
        if (reason == null) {
            throw new ValidationException("申请原因不能为空");
        }
        // Cmd：按 targetKind 仅调用一域创建
        if (kind == EditTargetKind.DOCUMENT) {
            return createDocumentEdit(request, tenantId, operatorId, reason);
        }
        // Asm：homepage 分支返回统一 DTO 形状
        return createHomepageEdit(request, tenantId, operatorId, reason);
    }

    /**
     * 打开申请（IF-MRMS-EDIT-02）。
     *
     * @param request 申请 id
     * @return 含双 JSON
     */
    public RecordEditApplicationDto getEditApplication(RecordEditApplicationIdRequest request) {
        // Gate：targetKind + 申请主键
        EditTargetKind kind = requireKind(request.getTargetKind());
        Long applicationId = requireApplicationId(request.getApplicationId());
        // Pre/Cmd：按域装载
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            EmrDocumentEditIdExtensionIn in = new EmrDocumentEditIdExtensionIn();
            in.setApplicationId(applicationId);
            // Asm：Extension Out → 统一 DTO（含双 JSON）
            return toDocumentDto(documentEditExtension.getById(in), true);
        }
        HomepageEditApplication entity = homepageEditDomainService.requireById(applicationId);
        return toHomepageDto(entity, true);
    }

    /**
     * 暂存工作稿（IF-MRMS-EDIT-03）。
     *
     * @param request 工作稿
     * @return 申请
     */
    public RecordEditApplicationDto saveEditDraft(SaveRecordEditDraftRequest request) {
        // Gate：登录 + 路由键 + 主键
        Long operatorId = operatorResolver.requireOperatorId();
        EditTargetKind kind = requireKind(request.getTargetKind());
        Long applicationId = requireApplicationId(request.getApplicationId());
        // Cmd：仅更新对应域 new_content_json
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            EmrDocumentEditSaveDraftExtensionIn in = new EmrDocumentEditSaveDraftExtensionIn();
            in.setApplicationId(applicationId);
            in.setNewContentJson(request.getNewContentJson());
            in.setOperatorId(operatorId);
            return toDocumentDto(documentEditExtension.saveDraft(in), true);
        }
        HomepageEditApplication entity = homepageEditDomainService.saveDraft(
                applicationId, request.getNewContentJson(), operatorId);
        return toHomepageDto(entity, true);
    }

    /**
     * 交审（IF-MRMS-EDIT-04）。
     *
     * @param request 申请 id
     * @return 申请
     */
    public RecordEditApplicationDto submitEditForReview(RecordEditApplicationIdRequest request) {
        // Gate：登录 + 路由键
        Long operatorId = operatorResolver.requireOperatorId();
        EditTargetKind kind = requireKind(request.getTargetKind());
        Long applicationId = requireApplicationId(request.getApplicationId());
        // Cmd：DRAFTING → PENDING_REVIEW
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            EmrDocumentEditIdExtensionIn in = new EmrDocumentEditIdExtensionIn();
            in.setApplicationId(applicationId);
            in.setOperatorId(operatorId);
            return toDocumentDto(documentEditExtension.submitForReview(in), true);
        }
        HomepageEditApplication entity = homepageEditDomainService.submitForReview(applicationId, operatorId);
        return toHomepageDto(entity, true);
    }

    /**
     * 审核列表（IF-MRMS-EDIT-05）。
     *
     * @param request 分页；targetKind 可空
     * @return 分页
     */
    public PagedResult<RecordEditApplicationDto> pageEditApplications(PageRecordEditApplicationRequest request) {
        // Gate：管理员看审核列表 + 分页窗口
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        String kindCode = trimToNull(request.getTargetKind());
        // Pre：空 kind 仅查首页，避免双域粗暴合并错页（前端应用 Tab）
        if (kindCode == null) {
            return pageHomepageEdit(request);
        }
        // Cmd：按 targetKind 单域分页
        EditTargetKind kind = requireKind(kindCode);
        if (kind == EditTargetKind.DOCUMENT) {
            return pageDocumentEdit(request);
        }
        return pageHomepageEdit(request);
    }

    /**
     * 对比双 JSON（IF-MRMS-EDIT-06）；复用 get。
     *
     * @param request 申请 id
     * @return 含双 JSON
     */
    public RecordEditApplicationDto compareEditApplication(RecordEditApplicationIdRequest request) {
        // Gate：管理员
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        // Cmd/Asm：与打开申请同路径，保证双 JSON 口径一致
        return getEditApplication(request);
    }

    /**
     * 同意编辑（IF-MRMS-EDIT-07）。
     *
     * @param request 申请 id
     * @return 申请
     */
    public RecordEditApplicationDto approveEdit(RecordEditApplicationIdRequest request) {
        // Gate：管理员 + 路由键
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        Long reviewerId = operatorResolver.requireOperatorId();
        EditTargetKind kind = requireKind(request.getTargetKind());
        Long applicationId = requireApplicationId(request.getApplicationId());
        // Cmd：文书走 SPI 合并；首页走 Domain + HomepageSaveCommand
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            EmrDocumentEditIdExtensionIn in = new EmrDocumentEditIdExtensionIn();
            in.setApplicationId(applicationId);
            in.setOperatorId(reviewerId);
            return toDocumentDto(documentEditExtension.approve(in), true);
        }
        return approveHomepageEdit(applicationId, reviewerId);
    }

    /**
     * 驳回编辑（IF-MRMS-EDIT-08）。
     *
     * @param request 含原因
     * @return 申请
     */
    public RecordEditApplicationDto rejectEdit(RejectRecordEditRequest request) {
        // Gate：管理员 + 驳回原因必填
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        String reason = trimToNull(request.getRejectReason());
        if (reason == null) {
            throw new ValidationException("请填写驳回原因");
        }
        Long reviewerId = operatorResolver.requireOperatorId();
        EditTargetKind kind = requireKind(request.getTargetKind());
        Long applicationId = requireApplicationId(request.getApplicationId());
        // Cmd：pending_review → rejected，保留工作稿
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            EmrDocumentEditRejectExtensionIn in = new EmrDocumentEditRejectExtensionIn();
            in.setApplicationId(applicationId);
            in.setRejectReason(reason);
            in.setOperatorId(reviewerId);
            return toDocumentDto(documentEditExtension.reject(in), true);
        }
        HomepageEditApplication entity = homepageEditDomainService.reject(applicationId, reason, reviewerId);
        return toHomepageDto(entity, true);
    }

    /**
     * 历史列表（IF-MRMS-EDIT-09）。
     *
     * @param request 锚点
     * @return 历史
     */
    public List<RecordEditApplicationDto> listEditHistory(ListRecordEditHistoryRequest request) {
        // Gate：路由键
        EditTargetKind kind = requireKind(request.getTargetKind());
        // Cmd：按锚点列历史（列表不带双 JSON）
        if (kind == EditTargetKind.DOCUMENT) {
            assertDocumentEditEnabled();
            if (request.getDocumentId() == null) {
                throw new ValidationException("文书主键不能为空");
            }
            List<EmrDocumentEditApplicationExtensionOut> history =
                    documentEditExtension.listHistoryByDocument(request.getDocumentId());
            List<RecordEditApplicationDto> rows = new ArrayList<>();
            for (EmrDocumentEditApplicationExtensionOut item : history) {
                rows.add(toDocumentDto(item, false));
            }
            return rows;
        }
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主键不能为空");
        }
        List<HomepageEditApplication> history =
                homepageEditQueryService.listHistoryByCaseRecord(request.getCaseRecordId());
        List<RecordEditApplicationDto> rows = new ArrayList<>();
        for (HomepageEditApplication entity : history) {
            rows.add(toHomepageDto(entity, false));
        }
        return rows;
    }

    /**
     * 创建文书编辑申请（Extension 写路径）。
     *
     * @param request 含 documentId
     * @param tenantId 租户
     * @param operatorId 申请人
     * @param reason 申请原因
     * @return 统一 DTO（含双 JSON）
     */
    private RecordEditApplicationDto createDocumentEdit(
            CreateRecordEditApplicationRequest request, Long tenantId, Long operatorId, String reason) {
        // Gate：EMR 启用 + 文书主键
        assertDocumentEditEnabled();
        if (request.getDocumentId() == null) {
            throw new ValidationException("文书主键不能为空");
        }
        // Pre：组装 Extension In
        EmrDocumentEditCreateExtensionIn in = new EmrDocumentEditCreateExtensionIn();
        in.setTenantId(tenantId);
        in.setDocumentId(request.getDocumentId());
        in.setEncounterClassCode(trimToNull(request.getEncounterClassCode()));
        in.setApplyReason(reason);
        in.setApplicantId(operatorId);
        in.setOperatorId(operatorId);
        // Cmd/Asm：SPI 创建 → 统一 DTO
        return toDocumentDto(documentEditExtension.create(in), true);
    }

    /**
     * 创建首页编辑申请（须已 archived；快照写入 old/new_content_json）。
     *
     * @param request 含 caseRecordId
     * @param tenantId 租户
     * @param operatorId 申请人
     * @param reason 申请原因
     * @return 统一 DTO（含双 JSON）
     */
    private RecordEditApplicationDto createHomepageEdit(
            CreateRecordEditApplicationRequest request, Long tenantId, Long operatorId, String reason) {
        // Gate：病案主键
        if (request.getCaseRecordId() == null) {
            throw new ValidationException("病案主键不能为空");
        }
        CaseRecord record = caseRecordDomainService.requireById(request.getCaseRecordId());
        // Gate：仅已归档可申请
        if (record.getStatusEnum() != CaseRecordStatus.ARCHIVED) {
            throw new ValidationException("仅已归档病案可申请编辑首页");
        }
        // Pre：装载首页并序列化为 SaveHomepageRequest 快照
        CaseRecordHomepage homepage = caseRecordHomepageDomainService.requireExistingByCaseRecordId(record.getId());
        String snapshotJson;
        try {
            SaveHomepageRequest snapshot = objectMapper.convertValue(
                    homepageMapper.toDto(
                            homepage,
                            caseRecordHomepageDomainService.listDiagnoses(record.getId()),
                            caseRecordHomepageDomainService.listProcedures(record.getId()),
                            caseRecordHomepageDomainService.listIcus(record.getId()),
                            caseRecordHomepageDomainService.listBlds(record.getId()),
                            record.getEncounterId()),
                    SaveHomepageRequest.class);
            snapshot.setCaseRecordId(record.getId());
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "首页编辑快照序列化失败");
        }
        // Cmd：创建申请（version 缺省按 1）
        CreateHomepageEditApplicationCommand command = new CreateHomepageEditApplicationCommand();
        command.setTenantId(tenantId);
        command.setCaseRecordId(record.getId());
        command.setEncounterId(record.getEncounterId());
        command.setApplyReason(reason);
        Integer fromVersion = homepage.getVersionNo();
        if (fromVersion == null) {
            fromVersion = 1;
        }
        command.setFromVersion(fromVersion);
        command.setContentJson(snapshotJson);
        command.setApplicantId(operatorId);
        command.setOperatorId(operatorId);
        HomepageEditApplication entity = homepageEditDomainService.create(command);
        // Asm：实体 → 统一 DTO
        return toHomepageDto(entity, true);
    }

    /**
     * 同意首页编辑：解析工作稿 → HomepageSaveCommand → Domain.approve。
     *
     * @param applicationId 申请主键
     * @param reviewerId 审核人
     * @return 统一 DTO
     */
    private RecordEditApplicationDto approveHomepageEdit(Long applicationId, Long reviewerId) {
        // Pre：装载申请并解析 new_content_json
        HomepageEditApplication entity = homepageEditDomainService.requireById(applicationId);
        SaveHomepageRequest saveRequest;
        try {
            saveRequest = objectMapper.readValue(entity.getNewContentJson(), SaveHomepageRequest.class);
        } catch (Exception ex) {
            throw new BusinessException(MrmsErrorCodes.HOMEPAGE_EDIT_INVALID_STATUS, "工作稿 JSON 无法解析为首页保存请求");
        }
        // Cmd：Domain 同意并落首页
        HomepageSaveCommand saveCommand = homepageMapper.toSaveCommand(saveRequest, reviewerId);
        ApproveHomepageEditCommand command = new ApproveHomepageEditCommand();
        command.setApplicationId(applicationId);
        command.setReviewerId(reviewerId);
        command.setHomepageSaveCommand(saveCommand);
        HomepageEditApplication saved = homepageEditDomainService.approve(command);
        // Asm：实体 → 统一 DTO
        return toHomepageDto(saved, true);
    }

    /**
     * 文书编辑审核分页（Extension）。
     *
     * @param request 分页条件
     * @return 统一 DTO 分页（不含双 JSON）
     */
    private PagedResult<RecordEditApplicationDto> pageDocumentEdit(PageRecordEditApplicationRequest request) {
        // Gate：EMR 启用
        assertDocumentEditEnabled();
        // Pre：组装 Extension 分页 In
        EmrDocumentEditPageExtensionIn in = new EmrDocumentEditPageExtensionIn();
        in.setTenantId(operatorResolver.requireTenantId());
        in.setStatusEnum(trimToNull(request.getStatusEnum()));
        in.setPageIndex(request.getPageNo());
        in.setPageSize(request.getBoundedPageSize());
        // Cmd：SPI 分页
        EmrDocumentEditPageExtensionOut page = documentEditExtension.pageForReview(in);
        // Asm：Out → 统一 DTO 列表
        List<RecordEditApplicationDto> rows = new ArrayList<>();
        for (EmrDocumentEditApplicationExtensionOut item : page.getContent()) {
            rows.add(toDocumentDto(item, false));
        }
        long total = 0L;
        if (page.getTotalCount() != null) {
            total = page.getTotalCount();
        }
        return PagedResultBuilder.build(
                new PagedResult<>(),
                rows,
                total,
                request.getPageNo(),
                request.getBoundedPageSize());
    }

    /**
     * 首页编辑审核分页（QueryService）。
     *
     * @param request 分页条件
     * @return 统一 DTO 分页（不含双 JSON）
     */
    private PagedResult<RecordEditApplicationDto> pageHomepageEdit(PageRecordEditApplicationRequest request) {
        // Pre：组装领域分页查询
        HomepageEditApplicationPageQuery query = new HomepageEditApplicationPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setStatusEnum(trimToNull(request.getStatusEnum()));
        query.setPageIndex(request.getPageNo());
        query.setPageSize(request.getBoundedPageSize());
        // Cmd：读侧分页
        Page<HomepageEditApplicationQueryModel> page = homepageEditQueryService.pageForReview(query);
        // Asm：QueryModel → 统一 DTO
        List<RecordEditApplicationDto> rows = new ArrayList<>();
        for (HomepageEditApplicationQueryModel model : page.getContent()) {
            RecordEditApplicationDto dto = new RecordEditApplicationDto();
            dto.setTargetKind(EditTargetKind.HOMEPAGE.getCode());
            dto.setApplicationId(model.getId());
            dto.setCaseRecordId(model.getCaseRecordId());
            dto.setStatusEnum(model.getStatusEnum());
            dto.setApplyReason(model.getApplyReason());
            dto.setRejectReason(model.getRejectReason());
            dto.setFromVersion(model.getFromVersion());
            dto.setToVersion(model.getToVersion());
            dto.setApplicantId(model.getApplicantId());
            dto.setAppliedAt(model.getAppliedAt());
            dto.setSubmittedAt(model.getSubmittedAt());
            dto.setReviewerId(model.getReviewerId());
            dto.setReviewedAt(model.getReviewedAt());
            rows.add(dto);
        }
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 文书编辑 SPI 未启用时拒绝 document 分支。
     */
    private void assertDocumentEditEnabled() {
        if (!documentEditExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用，无法办理文书编辑申请");
        }
    }

    /**
     * 解析并校验 targetKind。
     *
     * @param code 路由码
     * @return 枚举
     */
    private EditTargetKind requireKind(String code) {
        EditTargetKind kind = EditTargetKind.fromCode(code);
        if (kind == null) {
            throw new ValidationException("targetKind 须为 document 或 homepage");
        }
        return kind;
    }

    /**
     * 校验编辑申请主键非空。
     *
     * @param applicationId 申请主键
     * @return 同值
     */
    private Long requireApplicationId(Long applicationId) {
        if (applicationId == null) {
            throw new ValidationException("编辑申请主键不能为空");
        }
        return applicationId;
    }

    /**
     * Extension Out → 统一编辑申请 DTO。
     *
     * @param out SPI 出参
     * @param includeJson 是否带双 JSON
     * @return 统一 DTO
     */
    private RecordEditApplicationDto toDocumentDto(EmrDocumentEditApplicationExtensionOut out, boolean includeJson) {
        RecordEditApplicationDto dto = new RecordEditApplicationDto();
        dto.setTargetKind(EditTargetKind.DOCUMENT.getCode());
        if (out == null) {
            return dto;
        }
        dto.setApplicationId(out.getApplicationId());
        dto.setDocumentId(out.getDocumentId());
        dto.setStatusEnum(out.getStatusEnum());
        dto.setApplyReason(out.getApplyReason());
        dto.setRejectReason(out.getRejectReason());
        dto.setFromVersion(out.getFromVersion());
        dto.setToVersion(out.getToVersion());
        dto.setApplicantId(out.getApplicantId());
        dto.setAppliedAt(out.getAppliedAt());
        dto.setSubmittedAt(out.getSubmittedAt());
        dto.setReviewerId(out.getReviewerId());
        dto.setReviewedAt(out.getReviewedAt());
        if (includeJson) {
            dto.setOldContentJson(out.getOldContentJson());
            dto.setNewContentJson(out.getNewContentJson());
        }
        return dto;
    }

    /**
     * 首页编辑实体 → 统一编辑申请 DTO。
     *
     * @param entity 领域实体
     * @param includeJson 是否带双 JSON
     * @return 统一 DTO
     */
    private RecordEditApplicationDto toHomepageDto(HomepageEditApplication entity, boolean includeJson) {
        RecordEditApplicationDto dto = new RecordEditApplicationDto();
        dto.setTargetKind(EditTargetKind.HOMEPAGE.getCode());
        dto.setApplicationId(entity.getId());
        dto.setCaseRecordId(entity.getCaseRecordId());
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setApplyReason(entity.getApplyReason());
        dto.setRejectReason(entity.getRejectReason());
        dto.setFromVersion(entity.getFromVersion());
        dto.setToVersion(entity.getToVersion());
        dto.setApplicantId(entity.getApplicantId());
        dto.setAppliedAt(entity.getAppliedAt());
        dto.setSubmittedAt(entity.getSubmittedAt());
        dto.setReviewerId(entity.getReviewerId());
        dto.setReviewedAt(entity.getReviewedAt());
        if (includeJson) {
            dto.setOldContentJson(entity.getOldContentJson());
            dto.setNewContentJson(entity.getNewContentJson());
        }
        return dto;
    }
}
