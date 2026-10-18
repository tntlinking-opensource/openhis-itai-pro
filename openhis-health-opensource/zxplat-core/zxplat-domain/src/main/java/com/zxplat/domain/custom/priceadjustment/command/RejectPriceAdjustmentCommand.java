/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.priceadjustment.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 审批驳回调价单命令（DM-CUS-011 §5.1.4）。
 *
 * <p>驳回原因必填；领域层迁移 rejected。物流解锁库存由应用层同事务编排。</p>
 * <p>{@code operatorId} 供应用层解锁审计，领域层不消费。</p>
 */
@Getter
@Setter
public class RejectPriceAdjustmentCommand {

    /** 调价单号 {@code bus_no}。 */
    private String busNo;

    /** 审批人 {@code approver_id}。 */
    private Long approverId;

    /** 驳回原因（必填，写入 {@code approve_comment}）。 */
    private String approveComment;

    /** 审批/驳回时间；空则由领域层取当前时刻。 */
    private Date approveTime;

    /** 应用层解锁库存审计操作人；领域层不消费。 */
    private Long operatorId;
}
