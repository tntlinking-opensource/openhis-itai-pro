/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.devicerequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊退费申请撤销：软删未完结负向退耗申请命令。
 *
 * <p>条目须为带 {@code refund_device_id} 的负向单；药房已完成物理退耗的门禁由应用层先行拦截。</p>
 */
@Getter
@Setter
public class VoidOpenRefundDeviceOrderCommand {

    /** 负向退耗申请主键集合（须含 {@code refund_device_id}）。 */
    private Collection<Long> refundRequestIds;

    /** 操作人标识（写入删除审计）。 */
    private Long operatorId;
}
