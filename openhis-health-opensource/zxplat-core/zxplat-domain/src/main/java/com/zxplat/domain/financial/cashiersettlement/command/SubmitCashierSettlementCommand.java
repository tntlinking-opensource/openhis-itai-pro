/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.financial.cashiersettlement.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交交账命令（DM-FIN-003 · submitCashierSettlement）。
 */
@Getter
@Setter
public class SubmitCashierSettlementCommand {

    /** 交账类型：OP_PAYMENT / IP_PAYMENT / OP_PHARMACY / IP_PHARMACY。 */
    private String typeEnum;

    /** 交账科室；收款必填，药房可空。 */
    private Long orgId;

    /**
     * 科室下 location 主键白名单（收款提交锁候选；由应用层解析 org 下 location 后写入，可选）。
     */
    private List<Long> locationIds;

    /** 交账药房；药房必填，收款侧不用。 */
    private Long locationId;

    /** 提交人（必填）。 */
    private Long submitterId;

    /**
     * payment {@code enterer_id} 匹配白名单（可选软筛；漏交硬栏不按人）。
     */
    private List<Long> entererMatchIds;

    /** 业务窗起（时分秒，落库）。 */
    private LocalDateTime dateFrom;

    /** 业务窗止（时分秒，落库）。 */
    private LocalDateTime dateTo;

    /** 当前操作人（审计）。 */
    private Long operatorId;

    /**
     * 勾选挂载源行主键（payment 或 dispense）。
     *
     * <p>{@code null} 回落全量候选；非空空列表拒绝。</p>
     */
    private List<Long> selectedPaymentIds;

    /**
     * 同 {@link #selectedPaymentIds} 语义别名（药房日结可读性）。
     */
    private List<Long> selectedSourceIds;

    /** 人员快照（逗号分隔）。 */
    private String practitionerIds;

    /** 本次挂载笔数快照。 */
    private Integer paymentCount;

    /** 列表金额快照。 */
    private BigDecimal totalIncome;
}
