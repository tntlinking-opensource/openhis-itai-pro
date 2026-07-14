/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.issue.access;

import com.openhis.whale.einvoice.boss.domain.issue.result.BossInvoiceIssueResult;

import java.util.Optional;

/**
 * 博思电子票据对外编排端口（domain 层契约）。
 *
 * <p>调用链：health 支付/作废事件 → bridge {@code BossElectronicInvoiceExtensionImpl}
 * → 本端口 → application {@code EinvoiceIssueOrchestratorImpl} → Worker / 同步服务。</p>
 *
 * <p>实现类在 application 而非 bridge：Boss 异步开具 Worker、任务表、Flyway 均驻留 application，
 * bridge 仅通过本接口解耦，禁止反向依赖 application 源码包。</p>
 */
public interface EinvoiceIssueOrchestrator {

    /**
     * 异步入队开具任务：创建 {@code einv_boss_document/line/task}，不阻塞支付事务提交。
     *
     * <p>幂等：同一 {@code invoiceId} 已有活跃 task 时静默跳过（uk_einv_task_invoice_active）。</p>
     *
     * @param invoiceId health 侧 {@code adm_invoice.id}；{@code null} 时调用方应提前拦截
     */
    void scheduleIssueAsync(Long invoiceId);

    /**
     * 按 boss 单据主键同步触发一次开具（走 Worker 全链路）。
     *
     * <p>用于手工补偿、文档页「立即开票」。本地已有 {@code bossBillNo} 时幂等返回缓存票面。</p>
     *
     * @param documentId boss 单据主键 {@code einv_boss_document.id}
     * @return 博思蓝票结果（含 billNo、二维码、图片 URL）
     */
    BossInvoiceIssueResult issueByDocument(Long documentId);

    /**
     * 异步调度冲红（作废/退费事件 afterCommit 后触发）。
     *
     * <p>实际经 {@code BossElectronicInvoiceAsyncBridge} 走 {@code @Async}，
     * 避免与 OrchestratorImpl 同类自调用导致异步失效。</p>
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因（展示给博思；可空时 Extension 层默认「退费冲红」）
     */
    void scheduleWriteOffAsync(Long invoiceId, String reason);

    /**
     * 同步调用博思 {@code writeOffEBill} 冲红。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     * @return 冲红票结果；幂等成功时返回本地已缓存的红票号
     * @throws com.openhis.whale.core.application.exception.BusinessException 无蓝票、博思拒收或并发抢占失败
     */
    BossInvoiceIssueResult writeOff(Long invoiceId, String reason);

    /**
     * 按业务流水号向博思查票（{@code getEBillByBusNo}），用于超时丢单补偿。
     *
     * @param busNo 业务流水号，与 {@code adm_invoice.bus_no} 对齐
     * @return 查票成功且博思返回 S0000 时携带票面；否则 {@link Optional#empty()}
     */
    Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo);

    /**
     * 判断是否具备博思冲红前置条件（本地已记录蓝票号）。
     *
     * <p>Extension 在 scheduleWriteOff 前调用，避免对未开出蓝票的票据无意义冲红。</p>
     *
     * @param invoiceId 票据主键；{@code null} 返回 {@code false}
     * @return {@code einv_boss_document.boss_bill_no} 非空时为 {@code true}
     */
    boolean canWriteOff(Long invoiceId);
}
