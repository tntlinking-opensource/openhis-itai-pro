/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.servicerequest.command;

import java.util.Collection;

import lombok.Getter;
import lombok.Setter;

/**
 * 门诊代煎费退款完成后：软删因申请退费而停嘱的代煎 companion {@code ServiceRequest}。
 *
 * <p>仅允许 {@code STOPPED} 主档；主档与明细同事务软删，供医生站再补代煎前清障碍。</p>
 */
@Getter
@Setter
public class SoftDeleteStoppedServiceRequestAfterDecoctionRefundCommand {

    /** 已停嘱的代煎 companion 服务申请主键集合。 */
    private Collection<Long> serviceRequestIds;

    /** 操作人标识（写入删除审计）。 */
    private Long operatorId;
}
