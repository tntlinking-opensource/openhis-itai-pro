/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 提交调价单审批命令（DM-CUS-011 §6.1.2）。
 *
 * <p>领域层仅固化子表盈亏并迁移 pending；互斥、快照组装、加锁由应用层同事务前置编排。</p>
 */
@Getter
@Setter
public class SubmitPriceAdjustmentCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 提交人 {@code submitter_id}。 */
    private Long submitterId;

    /** 提交时间；空则由领域层取当前时刻。 */
    private Date submitTime;

    /** 应用层加锁后回填的锁定行数，写入提交事件载荷。 */
    private Integer lockedInventoryCount;
}
