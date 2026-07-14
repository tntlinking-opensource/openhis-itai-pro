/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.settlement.service;

import com.openhis.chs.domain.share.constants.ChsErrorCodes;
import com.openhis.chs.domain.inpatient.entity.YbInpatientFeeUpload;
import com.openhis.chs.domain.inpatient.entity.YbInpatientPreSettle;
import com.openhis.chs.domain.outpatient.entity.YbClinicFeeUpload;
import com.openhis.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.openhis.chs.domain.settlement.SettlementInvariantException;
import com.openhis.chs.domain.settlement.YbFeeUploadStatus;
import com.openhis.chs.domain.settlement.YbPreSettleStatus;
import com.openhis.chs.domain.settlement.YbSettlementVoucherStatus;
import com.openhis.chs.domain.settlement.entity.YbSettlementRecord;
import org.springframework.stereotype.Service;

/**
 * 收费批次状态机（规范 §6 / module-chs-jilin-domain README §3.3）。
 *
 * <p>三类实体各自独立跃迁，DomainService 在 {@code repository.save} 前调用：<。</p>
 * <ul>
 *   <li>费用行 {@code yb_*_fee_upload.upload_status}：UPLOADED → SETTLED / REVOKED</li>
 *   <li>预结算 {@code yb_*_pre_settle.status}：ACTIVE → SETTLED / CANCELLED / REVOKED</li>
 *   <li>结算凭证 {@code yb_settlement_record.status}：CONFIRMED → REVOKED</li>
 * </ul>
 *
 * <p>非法跃迁抛 {@link SettlementInvariantException}（INS-C08），不含持久化。<。</p> */
@Service
public class ChargeBatchLifecycle {

    /**
     * 2204 上传成功后标记门诊费用行 UPLOADED。
     *
     * @param row 费用上传行
     */
    public void markClinicFeeUploaded(YbClinicFeeUpload row) {
        row.setUploadStatus(YbFeeUploadStatus.UPLOADED);
    }

    /**
     * 2301 上传成功后标记住院费用行 UPLOADED。
     *
     * @param row 费用上传行
     */
    public void markInpatientFeeUploaded(YbInpatientFeeUpload row) {
        row.setUploadStatus(YbFeeUploadStatus.UPLOADED);
    }

    /**
     * 2207 正式结算后标记门诊费用行 SETTLED。
     *
     * @param row 当前须为 UPLOADED */
    public void markClinicFeeSettled(YbClinicFeeUpload row) {
        assertClinicFeeStatus(row, YbFeeUploadStatus.UPLOADED, "2207 标记已结算");
        row.setUploadStatus(YbFeeUploadStatus.SETTLED);
    }

    /**
     * 2304 正式结算后标记住院费用行 SETTLED。
     *
     * @param row 当前须为 UPLOADED */
    public void markInpatientFeeSettled(YbInpatientFeeUpload row) {
        assertInpatientFeeStatus(row, YbFeeUploadStatus.UPLOADED, "2304 标记已结算");
        row.setUploadStatus(YbFeeUploadStatus.SETTLED);
    }

    /**
     * 2205/2302 撤销未结算费用行（不含 SETTLED）。
     *
     * <p>已参与 2207/2304 的行须走 {@link #markClinicFeeSettleRevoked}。</p>
     *
     * @param row 费用上传行
     */
    public void markClinicFeeRevoked(YbClinicFeeUpload row) {
        markClinicFeeRevoked(row, false);
    }

    /**
     * 2208/2305 撤销正式结算后标记费用行 REVOKED（含 SETTLED）。
     *
     * @param row 费用上传行
     */
    public void markClinicFeeSettleRevoked(YbClinicFeeUpload row) {
        markClinicFeeRevoked(row, true);
    }

    /**
     * 2302 撤销未结算住院费用行。
     *
     * @param row 费用上传行
     */
    public void markInpatientFeeRevoked(YbInpatientFeeUpload row) {
        markInpatientFeeRevoked(row, false);
    }

    /**
     * 2305 撤销正式结算后标记住院费用行 REVOKED。
     *
     * @param row 费用上传行
     */
    public void markInpatientFeeSettleRevoked(YbInpatientFeeUpload row) {
        markInpatientFeeRevoked(row, true);
    }

    /**
     * 2207/2304 成功后：预结算 ACTIVE → SETTLED。
     *
     * @param pre 当前须为 ACTIVE */
    public void markClinicPreSettleSettled(YbClinicPreSettle pre) {
        assertPreSettleActive(pre.getStatus());
        pre.setStatus(YbPreSettleStatus.SETTLED.name());
    }

    /**
     * 2205 或预结算失败回滚：ACTIVE → CANCELLED。
     *
     * @param pre 已 SETTLED 时禁止作废（须先 2208） */
    public void markClinicPreSettleCancelled(YbClinicPreSettle pre) {
        if (YbPreSettleStatus.SETTLED.matches(pre.getStatus())) {
            throw invariant("预结算已正式结算，不可作废");
        }
        pre.setStatus(YbPreSettleStatus.CANCELLED.name());
    }

    /**
     * 2208/2601 后：预结算 → REVOKED。
     *
     * @param pre 预结算快照
     */
    public void markClinicPreSettleRevoked(YbClinicPreSettle pre) {
        pre.setStatus(YbPreSettleStatus.REVOKED.name());
    }

    /**
     * 2304 成功后：住院预结算 ACTIVE → SETTLED。
     *
     * @param pre 当前须为 ACTIVE
     */
    public void markInpatientPreSettleSettled(YbInpatientPreSettle pre) {
        assertPreSettleActive(pre.getStatus());
        pre.setStatus(YbPreSettleStatus.SETTLED.name());
    }

    /**
     * 2302 或重算前作废：ACTIVE → CANCELLED。
     *
     * @param pre 已 SETTLED 时禁止作废（须先 2305）
     */
    public void markInpatientPreSettleCancelled(YbInpatientPreSettle pre) {
        if (YbPreSettleStatus.SETTLED.matches(pre.getStatus())) {
            throw invariant("预结算已正式结算，不可作废");
        }
        pre.setStatus(YbPreSettleStatus.CANCELLED.name());
    }

    /**
     * 2305/2601 后：住院预结算 → REVOKED。
     *
     * @param pre 预结算快照
     */
    public void markInpatientPreSettleRevoked(YbInpatientPreSettle pre) {
        pre.setStatus(YbPreSettleStatus.REVOKED.name());
    }

    /**
     * 2208/2305/2601 后：结算凭证 CONFIRMED → REVOKED（幂等）。
     *
     * @param record 已 REVOKED 时直接返回，不抛异常 */
    public void markVoucherRevoked(YbSettlementRecord record) {
        // 幂等：重复撤销/冲正不重复改状态
        if (YbSettlementVoucherStatus.REVOKED.equals(record.getStatus())) {
            return;
        }
        record.setStatus(YbSettlementVoucherStatus.REVOKED);
    }

    /**
     * 撤销门诊费用行并校验状态（幂等）。
     *
     * @param row            费用上传行
     * @param includeSettled 是否允许撤销已 SETTLED 行
     */
    private void markClinicFeeRevoked(YbClinicFeeUpload row, boolean includeSettled) {
        // 已 REVOKED 则跳过（幂等）
        if (YbFeeUploadStatus.REVOKED.equals(row.getUploadStatus())) {
            return;
        }
        // 2205 不允许撤销已 SETTLED 行，须先 2208
        if (!includeSettled && YbFeeUploadStatus.SETTLED.equals(row.getUploadStatus())) {
            throw invariant("已参与正式结算的费用明细不可 2205 撤销");
        }
        row.setUploadStatus(YbFeeUploadStatus.REVOKED);
    }

    /**
     * 撤销住院费用行并校验状态（幂等）。
     *
     * @param row            费用上传行
     * @param includeSettled 是否允许撤销已 SETTLED 行
     */
    private void markInpatientFeeRevoked(YbInpatientFeeUpload row, boolean includeSettled) {
        if (YbFeeUploadStatus.REVOKED.equals(row.getUploadStatus())) {
            return;
        }
        if (!includeSettled && YbFeeUploadStatus.SETTLED.equals(row.getUploadStatus())) {
            throw invariant("已参与正式结算的费用明细不可 2302 撤销");
        }
        row.setUploadStatus(YbFeeUploadStatus.REVOKED);
    }

    /**
     * 校验门诊费用行当前状态。
     *
     * @param row      费用上传行
     * @param expected 期望 upload_status
     * @param action   操作描述（用于异常消息）
     */
    private static void assertClinicFeeStatus(YbClinicFeeUpload row, String expected, String action) {
        if (row == null || !expected.equals(row.getUploadStatus())) {
            throw invariant(action + " 要求 upload_status=" + expected + "，当前=" + safeStatus(row));
        }
    }

    /**
     * 校验住院费用行当前状态。
     *
     * @param row      费用上传行
     * @param expected 期望 upload_status
     * @param action   操作描述（用于异常消息）
     */
    private static void assertInpatientFeeStatus(YbInpatientFeeUpload row, String expected, String action) {
        if (row == null || !expected.equals(row.getUploadStatus())) {
            throw invariant(action + " 要求 upload_status=" + expected + "，当前=" + safeStatus(row));
        }
    }

    /**
     * 校验预结算状态为 ACTIVE。
     *
     * @param status 当前预结算状态
     */
    private static void assertPreSettleActive(String status) {
        if (!YbPreSettleStatus.ACTIVE.matches(status)) {
            throw invariant("预结算状态须为 ACTIVE，当前=" + status);
        }
    }

    /**
     * 安全读取门诊费用行 upload_status。
     *
     * @param row 费用上传行
     * @return 状态字符串；row 为空时 {@code "null"}
     */
    private static String safeStatus(YbClinicFeeUpload row) {
        if (row == null) {
            return "null";
        }
        return row.getUploadStatus();
    }

    /**
     * 安全读取住院费用行 upload_status。
     *
     * @param row 费用上传行
     * @return 状态字符串；row 为空时 {@code "null"}
     */
    private static String safeStatus(YbInpatientFeeUpload row) {
        if (row == null) {
            return "null";
        }
        return row.getUploadStatus();
    }

    /**
     * 构造结算状态机不变式异常。
     *
     * @param message 异常消息
     * @return 领域异常
     */
    private static SettlementInvariantException invariant(String message) {
        return new SettlementInvariantException(ChsErrorCodes.SETTLEMENT_INVARIANT, message);
    }
}
