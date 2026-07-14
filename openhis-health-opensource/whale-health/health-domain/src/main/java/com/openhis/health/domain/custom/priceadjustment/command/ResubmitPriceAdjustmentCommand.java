/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 驳回后重新提交调价单命令（DM-CUS-011 §6.1.5）。
 *
 * <p>语义同提交审批；互斥、快照组装、加锁由应用层同事务前置编排。</p>
 */
@Getter
@Setter
public class ResubmitPriceAdjustmentCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 提交人 {@code submitter_id}。 */
    private Long submitterId;

    /** 提交时间；空则由领域层取当前时刻。 */
    private Date submitTime;

    /** 应用层加锁后回填的锁定行数，写入再次提交事件载荷。 */
    private Integer lockedInventoryCount;
}
