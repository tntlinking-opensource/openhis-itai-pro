/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.repository;

import com.openhis.whale.einvoice.boss.domain.issue.entity.EinvBossIssueTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 异步开票任务仓储（{@code einv_boss_issue_task}）。
 *
 * <p>Worker 轮询 {@link #findByStatusAndIsDeletedFalse} / 失败重试查询，
 * 多实例通过 {@link #claimForProcessing} 原子抢占防重复调博思。</p>
 */
public interface EinvBossIssueTaskRepository extends JpaRepository<EinvBossIssueTask, Long> {

    /**
     * 按票据主键查询未删除的活跃任务。
     *
     * @param invoiceId 票据主键
     * @return 任务（若存在）
     */
    Optional<EinvBossIssueTask> findByInvoiceIdAndIsDeletedFalse(Long invoiceId);

    /**
     * 查询待消费任务（PENDING）。
     *
     * @param status 状态码
     * @return 任务列表
     */
    List<EinvBossIssueTask> findByStatusAndIsDeletedFalse(String status);

    /**
     * 查询到达重试时间的失败任务。
     *
     * @param status 状态码
     * @param nextRetryAt 当前时间
     * @return 任务列表
     */
    List<EinvBossIssueTask> findByStatusAndIsDeletedFalseAndNextRetryAtBefore(String status, LocalDateTime nextRetryAt);

    /**
     * 原子抢占任务（蓝绿多实例仅一条 UPDATE 成功，防重复调博思开票）。
     *
     * @param taskId 任务主键
     * @param processingStatus 处理中状态码
     * @param claimableStatuses 可抢占的源状态
     * @param now 更新时间
     * @return 更新行数（1 表示抢占成功）
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EinvBossIssueTask t
            SET t.status = :processingStatus,
                t.attemptCount = t.attemptCount + 1,
                t.updatedAt = :now
            WHERE t.id = :taskId
              AND t.isDeleted = false
              AND t.status IN :claimableStatuses
            """)
    int claimForProcessing(
            @Param("taskId") Long taskId,
            @Param("processingStatus") String processingStatus,
            @Param("claimableStatuses") Collection<String> claimableStatuses,
            @Param("now") LocalDateTime now);
}
