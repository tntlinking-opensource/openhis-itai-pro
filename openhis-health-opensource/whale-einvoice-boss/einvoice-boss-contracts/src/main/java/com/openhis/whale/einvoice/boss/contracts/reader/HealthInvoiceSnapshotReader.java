package com.openhis.whale.einvoice.boss.contracts.reader;

import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.dto.ReconcileLocalStatsDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 读取 health 侧开票快照（adapter 实现，application 消费）。
 */
public interface HealthInvoiceSnapshotReader {

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
    ReconcileLocalStatsDto countDailyReconcileStats(LocalDate checkDate, String dateMode, String issueKind);
}
