package com.openhis.whale.einvoice.boss.application.compensation;

import com.openhis.health.domain.share.enums.InvoiceStatus;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.enums.CompensationExceptionType;
import com.openhis.whale.einvoice.boss.contracts.enums.TaskStatus;
import com.openhis.whale.einvoice.boss.domain.task.entity.EinvBossIssueTask;

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
