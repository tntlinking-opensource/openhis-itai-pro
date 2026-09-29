/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.archiveapproval.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.mrms.archiveapproval.ArchiveApprovalBizCategory;
import com.openhis.health.application.mrms.archiveapproval.dto.PendingArchiveDetailDto;
import com.openhis.health.application.mrms.archiveapproval.dto.PendingArchiveListItemDto;
import com.openhis.health.application.mrms.archiveapproval.request.ApproveArchiveBatchRequest;
import com.openhis.health.application.mrms.archiveapproval.request.ApproveArchiveRequest;
import com.openhis.health.application.mrms.archiveapproval.request.GetPendingArchiveDetailRequest;
import com.openhis.health.application.mrms.archiveapproval.request.PagePendingArchiveRequest;
import com.openhis.health.application.mrms.archiveapproval.request.RejectArchiveBatchRequest;
import com.openhis.health.application.mrms.archiveapproval.request.RejectArchiveRequest;
import com.openhis.health.application.mrms.caserecord.homepage.mapper.HomepageMapper;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.MrmsOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecord;
import com.openhis.health.domain.mrms.caserecord.entity.CaseRecordHomepage;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordPageQuery;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordHomepageDomainService;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordQueryService;
import com.openhis.health.domain.share.authorization.MrmsAdminAuthorizer;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.infrastructure.integration.emr.EmrDocumentArchiveWorkflowExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveDetailExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchivePageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveWriteExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveDetailExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchivePageExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveWriteExtensionOut;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病历病案归档审批应用服务（AS-MRMS-APR-001）。
 *
 * <p>职责边界：</p>
 * <ul>
 *   <li>{@link MrmsAdminAuthorizer} — 仅病案管理员可审批</li>
 *   <li>{@link EmrDocumentArchiveWorkflowExtension} — 文书三态审批（新增 SPI，不改旧 lockAll）</li>
 *   <li>{@link CaseRecordDomainService} — 首页主档 approve/rejectArchive</li>
 *   <li>{@link CaseRecordQueryService} — 待审病案分页</li>
 * </ul>
 *
 * <p>接入：{@code AppServiceControllerRegistrar → POST /api/app/mrmsRecordArchiveApproval/{method}}；
 * 不另写 RestController。</p>
 *
 * <p><strong>增量纪律</strong>：不修改/替换既有「归档病例」一键锁定接口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MrmsRecordArchiveApprovalAppService {

    /** 病案管理员鉴权。 */
    private final MrmsAdminAuthorizer mrmsAdminAuthorizer;

    /** 操作人/租户。 */
    private final MrmsOperatorResolver operatorResolver;

    /** 分页门禁。 */
    private final PageWindowGate pageWindowGate;

    /** 文书归档工作流 SPI。 */
    private final EmrDocumentArchiveWorkflowExtension documentArchiveWorkflowExtension;

    /** 病案主档写侧。 */
    private final CaseRecordDomainService caseRecordDomainService;

    /** 病案主档读侧。 */
    private final CaseRecordQueryService caseRecordQueryService;

    /** 首页读侧（详情预览）。 */
    private final CaseRecordHomepageDomainService caseRecordHomepageDomainService;

    /** 首页映射。 */
    private final HomepageMapper homepageMapper;

    /** 就诊读侧（文书门诊/住院过滤）。 */
    private final EncounterQueryService encounterQueryService;

    /** JSON 序列化。 */
    private final ObjectMapper objectMapper;

    /**
     * 待归档审批分页（IF-MRMS-APR-01）。
     *
     * @param request 类别/关键字/分页
     * @return 分页行
     */
    public PagedResult<PendingArchiveListItemDto> pagePendingArchive(PagePendingArchiveRequest request) {
        // Gate：管理员 + 分页
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        // Pre/Cmd：按类别分查
        if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
            return pageHomepagePending(request);
        }
        return pageDocumentPending(request, category);
    }

    /**
     * 待审详情只读预览（IF-MRMS-APR-02）。
     *
     * @param request 类别 + bizId
     * @return 详情
     */
    public PendingArchiveDetailDto getPendingArchiveDetail(GetPendingArchiveDetailRequest request) {
        // Gate：管理员
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        Long bizId = requireBizId(request.getBizId());
        // Pre/Asm：按类别装载
        if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
            return detailHomepage(bizId);
        }
        return detailDocument(category, bizId);
    }

    /**
     * 同意归档（IF-MRMS-APR-03）。
     *
     * @param request 类别 + bizId
     */
    public void approveArchive(ApproveArchiveRequest request) {
        // Gate：管理员
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        Long bizId = requireBizId(request.getBizId());
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：按类别同意
        if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
            caseRecordDomainService.approveArchive(bizId, operatorId, trimToNull(request.getQualityGradeCode()));
            return;
        }
        approveDocument(bizId, operatorId);
    }

    /**
     * 驳回归档（IF-MRMS-APR-04）。
     *
     * @param request 须含驳回原因
     */
    public void rejectArchive(RejectArchiveRequest request) {
        // Gate：管理员 + 原因
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        String reason = trimToNull(request.getRejectReason());
        if (reason == null) {
            throw new ValidationException("请填写驳回原因");
        }
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        Long bizId = requireBizId(request.getBizId());
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：按类别驳回
        if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
            caseRecordDomainService.rejectArchive(bizId, reason, operatorId);
            return;
        }
        rejectDocument(bizId, reason, operatorId);
    }

    /**
     * 批量同意（IF-MRMS-APR-05）；任一份失败整批抛错由框架事务回滚。
     *
     * @param request 同类型 bizIds
     */
    public void approveArchiveBatch(ApproveArchiveBatchRequest request) {
        // Gate：管理员
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        List<Long> bizIds = requireBizIds(request.getBizIds());
        Long operatorId = operatorResolver.requireOperatorId();
        String grade = trimToNull(request.getQualityGradeCode());
        // Cmd：逐条同意
        for (Long bizId : bizIds) {
            if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
                caseRecordDomainService.approveArchive(bizId, operatorId, grade);
            } else {
                approveDocument(bizId, operatorId);
            }
        }
    }

    /**
     * 批量驳回（IF-MRMS-APR-05）。
     *
     * @param request 同类型 bizIds + 原因
     */
    public void rejectArchiveBatch(RejectArchiveBatchRequest request) {
        // Gate：管理员 + 原因
        mrmsAdminAuthorizer.assertMrmsAdminRole();
        String reason = trimToNull(request.getRejectReason());
        if (reason == null) {
            throw new ValidationException("请填写驳回原因");
        }
        ArchiveApprovalBizCategory category = requireCategory(request.getBizCategory());
        List<Long> bizIds = requireBizIds(request.getBizIds());
        Long operatorId = operatorResolver.requireOperatorId();
        // Cmd：逐条驳回
        for (Long bizId : bizIds) {
            if (category == ArchiveApprovalBizCategory.CASE_HOMEPAGE) {
                caseRecordDomainService.rejectArchive(bizId, reason, operatorId);
            } else {
                rejectDocument(bizId, reason, operatorId);
            }
        }
    }

    /**
     * 首页待审分页。
     *
     * @param request 请求
     * @return 分页
     */
    private PagedResult<PendingArchiveListItemDto> pageHomepagePending(PagePendingArchiveRequest request) {
        CaseRecordPageQuery query = new CaseRecordPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setKeyword(trimToNull(request.getKeyword()));
        PageRequest pageable = PageRequest.of(
                request.springPageIndex(),
                request.getBoundedPageSize(),
                Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<CaseRecordListItemQueryModel> page =
                caseRecordQueryService.pageForArchiveApproval(query, pageable);
        List<PendingArchiveListItemDto> rows = new ArrayList<>();
        for (CaseRecordListItemQueryModel model : page.getContent()) {
            PendingArchiveListItemDto row = new PendingArchiveListItemDto();
            row.setBizCategory(ArchiveApprovalBizCategory.CASE_HOMEPAGE.getCode());
            row.setBizId(model.getId());
            String title = model.getMedcasno();
            if (title == null || title.isBlank()) {
                title = model.getPsnName();
            }
            row.setTitle(title);
            row.setPatientName(model.getPsnName());
            if (model.getEncounterId() != null) {
                row.setEncounterId(String.valueOf(model.getEncounterId()));
            }
            row.setStatusCode(model.getStatusCode());
            rows.add(row);
        }
        return PagedResultBuilder.build(new PagedResult<>(), rows, page);
    }

    /**
     * 文书待审分页（按 AMB/IMP 过滤当前页）。
     *
     * @param request 请求
     * @param category 门诊或住院
     * @return 分页
     */
    private PagedResult<PendingArchiveListItemDto> pageDocumentPending(
            PagePendingArchiveRequest request, ArchiveApprovalBizCategory category) {
        // Gate：EMR 可用
        assertEmrArchiveEnabled();
        EmrDocumentArchivePageExtensionIn in = new EmrDocumentArchivePageExtensionIn();
        in.setTenantId(operatorResolver.requireTenantId());
        in.setPageIndex(request.getPageNo());
        in.setPageSize(request.getBoundedPageSize());
        EmrDocumentArchivePageExtensionOut page = documentArchiveWorkflowExtension.pagePendingArchive(in);
        String expectedClass = EncounterClass.AMB.getCode();
        if (category == ArchiveApprovalBizCategory.IP_DOCUMENT) {
            expectedClass = EncounterClass.IMP.getCode();
        }
        Set<Long> encounterIdSet = new HashSet<>();
        for (EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut item : page.getContent()) {
            Long encounterId = resolveEncounterIdFromRaw(item.getEncounterId());
            if (encounterId != null) {
                encounterIdSet.add(encounterId);
            }
        }
        Set<Long> matchedEncounterIds = filterEncounterIdsByClass(encounterIdSet, expectedClass);
        List<PendingArchiveListItemDto> rows = new ArrayList<>();
        String keyword = trimToNull(request.getKeyword());
        for (EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut item : page.getContent()) {
            Long encounterId = resolveEncounterIdFromRaw(item.getEncounterId());
            // 就诊类别不匹配则跳过
            if (encounterId == null || !matchedEncounterIds.contains(encounterId)) {
                continue;
            }
            // 关键字：标题模糊（可选）
            if (keyword != null) {
                String title = "";
                if (item.getTitle() != null) {
                    title = item.getTitle();
                }
                if (!title.toLowerCase().contains(keyword.toLowerCase())) {
                    continue;
                }
            }
            PendingArchiveListItemDto row = new PendingArchiveListItemDto();
            row.setBizCategory(category.getCode());
            row.setBizId(item.getDocumentId());
            row.setTitle(item.getTitle());
            row.setEncounterId(item.getEncounterId());
            row.setStatusCode(item.getStatusCode());
            row.setSubmittedAt(item.getArchiveSubmittedAt());
            row.setOrganizationId(item.getOrganizationId());
            row.setVersionNo(item.getVersionNo());
            rows.add(row);
        }
        long total = rows.size();
        if (page.getTotalCount() != null) {
            total = page.getTotalCount();
        }
        int pageNo = 1;
        if (request.getPageNo() != null) {
            pageNo = request.getPageNo();
        }
        return PagedResultBuilder.build(
                new PagedResult<>(),
                rows,
                total,
                pageNo,
                request.getBoundedPageSize());
    }

    /**
     * 首页详情。
     *
     * @param caseRecordId 病案
     * @return 详情
     */
    private PendingArchiveDetailDto detailHomepage(Long caseRecordId) {
        CaseRecord record = caseRecordDomainService.requireById(caseRecordId);
        CaseRecordHomepage homepage = caseRecordHomepageDomainService
                .findOptionalByCaseRecordId(caseRecordId)
                .orElse(null);
        PendingArchiveDetailDto dto = new PendingArchiveDetailDto();
        dto.setBizCategory(ArchiveApprovalBizCategory.CASE_HOMEPAGE.getCode());
        dto.setBizId(caseRecordId);
        dto.setTitle(record.getMedcasno());
        dto.setStatusCode(record.getStatusCode());
        if (homepage != null) {
            dto.setVersionNo(homepage.getVersionNo());
            try {
                dto.setHomepagePreviewJson(objectMapper.writeValueAsString(homepageMapper.toDto(
                        homepage,
                        caseRecordHomepageDomainService.listDiagnoses(caseRecordId),
                        caseRecordHomepageDomainService.listProcedures(caseRecordId),
                        record.getEncounterId())));
            } catch (Exception ex) {
                throw new BusinessException(MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "首页预览序列化失败");
            }
        }
        return dto;
    }

    /**
     * 文书详情。
     *
     * @param category 类别
     * @param documentId 文书
     * @return 详情
     */
    private PendingArchiveDetailDto detailDocument(ArchiveApprovalBizCategory category, Long documentId) {
        assertEmrArchiveEnabled();
        EmrDocumentArchiveDetailExtensionIn in = new EmrDocumentArchiveDetailExtensionIn();
        in.setDocumentId(documentId);
        EmrDocumentArchiveDetailExtensionOut detail = documentArchiveWorkflowExtension.getArchiveDetail(in);
        if (detail.getDocumentId() == null) {
            throw new ValidationException("文书不存在");
        }
        PendingArchiveDetailDto dto = new PendingArchiveDetailDto();
        dto.setBizCategory(category.getCode());
        dto.setBizId(detail.getDocumentId());
        dto.setTitle(detail.getTitle());
        dto.setStatusCode(detail.getStatusCode());
        dto.setVersionNo(detail.getVersionNo());
        dto.setDocumentContent(detail.getContent());
        dto.setRejectReason(detail.getArchiveRejectReason());
        return dto;
    }

    /**
     * 同意文书归档。
     *
     * @param documentId 文书
     * @param operatorId 审批人
     */
    private void approveDocument(Long documentId, Long operatorId) {
        assertEmrArchiveEnabled();
        EmrDocumentArchiveWriteExtensionIn in = new EmrDocumentArchiveWriteExtensionIn();
        in.setDocumentId(documentId);
        in.setOperatorId(operatorId);
        EmrDocumentArchiveWriteExtensionOut out = documentArchiveWorkflowExtension.approveArchive(in);
        if (!out.isSuccess()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, out.getMessage());
        }
    }

    /**
     * 驳回文书归档。
     *
     * @param documentId 文书
     * @param reason 原因
     * @param operatorId 审批人
     */
    private void rejectDocument(Long documentId, String reason, Long operatorId) {
        assertEmrArchiveEnabled();
        EmrDocumentArchiveWriteExtensionIn in = new EmrDocumentArchiveWriteExtensionIn();
        in.setDocumentId(documentId);
        in.setOperatorId(operatorId);
        in.setRejectReason(reason);
        EmrDocumentArchiveWriteExtensionOut out = documentArchiveWorkflowExtension.rejectArchive(in);
        if (!out.isSuccess()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, out.getMessage());
        }
    }

    /**
     * EMR 归档 SPI 可用门禁。
     */
    private void assertEmrArchiveEnabled() {
        if (!documentArchiveWorkflowExtension.isEnabled()) {
            throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_EMR_DISABLED, "电子病历未启用，无法审批文书归档");
        }
    }

    /**
     * 解析业务类别。
     *
     * @param code 契约码
     * @return 枚举
     */
    private ArchiveApprovalBizCategory requireCategory(String code) {
        ArchiveApprovalBizCategory category = ArchiveApprovalBizCategory.fromCode(code);
        if (category == null) {
            throw new ValidationException("业务类别非法，须为 op_document/ip_document/case_homepage");
        }
        return category;
    }

    /**
     * 业务主键门禁。
     *
     * @param bizId 主键
     * @return 主键
     */
    private Long requireBizId(Long bizId) {
        if (bizId == null) {
            throw new ValidationException("业务主键不能为空");
        }
        return bizId;
    }

    /**
     * 批量主键门禁。
     *
     * @param bizIds 列表
     * @return 非空列表
     */
    private List<Long> requireBizIds(List<Long> bizIds) {
        if (bizIds == null || bizIds.isEmpty()) {
            throw new ValidationException("业务主键列表不能为空");
        }
        return bizIds;
    }

    /**
     * 将文书侧字符串就诊主键解析为 Long（非枚举身份解析；非法格式返回 null）。
     *
     * @param raw 原始字符串
     * @return Long 或 null
     */
    private Long resolveEncounterIdFromRaw(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 按就诊 class 过滤主键集合。
     *
     * @param encounterIds 待过滤
     * @param expectedClass AMB/IMP
     * @return 匹配集合
     */
    private Set<Long> filterEncounterIdsByClass(Set<Long> encounterIds, String expectedClass) {
        Set<Long> matched = new HashSet<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return matched;
        }
        List<Encounter> encounters = encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        for (Encounter encounter : encounters) {
            if (expectedClass.equals(encounter.getClassEnum())) {
                matched.add(encounter.getId());
            }
        }
        return matched;
    }
}
