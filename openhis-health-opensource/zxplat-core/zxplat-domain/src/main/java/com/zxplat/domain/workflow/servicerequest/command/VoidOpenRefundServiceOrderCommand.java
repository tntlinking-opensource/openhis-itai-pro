/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.servicerequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：软删未完结负向退诊疗申请命令。
 *
 * <p>条目须为带 {@code refund_service_id} 的负向单；主档与明细同事务软删。</p>
 */
@Getter
@Setter
public class VoidOpenRefundServiceOrderCommand {

    /** 负向退诊疗申请主键集合（须含 {@code refund_service_id}）。 */
    private Collection<Long> refundRequestIds;

    /** 操作人标识（写入删除审计）。 */
    private Long operatorId;
}
