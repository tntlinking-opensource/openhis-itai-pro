/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeLine;
import com.zxplat.plugin.chs.domain.notice.model.NoticeLineView;
import com.zxplat.plugin.chs.domain.notice.model.NoticePageResult;
import com.zxplat.plugin.chs.domain.notice.model.PageNoticeLineQuery;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeLineRepository;
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
 * 通知明细行只读查询；仅注入本聚合 {@link YbCatalogNoticeLineRepository}。
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeLineQueryService {

    private final YbCatalogNoticeLineRepository lineRepository;

    /**
     * 按文件头分页过滤明细（R-02）。
     *
     * @param query 含 docId 与分页过滤
     * @return total + records
     */
    public NoticePageResult<NoticeLineView> pageLines(PageNoticeLineQuery query) {
        if (query == null || query.getTenantId() == null || query.getDocId() == null) {
            return NoticePageResult.of(0L, List.of());
        }
        PageRequest pageable = PageRequest.of(
            query.springPageIndex(),
            query.boundedPageSize(),
            Sort.by(Sort.Direction.ASC, "rowNo", "id"));
        Page<YbCatalogNoticeLine> page = lineRepository.findAll(buildSpec(query), pageable);
        List<NoticeLineView> records = new ArrayList<>(page.getContent().size());
        for (YbCatalogNoticeLine line : page.getContent()) {
            records.add(toView(line));
        }
        return NoticePageResult.of(page.getTotalElements(), records);
    }

    private Specification<YbCatalogNoticeLine> buildSpec(PageNoticeLineQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            predicates.add(cb.equal(root.get("docId"), query.getDocId()));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (query.getAction() != null && !query.getAction().isBlank()) {
                predicates.add(cb.equal(root.get("action"), query.getAction()));
            }
            if (query.getMatchStatus() != null && !query.getMatchStatus().isBlank()) {
                predicates.add(cb.equal(root.get("matchStatus"), query.getMatchStatus()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private NoticeLineView toView(YbCatalogNoticeLine line) {
        NoticeLineView view = new NoticeLineView();
        view.setId(line.getId());
        view.setDocId(line.getDocId());
        view.setAction(line.getAction());
        view.setHilistCode(line.getHilistCode());
        view.setProjectCode(line.getProjectCode());
        view.setHilistName(line.getHilistName());
        view.setBeginDate(line.getBeginDate());
        view.setEndDate(line.getEndDate());
        view.setChrgitmLv(line.getChrgitmLv());
        view.setPriceLimitAmt(line.getPriceLimitAmt());
        view.setSheetName(line.getSheetName());
        view.setRowNo(line.getRowNo());
        view.setWarning(line.getWarningMessage());
        view.setMatchStatus(line.getMatchStatus());
        view.setApplyResult(line.getApplyResult());
        return view;
    }
}
