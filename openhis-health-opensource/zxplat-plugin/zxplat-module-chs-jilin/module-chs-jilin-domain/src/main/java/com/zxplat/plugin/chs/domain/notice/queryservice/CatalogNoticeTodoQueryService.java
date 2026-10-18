/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.queryservice;

import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeTodo;
import com.zxplat.plugin.chs.domain.notice.model.NoticePageResult;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoSummaryView;
import com.zxplat.plugin.chs.domain.notice.model.NoticeTodoView;
import com.zxplat.plugin.chs.domain.notice.model.PageNoticeTodoQuery;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeTodoRepository;
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
 * 催办待办只读查询（R-04）。
 * <p>仅本聚合 Repository（{@link YbCatalogNoticeTodoRepository}）。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeTodoQueryService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String TODO_STOP = "STOP";
    private static final String TODO_PAY_CLASS = "PAY_CLASS";

    private final YbCatalogNoticeTodoRepository todoRepository;

    /**
     * 首页 OPEN 待办汇总（STOP / PAY_CLASS 分计）。
     *
     * @param tenantId 租户；空则返回零计数
     * @return 汇总视图
     */
    public NoticeTodoSummaryView summarizeTodos(Long tenantId) {
        NoticeTodoSummaryView summary = new NoticeTodoSummaryView();
        if (tenantId == null) {
            return summary;
        }
        summary.setStopOpenCount(todoRepository.countByTenantIdAndTodoTypeAndStatusAndIsDeletedFalse(
            tenantId, TODO_STOP, STATUS_OPEN));
        summary.setPayClassOpenCount(todoRepository.countByTenantIdAndTodoTypeAndStatusAndIsDeletedFalse(
            tenantId, TODO_PAY_CLASS, STATUS_OPEN));
        return summary;
    }

    /**
     * 待办分页；status 空时默认 OPEN。
     *
     * @param query 分页与过滤；tenantId 必填
     * @return total + records
     */
    public NoticePageResult<NoticeTodoView> pageTodos(PageNoticeTodoQuery query) {
        if (query == null || query.getTenantId() == null) {
            return NoticePageResult.of(0L, List.of());
        }
        if (query.getStatus() == null || query.getStatus().isBlank()) {
            query.setStatus(STATUS_OPEN);
        }
        PageRequest pageable = PageRequest.of(
            query.springPageIndex(),
            query.boundedPageSize(),
            Sort.by(Sort.Direction.DESC, "effectiveDate", "id"));
        Page<YbCatalogNoticeTodo> page = todoRepository.findAll(buildSpec(query), pageable);
        List<NoticeTodoView> records = new ArrayList<>(page.getContent().size());
        for (YbCatalogNoticeTodo todo : page.getContent()) {
            records.add(toView(todo));
        }
        return NoticePageResult.of(page.getTotalElements(), records);
    }

    private Specification<YbCatalogNoticeTodo> buildSpec(PageNoticeTodoQuery query) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), query.getTenantId()));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            if (query.getStatus() != null && !query.getStatus().isBlank()) {
                predicates.add(cb.equal(root.get("status"), query.getStatus()));
            }
            if (query.getTodoType() != null && !query.getTodoType().isBlank()) {
                predicates.add(cb.equal(root.get("todoType"), query.getTodoType()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static NoticeTodoView toView(YbCatalogNoticeTodo todo) {
        NoticeTodoView view = new NoticeTodoView();
        view.setId(todo.getId());
        view.setTodoType(todo.getTodoType());
        view.setHilistCode(todo.getHilistCode());
        view.setProjectCode(todo.getProjectCode());
        view.setProductName(todo.getProductName());
        view.setEffectiveDate(todo.getEffectiveDate());
        view.setDocId(todo.getDocId());
        view.setStatus(todo.getStatus());
        return view;
    }
}
