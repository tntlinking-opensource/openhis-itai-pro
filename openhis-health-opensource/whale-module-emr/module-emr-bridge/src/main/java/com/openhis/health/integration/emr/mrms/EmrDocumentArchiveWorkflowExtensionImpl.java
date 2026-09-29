/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.integration.emr.mrms;

import com.openhis.health.infrastructure.integration.emr.EmrDocumentArchiveWorkflowExtension;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveBatchExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveDetailExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchivePageExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionIn.EmrDocumentArchiveWriteExtensionIn;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveDetailExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchivePageExtensionOut;
import com.openhis.health.infrastructure.integration.emr.ExtensionOut.EmrDocumentArchiveWriteExtensionOut;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.emr.domain.document.entity.Document;
import com.openhis.whale.emr.domain.document.queries.DocumentArchiveApprovalPageQuery;
import com.openhis.whale.emr.domain.document.service.DocumentManager;
import com.openhis.whale.emr.domain.document.service.DocumentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * EMR 文书归档三态工作流 Extension 实现（新增；不改 lockAll/unlockAll）。
 *
 * <p>委托 {@link DocumentManager} 写路径与 {@link DocumentQueryService} 待审读路径；
 * 供 health 侧 {@code MrmsRecordArchiveApprovalAppService} 调用。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.document.entity.Document")
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
public class EmrDocumentArchiveWorkflowExtensionImpl implements EmrDocumentArchiveWorkflowExtension {

    /** 文书写侧（提交/同意/驳回归档）。 */
    private final DocumentManager documentManager;

    /** 文书读侧（待审分页与详情）。 */
    private final DocumentQueryService documentQueryService;

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
     * 单份文书提交归档：DRAFT → PENDING_ARCHIVE。
     *
     * @param command 文书主键 + 操作人
     * @return 写结果
     */
    @Override
    public EmrDocumentArchiveWriteExtensionOut submitArchive(EmrDocumentArchiveWriteExtensionIn command) {
        // Gate：文书与操作人必填
        requireWriteCommand(command);
        Document doc = documentManager.submitArchive(command.getDocumentId(), command.getOperatorId());
        return toWriteOut(doc);
    }

    /**
     * 批量提交归档；返回批内最后一份状态作摘要。
     *
     * @param command 文书 id 列表 + 操作人
     * @return 写结果摘要
     */
    @Override
    public EmrDocumentArchiveWriteExtensionOut submitArchiveBatch(EmrDocumentArchiveBatchExtensionIn command) {
        // Gate：批量列表与操作人
        if (command == null || command.getDocumentIds() == null || command.getDocumentIds().isEmpty()) {
            throw new ValidationException("提交归档文书列表不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new ValidationException("操作人不能为空");
        }
        List<Document> docs = documentManager.submitArchiveBatch(command.getDocumentIds(), command.getOperatorId());
        // Asm：返回最后一份状态作为批结果摘要
        if (docs.isEmpty()) {
            EmrDocumentArchiveWriteExtensionOut empty = new EmrDocumentArchiveWriteExtensionOut();
            empty.setSuccess(false);
            empty.setMessage("无文书被提交");
            return empty;
        }
        return toWriteOut(docs.get(docs.size() - 1));
    }

    /**
     * 同意归档：PENDING_ARCHIVE → ARCHIVED。
     *
     * @param command 文书主键 + 审批人
     * @return 写结果
     */
    @Override
    public EmrDocumentArchiveWriteExtensionOut approveArchive(EmrDocumentArchiveWriteExtensionIn command) {
        // Gate：文书与操作人必填
        requireWriteCommand(command);
        Document doc = documentManager.approveArchive(command.getDocumentId(), command.getOperatorId());
        return toWriteOut(doc);
    }

    /**
     * 驳回归档：PENDING_ARCHIVE → DRAFT，并写驳回原因。
     *
     * @param command 文书主键 + 原因 + 审批人
     * @return 写结果
     */
    @Override
    public EmrDocumentArchiveWriteExtensionOut rejectArchive(EmrDocumentArchiveWriteExtensionIn command) {
        // Gate：文书与操作人必填
        requireWriteCommand(command);
        Document doc = documentManager.rejectArchive(
                command.getDocumentId(), command.getRejectReason(), command.getOperatorId());
        return toWriteOut(doc);
    }

    /**
     * 待归档审批分页（status=PENDING_ARCHIVE）。
     *
     * @param query 租户/就诊/科室/分页
     * @return 分页行
     */
    @Override
    public EmrDocumentArchivePageExtensionOut pagePendingArchive(EmrDocumentArchivePageExtensionIn query) {
        // Pre：Extension In → 领域 PageQuery
        DocumentArchiveApprovalPageQuery pageQuery = new DocumentArchiveApprovalPageQuery();
        if (query != null) {
            pageQuery.setTenantId(query.getTenantId());
            pageQuery.setEncounterIds(query.getEncounterIds());
            pageQuery.setOrganizationId(query.getOrganizationId());
            pageQuery.setPageIndex(query.getPageIndex());
            pageQuery.setPageSize(query.getPageSize());
        }
        // Cmd：本聚合读侧分页
        Page<Document> page = documentQueryService.pageForArchiveApproval(pageQuery);
        // Asm：Document → Extension 行
        EmrDocumentArchivePageExtensionOut out = new EmrDocumentArchivePageExtensionOut();
        out.setTotalCount(page.getTotalElements());
        List<EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut> rows = new ArrayList<>();
        for (Document doc : page.getContent()) {
            rows.add(toItem(doc));
        }
        out.setContent(rows);
        return out;
    }

    /**
     * 待审文书详情（含正文与驳回原因）。
     *
     * @param query 文书主键
     * @return 详情；文书不存在时返回空壳
     */
    @Override
    public EmrDocumentArchiveDetailExtensionOut getArchiveDetail(EmrDocumentArchiveDetailExtensionIn query) {
        EmrDocumentArchiveDetailExtensionOut out = new EmrDocumentArchiveDetailExtensionOut();
        // Gate：主键缺失则空壳
        if (query == null || query.getDocumentId() == null) {
            return out;
        }
        Document doc = documentQueryService.getById(query.getDocumentId()).orElse(null);
        if (doc == null) {
            return out;
        }
        // Asm：实体 → Extension Out
        out.setDocumentId(doc.getId());
        out.setPatientId(doc.getPatientId());
        out.setEncounterId(doc.getEncounterId());
        out.setTitle(doc.getTitle());
        out.setDocumentTypeCode(doc.getDocumentTypeCode());
        out.setStatusCode(doc.getStatus());
        out.setOrganizationId(doc.getOrganizationId());
        out.setVersionNo(doc.getVersionNo());
        out.setContent(doc.getContent());
        out.setArchiveSubmittedAt(doc.getArchiveSubmittedAt());
        out.setArchiveRejectReason(doc.getArchiveRejectReason());
        return out;
    }

    /**
     * 写命令门禁：文书主键与操作人必填。
     *
     * @param command 写入参
     */
    private void requireWriteCommand(EmrDocumentArchiveWriteExtensionIn command) {
        if (command == null || command.getDocumentId() == null) {
            throw new ValidationException("文书主键不能为空");
        }
        if (command.getOperatorId() == null) {
            throw new ValidationException("操作人不能为空");
        }
    }

    /**
     * 文书实体 → 写出参。
     *
     * @param doc 已持久化文书
     * @return 成功写出参
     */
    private EmrDocumentArchiveWriteExtensionOut toWriteOut(Document doc) {
        EmrDocumentArchiveWriteExtensionOut out = new EmrDocumentArchiveWriteExtensionOut();
        out.setSuccess(true);
        out.setDocumentId(doc.getId());
        out.setStatusCode(doc.getStatus());
        out.setVersionNo(doc.getVersionNo());
        return out;
    }

    /**
     * 文书实体 → 待审列表行。
     *
     * @param doc 文书
     * @return 列表行
     */
    private EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut toItem(Document doc) {
        EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut row =
                new EmrDocumentArchivePageExtensionOut.EmrDocumentArchiveItemExtensionOut();
        row.setDocumentId(doc.getId());
        row.setPatientId(doc.getPatientId());
        row.setEncounterId(doc.getEncounterId());
        row.setTitle(doc.getTitle());
        row.setDocumentTypeCode(doc.getDocumentTypeCode());
        row.setStatusCode(doc.getStatus());
        row.setOrganizationId(doc.getOrganizationId());
        row.setVersionNo(doc.getVersionNo());
        row.setArchiveSubmittedAt(doc.getArchiveSubmittedAt());
        row.setArchiveSubmittedBy(doc.getArchiveSubmittedBy());
        return row;
    }
}
