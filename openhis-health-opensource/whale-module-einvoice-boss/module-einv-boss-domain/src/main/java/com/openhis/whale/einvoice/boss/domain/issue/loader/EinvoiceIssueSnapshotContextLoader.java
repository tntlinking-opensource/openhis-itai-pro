/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.loader;

import com.openhis.whale.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.domain.reconcile.result.ReconcileLocalStats;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 读取 health 侧开票快照的端口（CQRS 读模型）。
 *
 * <p>由 bridge {@code EinvoiceIssueSnapshotContextLoaderImpl} 实现，聚合票据、支付、患者、就诊、
 * 收费明细等 HIS 数据，供 application Worker 组装博思 payload 与对账统计。</p>
 */
public interface EinvoiceIssueSnapshotContextLoader {

    /**
     * 按票据主键加载开票快照。
     *
     * @param invoiceId 票据主键
     * @return 快照
     */
    BossIssueSnapshot loadForInvoice(Long invoiceId);

    /**
     * 查找仍处于 DRAFT 且尚无 boss 活跃任务的票据（事件丢失兜底）。
     *
     * @param limit 最大条数
     * @return 票据主键列表
     */
    List<Long> findOrphanDraftInvoiceIds(int limit);

    /**
     * 查找创建/业务时间早于阈值的 DRAFT 票据（超时待查扫描）。
     *
     * @param threshold 时间阈值
     * @param limit 最大条数
     * @return 票据主键列表
     */
    List<Long> findDraftInvoiceIdsBefore(LocalDateTime threshold, int limit);

    /**
     * 按患者姓名/就诊号/busNo 关键词检索票据主键（查询列表筛选用）。
     *
     * @param keyword 关键词
     * @param invoiceStatus 票据状态过滤（可空）
     * @param limit 最大条数
     * @return 票据主键列表
     */
    List<Long> findInvoiceIdsByKeyword(String keyword, String invoiceStatus, int limit);

    /**
     * 统计指定日期的本地已开具笔数与金额（日结核对 P2）。
     *
     * @param checkDate 核对日期
     * @param dateMode BUS_DATE 或 IVC_DATE
     * @param issueKind 业务线过滤（REG/AMB/IMP，可空）
     * @return 本地统计
     */
    ReconcileLocalStats countDailyReconcileStats(LocalDate checkDate, String dateMode, String issueKind);
}
