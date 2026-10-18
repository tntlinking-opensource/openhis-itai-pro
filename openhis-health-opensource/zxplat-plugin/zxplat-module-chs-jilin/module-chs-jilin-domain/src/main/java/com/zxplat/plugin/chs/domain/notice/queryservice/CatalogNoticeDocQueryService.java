/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeDoc;
import com.zxplat.plugin.chs.domain.notice.model.NoticeDocView;
import com.zxplat.plugin.chs.domain.notice.model.NoticePageResult;
import com.zxplat.plugin.chs.domain.notice.model.PageNoticeDocQuery;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeDocRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知文件头只读查询；仅注入本聚合 {@link YbCatalogNoticeDocRepository}。
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeDocQueryService {

    private final YbCatalogNoticeDocRepository docRepository;

    /**
     * 分页过滤文件头列表（R-01）。
     *
     * @param query 过滤与分页
     * @return total + records
     */
    public NoticePageResult<NoticeDocView> pageDocs(PageNoticeDocQuery query) {
        if (query == null || query.getTenantId() == null) {
            return NoticePageResult.of(0L, List.of());
        }
        PageRequest pageable = PageRequest.of(
            query.springPageIndex(),
            query.boundedPageSize(),
            Sort.by(Sort.Direction.DESC, "importedAt", "id"));
        Page<YbCatalogNoticeDoc> page = docRepository.findAll(buildSpec(query), pageable);
        List<NoticeDocView> records = new ArrayList<>(page.getContent().size());
        for (YbCatalogNoticeDoc doc : page.getContent()) {
            records.add(toView(doc));
        }
        return NoticePageResult.of(page.getTotalElements(), records);
    }

    /**
     * 按主键取文件头；不存在抛 {@link ChsErrorCodes#NOTICE_DOC_NOT_FOUND}。
     *
     * @param docId    文件头主键
     * @param tenantId 租户
     * @return 文件头视图
     */
    public NoticeDocView getDocById(Long docId, Long tenantId) {
        if (docId == null || tenantId == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_DOC_NOT_FOUND, "文件不存在");
        }
        YbCatalogNoticeDoc doc = docRepository.findByIdAndTenantIdAndIsDeletedFalse(docId, tenantId)
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.NOTICE_DOC_NOT_FOUND,
                "文件不存在，docId=" + docId));
        return toView(doc);
    }

    private Specification<YbCatalogNoticeDoc> buildSpec(PageNoticeDocQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (query.getDocKind() != null && !query.getDocKind().isBlank()) {
                predicates.add(cb.equal(root.get("docKind"), query.getDocKind()));
            }
            if (query.getCategory() != null && !query.getCategory().isBlank()) {
                predicates.add(cb.equal(root.get("category"), query.getCategory()));
            }
            if (query.getApplyStatus() != null && !query.getApplyStatus().isBlank()) {
                predicates.add(cb.equal(root.get("applyStatus"), query.getApplyStatus()));
            }
            if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
                String pattern = "%" + query.getKeyword().trim() + "%";
                predicates.add(cb.or(
                    cb.like(root.get("title"), pattern),
                    cb.like(root.get("sourceFilename"), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
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
