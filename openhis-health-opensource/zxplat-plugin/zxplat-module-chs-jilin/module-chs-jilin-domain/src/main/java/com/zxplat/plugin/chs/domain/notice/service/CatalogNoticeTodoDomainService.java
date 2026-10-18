/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.notice.service;

import com.zxplat.plugin.chs.domain.notice.command.CloseNoticeTodoCommand;
import com.zxplat.plugin.chs.domain.notice.entity.YbCatalogNoticeTodo;
import com.zxplat.plugin.chs.domain.notice.model.CloseNoticeTodoResult;
import com.zxplat.plugin.chs.domain.notice.repository.YbCatalogNoticeTodoRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsErrorCodes;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 医保文件催办待办写服务（W-04 / 合规重算 upsert）。
 * <p>聚合边界：仅写本聚合 {@code yb_catalog_notice_todo}；关闭不解除运行时硬阻断（BR-06）。</p>
 */
@Service
@RequiredArgsConstructor
public class CatalogNoticeTodoDomainService {

    private static final String STATUS_OPEN = "OPEN";
    private static final String STATUS_CLOSED = "CLOSED";

    private final YbCatalogNoticeTodoRepository todoRepository;

    /**
     * 关闭 OPEN 待办；关闭不解除仍成立的硬阻断条件（BR-06）。
     *
     * @param command 关闭命令
     * @return todoId + status
     */
    @Transactional
    public CloseNoticeTodoResult closeTodo(CloseNoticeTodoCommand command) {
        // 前置校验：主键与关闭原因必填
        if (command == null || command.getTodoId() == null || command.getTenantId() == null) {
            throw new BusinessException(ChsErrorCodes.NOTICE_TODO_NOT_FOUND, "todoId 不能为空");
        }
        String reason = command.getCloseReason();
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ChsErrorCodes.NOTICE_PARAM_INVALID, "closeReason 不能为空");
        }
        // 加载聚合：租户内未删除待办
        YbCatalogNoticeTodo todo = todoRepository
            .findByIdAndTenantIdAndIsDeletedFalse(command.getTodoId(), command.getTenantId())
            .orElseThrow(() -> new BusinessException(
                ChsErrorCodes.NOTICE_TODO_NOT_FOUND,
                "待办不存在，todoId=" + command.getTodoId()));
        // 已关闭不可重复关
        if (STATUS_CLOSED.equals(todo.getStatus())) {
            throw new BusinessException(ChsErrorCodes.NOTICE_TODO_CLOSED, "待办已关闭");
        }
        // 持久化：标记 CLOSED 与审计字段
        todo.setStatus(STATUS_CLOSED);
        todo.setCloseReason(reason.trim());
        todo.setClosedBy(command.getOperatorId());
        todo.setClosedAt(LocalDateTime.now());
        todoRepository.save(todo);

        CloseNoticeTodoResult result = new CloseNoticeTodoResult();
        result.setTodoId(todo.getId());
        result.setStatus(STATUS_CLOSED);
        return result;
    }

    /**
     * 供合规报告重算时 upsert OPEN 待办（同租户+类型+编码已存在 OPEN 则刷新文案）。
     *
     * @param tenantId      租户
     * @param todoType      STOP / PAY_CLASS
     * @param hilistCode    国家码
     * @param projectCode   项目码；可空
     * @param productName   展示名或问题文案
     * @param effectiveDate 催办参考日
     * @param docId         关联文件；可空
     * @param lineId        关联明细；可空
     */
    @Transactional
    public void upsertOpenTodo(
            Long tenantId,
            String todoType,
            String hilistCode,
            String projectCode,
            String productName,
            java.time.LocalDate effectiveDate,
            Long docId,
            Long lineId) {
        if (tenantId == null || todoType == null || todoType.isBlank()) {
            return;
        }
        YbCatalogNoticeTodo existing = todoRepository
            .findFirstByTenantIdAndTodoTypeAndHilistCodeAndStatusAndIsDeletedFalse(
                tenantId, todoType, hilistCode, STATUS_OPEN)
            .orElse(null);
        if (existing != null) {
            existing.setProjectCode(projectCode);
            existing.setProductName(productName);
            existing.setEffectiveDate(effectiveDate);
            existing.setDocId(docId);
            existing.setLineId(lineId);
            todoRepository.save(existing);
            return;
        }
        YbCatalogNoticeTodo todo = new YbCatalogNoticeTodo();
        todo.setTenantId(tenantId);
        todo.setTodoType(todoType);
        todo.setHilistCode(hilistCode);
        todo.setProjectCode(projectCode);
        todo.setProductName(productName);
        todo.setEffectiveDate(effectiveDate);
        todo.setDocId(docId);
        todo.setLineId(lineId);
        todo.setStatus(STATUS_OPEN);
        todo.setIsDeleted(false);
        todoRepository.save(todo);
    }
}
