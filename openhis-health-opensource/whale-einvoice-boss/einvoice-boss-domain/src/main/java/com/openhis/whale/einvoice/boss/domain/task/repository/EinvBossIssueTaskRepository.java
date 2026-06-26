package com.openhis.whale.einvoice.boss.domain.task.repository;

import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 异步开票任务仓储。
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
}
