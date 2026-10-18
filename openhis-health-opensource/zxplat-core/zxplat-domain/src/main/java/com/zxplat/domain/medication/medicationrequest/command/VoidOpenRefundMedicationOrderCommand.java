/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.medication.medicationrequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：软删未完结负向退药申请命令。
 *
 * <p>条目须为带 {@code refund_medicine_id} 的负向单；药房已完成物理退药的门禁由应用层先行拦截。</p>
 */
@Getter
@Setter
public class VoidOpenRefundMedicationOrderCommand {

    /** 负向退药申请主键集合（须含 {@code refund_medicine_id}）。 */
    private Collection<Long> refundRequestIds;

    /** 操作人标识（写入删除审计）。 */
    private Long operatorId;
}
