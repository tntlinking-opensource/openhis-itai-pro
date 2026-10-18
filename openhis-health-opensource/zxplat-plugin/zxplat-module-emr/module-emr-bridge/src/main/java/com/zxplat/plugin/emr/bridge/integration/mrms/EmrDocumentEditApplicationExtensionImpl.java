/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.bridge.integration.mrms;

import com.zxplat.domain.infrastructure.integration.emr.EmrDocumentEditApplicationExtension;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditCreateExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditIdExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditPageExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditRejectExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionIn.EmrDocumentEditSaveDraftExtensionIn;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditApplicationExtensionOut;
import com.zxplat.domain.infrastructure.integration.emr.ExtensionOut.EmrDocumentEditPageExtensionOut;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.plugin.emr.domain.documentedit.entity.DocumentEditApplication;
import com.zxplat.plugin.emr.domain.documentedit.queries.DocumentEditApplicationPageQuery;
import com.zxplat.plugin.emr.domain.documentedit.queries.model.DocumentEditApplicationQueryModel;
import com.zxplat.plugin.emr.domain.documentedit.service.DocumentEditApplicationDomainService;
import com.zxplat.plugin.emr.domain.documentedit.service.DocumentEditApplicationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EMR 文书编辑申请 Extension 实现（新增；不改既有文书 lock 契约）。
 *
 * <p>委托 {@link DocumentEditApplicationDomainService} / {@link DocumentEditApplicationQueryService}；
 * 供 health 侧 {@code MrmsRecordEditFacadeAppService} document 分支调用。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.zxplat.plugin.emr.domain.documentedit.entity.DocumentEditApplication")
@ConditionalOnProperty(name = "zxplat.modules.emr.enabled", havingValue = "true", matchIfMissing = true)
public class EmrDocumentEditApplicationExtensionImpl implements EmrDocumentEditApplicationExtension {

    /** 文书编辑申请写侧。 */
    private final DocumentEditApplicationDomainService documentEditApplicationDomainService;

    /** 文书编辑申请读侧。 */
    private final DocumentEditApplicationQueryService documentEditApplicationQueryService;

    /**
     * 本实现在 EMR 模块启用时恒为 true。
     *
     * @return true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 创建文书编辑申请（须文书已 ARCHIVED）。
     *
     * @param command 文书锚点 + 原因 + 申请人
     * @return 申请 Out（含双 JSON）
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut create(EmrDocumentEditCreateExtensionIn command) {
        // Gate：文书主键
        if (command == null || command.getDocumentId() == null) {
            throw new ValidationException("文书主键不能为空");
        }
        // Cmd：领域创建
        DocumentEditApplication entity = documentEditApplicationDomainService.create(
                command.getTenantId(),
                command.getDocumentId(),
                command.getEncounterClassCode(),
                command.getApplyReason(),
                command.getApplicantId(),
                command.getOperatorId());
        // Asm：实体 → Out
        return toOut(entity, true);
    }

    /**
     * 暂存工作稿 new_content_json。
     *
     * @param command 申请 id + 工作稿 + 操作人
     * @return 申请 Out（含双 JSON）
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut saveDraft(EmrDocumentEditSaveDraftExtensionIn command) {
        // Gate：申请主键
        Long applicationId = null;
        if (command != null) {
            applicationId = command.getApplicationId();
        }
        requireApplicationId(applicationId);
        // Cmd：领域暂存
        DocumentEditApplication entity = documentEditApplicationDomainService.saveDraft(
                command.getApplicationId(), command.getNewContentJson(), command.getOperatorId());
        return toOut(entity, true);
    }

    /**
     * 交审：DRAFTING → PENDING_REVIEW。
     *
     * @param command 申请 id + 操作人
     * @return 申请 Out
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut submitForReview(EmrDocumentEditIdExtensionIn command) {
        Long applicationId = null;
        if (command != null) {
            applicationId = command.getApplicationId();
        }
        requireApplicationId(applicationId);
        DocumentEditApplication entity = documentEditApplicationDomainService.submitForReview(
                command.getApplicationId(), command.getOperatorId());
        return toOut(entity, true);
    }

    /**
     * 同意：合并工作稿到文书并归档态保持。
     *
     * @param command 申请 id + 审核人
     * @return 申请 Out
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut approve(EmrDocumentEditIdExtensionIn command) {
        Long applicationId = null;
        if (command != null) {
            applicationId = command.getApplicationId();
        }
        requireApplicationId(applicationId);
        DocumentEditApplication entity = documentEditApplicationDomainService.approve(
                command.getApplicationId(), command.getOperatorId());
        return toOut(entity, true);
    }

    /**
     * 驳回：pending_review → rejected，保留工作稿。
     *
     * @param command 申请 id + 原因 + 审核人
     * @return 申请 Out
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut reject(EmrDocumentEditRejectExtensionIn command) {
        Long applicationId = null;
        if (command != null) {
            applicationId = command.getApplicationId();
        }
        requireApplicationId(applicationId);
        DocumentEditApplication entity = documentEditApplicationDomainService.reject(
                command.getApplicationId(), command.getRejectReason(), command.getOperatorId());
        return toOut(entity, true);
    }

    /**
     * 按主键打开申请（含双 JSON）。
     *
     * @param query 申请 id
     * @return 申请 Out；不存在时空壳
     */
    @Override
    public EmrDocumentEditApplicationExtensionOut getById(EmrDocumentEditIdExtensionIn query) {
        Long applicationId = null;
        if (query != null) {
            applicationId = query.getApplicationId();
        }
        requireApplicationId(applicationId);
        return documentEditApplicationQueryService.getById(query.getApplicationId())
                .map(entity -> toOut(entity, true))
                .orElseGet(EmrDocumentEditApplicationExtensionOut::new);
    }

    /**
     * 审核列表分页。
     *
     * @param query 状态/文书/就诊类别/分页
     * @return 分页 Out（行无双 JSON）
     */
    @Override
    public EmrDocumentEditPageExtensionOut pageForReview(EmrDocumentEditPageExtensionIn query) {
        // Pre：Extension In → 领域 PageQuery
        DocumentEditApplicationPageQuery pageQuery = new DocumentEditApplicationPageQuery();
        if (query != null) {
            pageQuery.setTenantId(query.getTenantId());
            pageQuery.setStatusEnum(query.getStatusEnum());
            pageQuery.setDocumentId(query.getDocumentId());
            pageQuery.setEncounterClassCode(query.getEncounterClassCode());
            pageQuery.setPageIndex(query.getPageIndex());
            pageQuery.setPageSize(query.getPageSize());
        }
        // Cmd：读侧分页
        Page<DocumentEditApplicationQueryModel> page = documentEditApplicationQueryService.pageForReview(pageQuery);
        // Asm：QueryModel → Out 行
        EmrDocumentEditPageExtensionOut out = new EmrDocumentEditPageExtensionOut();
        out.setTotalCount(page.getTotalElements());
        List<EmrDocumentEditApplicationExtensionOut> rows = new ArrayList<>();
        for (DocumentEditApplicationQueryModel model : page.getContent()) {
            rows.add(toOut(model));
        }
        out.setContent(rows);
        return out;
    }

    /**
     * 按文书列编辑历史（不含双 JSON）。
     *
     * @param documentId 文书主键
     * @return 历史列表
     */
    @Override
    public List<EmrDocumentEditApplicationExtensionOut> listHistoryByDocument(Long documentId) {
        List<DocumentEditApplication> history =
                documentEditApplicationQueryService.listHistoryByDocument(documentId);
        List<EmrDocumentEditApplicationExtensionOut> rows = new ArrayList<>();
        for (DocumentEditApplication entity : history) {
            // 历史列表不带双 JSON，避免膨胀
            rows.add(toOut(entity, false));
        }
        return rows;
    }

    /**
     * 申请主键门禁。
     *
     * @param applicationId 申请 id
     */
    private void requireApplicationId(Long applicationId) {
        if (applicationId == null) {
            throw new ValidationException("编辑申请主键不能为空");
        }
    }

    /**
     * 实体 → Extension Out。
     *
     * @param entity 领域实体
     * @param includeJson 是否带双 JSON
     * @return Out
     */
    private EmrDocumentEditApplicationExtensionOut toOut(DocumentEditApplication entity, boolean includeJson) {
        EmrDocumentEditApplicationExtensionOut out = new EmrDocumentEditApplicationExtensionOut();
        out.setApplicationId(entity.getId());
        out.setDocumentId(entity.getDocumentId());
        out.setEncounterId(entity.getEncounterId());
        out.setOrganizationId(entity.getOrganizationId());
        out.setEncounterClassCode(entity.getEncounterClassCode());
        out.setStatusEnum(entity.getStatusEnum());
        out.setApplyReason(entity.getApplyReason());
        out.setRejectReason(entity.getRejectReason());
        out.setFromVersion(entity.getFromVersion());
        out.setToVersion(entity.getToVersion());
        out.setApplicantId(entity.getApplicantId());
        out.setAppliedAt(entity.getAppliedAt());
        out.setSubmittedAt(entity.getSubmittedAt());
        out.setReviewerId(entity.getReviewerId());
        out.setReviewedAt(entity.getReviewedAt());
        if (includeJson) {
            out.setOldContentJson(entity.getOldContentJson());
            out.setNewContentJson(entity.getNewContentJson());
        }
        return out;
    }

    /**
     * QueryModel → Extension Out（列表无 JSON）。
     *
     * @param model 读模型
     * @return Out
     */
    private EmrDocumentEditApplicationExtensionOut toOut(DocumentEditApplicationQueryModel model) {
        EmrDocumentEditApplicationExtensionOut out = new EmrDocumentEditApplicationExtensionOut();
        out.setApplicationId(model.getId());
        out.setDocumentId(model.getDocumentId());
        out.setEncounterId(model.getEncounterId());
        out.setOrganizationId(model.getOrganizationId());
        out.setEncounterClassCode(model.getEncounterClassCode());
        out.setStatusEnum(model.getStatusEnum());
        out.setApplyReason(model.getApplyReason());
        out.setRejectReason(model.getRejectReason());
        out.setFromVersion(model.getFromVersion());
        out.setToVersion(model.getToVersion());
        out.setApplicantId(model.getApplicantId());
        out.setAppliedAt(model.getAppliedAt());
        out.setSubmittedAt(model.getSubmittedAt());
        out.setReviewerId(model.getReviewerId());
        out.setReviewedAt(model.getReviewedAt());
        return out;
    }
}
