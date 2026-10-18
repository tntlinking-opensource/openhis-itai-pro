/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.service;

import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeDocCommand;
import com.zxplat.plugin.chs.domain.notice.command.PersistNoticeLineCommand;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeDoc;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeLine;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.port.CatalogNoticePackStorePort;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeDocRepository;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeLineRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 医保文件通知导入写服务。
 * <p>不变量：同租户同 hash 默认拒绝；force 时软删旧 doc 后生成新版本；落库顺序 PackStore → Doc → Lines。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeImportDomainService {

    private static final int LINE_BATCH_SIZE = 500;

    private final YbCatalogNoticeDocRepository docRepository;
    private final YbCatalogNoticeLineRepository lineRepository;
    private final CatalogNoticePackStorePort packStorePort;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 持久化已解析的标准落地包、文件头与明细（W-02）。
     *
     * @param command 含 hash、pack 字节与行列表
     * @return 入库后的文件头视图
     */
    @Transactional(timeout = 600)
    public NoticeDocView persistImportedDoc(PersistNoticeDocCommand command) {
        // 前置校验：命令与 hash/包字节齐全，否则无法保证幂等与可回下
        if (command == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "入库命令不能为空");
        }
        if (command.getTenantId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "租户不能为空");
        }
        if (command.getSourceFileHash() == null || command.getSourceFileHash().isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARSE_FAILED, "文件 hash 不能为空");
        }
        if (command.getPackBytes() == null || command.getPackBytes().length == 0) {
            throw new BusinessException(ChsErrorCodes.NOTICE_STORE_FAILED, "标准包字节为空，无法存储");
        }

        Optional<YbCatalogNoticeDoc> existing = docRepository.findByTenantIdAndSourceFileHashAndIsDeletedFalse(
            command.getTenantId(), command.getSourceFileHash());
        // 相同内容已入库且未强制重导：拒绝产生双份权威文件
        if (existing.isPresent() && !command.isForce()) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_DUPLICATE_HASH,
                "相同内容已入库，docId=" + existing.get().getId());
        }
        // 强制重导：软删旧文档，释放 (tenant, hash) 唯一键，生成新版本 doc
        if (existing.isPresent() && command.isForce()) {
            YbCatalogNoticeDoc old = existing.get();
            old.setIsDeleted(true);
            old.setDeletedAt(LocalDateTime.now());
            docRepository.save(old);
        }

        // 加载/组装聚合：先落标准包，保证库内 URI 可回读
        String packUri;
        try {
            String fileName = command.getPackFileName();
            if (fileName == null || fileName.isBlank()) {
                fileName = command.getSourceFileHash() + ".zip";
            }
            packUri = packStorePort.storeCanonicalPack(
                command.getTenantId(),
                command.getSourceFileHash(),
                fileName,
                command.getPackBytes());
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(
                ChsErrorCodes.NOTICE_STORE_FAILED,
                "标准落地包存储失败：" + ex.getMessage(),
                ex);
        }
        if (packUri == null || packUri.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_STORE_FAILED, "标准落地包存储返回空 URI");
        }

        // 持久化：文件头（字段截断，避免超 VARCHAR 整事务回滚）
        LocalDateTime now = LocalDateTime.now();
        YbCatalogNoticeDoc doc = new YbCatalogNoticeDoc();
        doc.setTenantId(command.getTenantId());
        doc.setContractId(command.getContractId());
        doc.setDocKind(truncate(command.getDocKind(), 32));
        doc.setCategory(truncate(command.getCategory(), 32));
        doc.setTitle(truncate(command.getTitle(), 256));
        doc.setSourceFilename(truncate(command.getSourceFilename(), 512));
        doc.setSourceFileHash(truncate(command.getSourceFileHash(), 64));
        doc.setCanonicalPackUri(truncate(packUri, 1024));
        doc.setNoticeDate(command.getNoticeDate());
        doc.setEffectiveDate(command.getEffectiveDate());
        String parseStatus = command.getParseStatus();
        if (parseStatus == null || parseStatus.isBlank()) {
            parseStatus = "PARSED";
        }
        doc.setParseStatus(truncate(parseStatus, 32));
        doc.setApplyStatus("NOT_APPLIED");
        doc.setSheetSummaryJson(command.getSheetSummaryJson());
        doc.setImportedBy(command.getImportedBy());
        doc.setImportedAt(now);
        int lineCount = 0;
        if (command.getLines() != null) {
            lineCount = command.getLines().size();
        }
        doc.setLineCount(lineCount);
        doc.setIsDeleted(false);
        YbCatalogNoticeDoc savedDoc = docRepository.save(doc);
        Long docId = savedDoc.getId();

        // 持久化：明细行（分批 flush/clear，支撑数千行大包）
        if (command.getLines() != null && !command.getLines().isEmpty()) {
            List<YbCatalogNoticeLine> batch = new ArrayList<>(LINE_BATCH_SIZE);
            for (PersistNoticeLineCommand lineCmd : command.getLines()) {
                YbCatalogNoticeLine line = new YbCatalogNoticeLine();
                line.setTenantId(command.getTenantId());
                line.setDocId(docId);
                line.setAction(truncate(lineCmd.getAction(), 32));
                line.setListType(truncate(lineCmd.getListType(), 30));
                line.setHilistCode(truncate(lineCmd.getHilistCode(), 64));
                line.setProjectCode(truncate(lineCmd.getProjectCode(), 64));
                line.setHilistName(truncate(lineCmd.getHilistName(), 512));
                line.setBeginDate(lineCmd.getBeginDate());
                line.setEndDate(lineCmd.getEndDate());
                line.setChangeFields(truncate(lineCmd.getChangeFields(), 512));
                line.setChrgitmLv(truncate(lineCmd.getChrgitmLv(), 8));
                line.setLmtUsedFlag(truncate(lineCmd.getLmtUsedFlag(), 8));
                line.setLmtScope(truncate(lineCmd.getLmtScope(), 512));
                line.setPriceLimitAmt(lineCmd.getPriceLimitAmt());
                line.setOverlmtDspoWay(truncate(lineCmd.getOverlmtDspoWay(), 8));
                line.setDualChannelFlag(truncate(lineCmd.getDualChannelFlag(), 8));
                line.setPayloadJson(lineCmd.getPayloadJson());
                line.setSheetName(truncate(lineCmd.getSheetName(), 128));
                line.setRowNo(lineCmd.getRowNo());
                line.setWarningMessage(truncate(lineCmd.getWarningMessage(), 1024));
                line.setLineStatus("ACTIVE");
                line.setIsDeleted(false);
                batch.add(line);
                if (batch.size() >= LINE_BATCH_SIZE) {
                    flushLineBatch(batch);
                }
            }
            if (!batch.isEmpty()) {
                flushLineBatch(batch);
            }
        }

        return toView(savedDoc);
    }

    private void flushLineBatch(List<YbCatalogNoticeLine> batch) {
        lineRepository.saveAll(batch);
        entityManager.flush();
        entityManager.clear();
        batch.clear();
    }

    private static String truncate(String value, int maxLen) {
        if (value == null || value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private NoticeDocView toView(YbCatalogNoticeDoc doc) {
        NoticeDocView view = new NoticeDocView();
        view.setId(doc.getId());
        view.setContractId(doc.getContractId());
        view.setDocKind(doc.getDocKind());
        view.setCategory(doc.getCategory());
        view.setTitle(doc.getTitle());
        view.setSourceFilename(doc.getSourceFilename());
        view.setSourceFileHash(doc.getSourceFileHash());
        view.setCanonicalPackUri(doc.getCanonicalPackUri());
        view.setNoticeDate(doc.getNoticeDate());
        view.setEffectiveDate(doc.getEffectiveDate());
        view.setParseStatus(doc.getParseStatus());
        view.setApplyStatus(doc.getApplyStatus());
        view.setLineCount(doc.getLineCount());
        view.setImportedAt(doc.getImportedAt());
        return view;
    }
}
