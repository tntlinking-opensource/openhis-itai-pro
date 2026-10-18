/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.application.internal.compensation;

import com.zxplat.domain.share.enums.InvoiceStatus;
import com.zxplat.plugin.einvoice.boss.domain.share.EinvBossErrorCodes;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.domain.enums.CompensationExceptionType;
import com.zxplat.plugin.einvoice.boss.domain.enums.TaskStatus;
import com.zxplat.plugin.einvoice.boss.domain.issue.entity.EinvBossIssueTask;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 待补偿异常分类（纯逻辑，无 Spring）。
 */
public final class BossCompensationClassifier {

    private BossCompensationClassifier() {
    }

    /**
     * 判定异常类型；非待补偿项返回 empty。
     *
     * @param snapshot 票据快照
     * @param task 活跃任务
     * @param staleMinutes 超时分钟阈值
     * @param now 当前时间
     * @return 异常类型
     */
    public static Optional<CompensationExceptionType> classify(
            BossIssueSnapshot snapshot,
            Optional<EinvBossIssueTask> task,
            int staleMinutes,
            LocalDateTime now) {
        if (snapshot == null || snapshot.getInvoiceId() == null) {
            return Optional.empty();
        }
        if (InvoiceStatus.ISSUED.getCode().equals(snapshot.getInvoiceStatus())) {
            return Optional.empty();
        }
        if (InvoiceStatus.CANCELLED.getCode().equals(snapshot.getInvoiceStatus())) {
            return Optional.empty();
        }
        if (task.isEmpty()) {
            return Optional.of(CompensationExceptionType.LOST);
        }
        EinvBossIssueTask active = task.get();
        if (TaskStatus.FAILED.getCode().equals(active.getStatus())) {
            if (EinvBossErrorCodes.EINV_AMOUNT_MISMATCH.equals(active.getLastErrorCode())) {
                return Optional.of(CompensationExceptionType.AMOUNT);
            }
            return Optional.of(CompensationExceptionType.FAILED);
        }
        if (isStale(snapshot, staleMinutes, now)) {
            return Optional.of(CompensationExceptionType.STALE);
        }
        return Optional.empty();
    }

    /**
     * 判定 DRAFT 票据是否超过等待阈值（用于 STALE 补偿分类）。
     *
     * <p>仅以 {@link BossIssueSnapshot#getBillDateTime()} 为锚点，与
     * {@link #waitingMinutes} 计算口径一致；非 DRAFT 或锚点为空视为未超时。</p>
     *
     * @param snapshot 票据快照
     * @param staleMinutes 超时分钟阈值（来自配置）
     * @param now 当前时间
     * @return 超时返回 true
     */
    private static boolean isStale(BossIssueSnapshot snapshot, int staleMinutes, LocalDateTime now) {
        if (!InvoiceStatus.DRAFT.getCode().equals(snapshot.getInvoiceStatus())) {
            return false;
        }
        LocalDateTime anchor = snapshot.getBillDateTime();
        if (anchor == null) {
            return false;
        }
        return Duration.between(anchor, now).toMinutes() >= staleMinutes;
    }

    /**
     * 计算等待分钟数。
     *
     * @param snapshot 快照
     * @param now 当前时间
     * @return 分钟
     */
    public static long waitingMinutes(BossIssueSnapshot snapshot, LocalDateTime now) {
        if (snapshot == null || snapshot.getBillDateTime() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(snapshot.getBillDateTime(), now).toMinutes());
    }
}
