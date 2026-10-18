/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.supplyrequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建损益执行单命令（见 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createGainLoss}）。
 *
 * <p>行字段口径：{@code itemQuantity} 盈亏（可负）、{@code totalQuantity} 实盘、{@code totalPrice} 实盘金额、
 * {@code profitLossPrice} 盈亏金额；损益库房仅写 {@code purposeLocationId}/{@code purposeTypeEnum}（单端点）。</p>
 *
 * <p>修订：首行 {@code busNo} 非空时表示同号全量替换（软删+重插），见
 * {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createGainLoss}。</p>
 *
 * <p>枚举语义字段为持久化 {@code getCode()}（B.10.8.1）；同意后由应用层对非零盈亏行入账。</p>
 */
@Getter
@Setter
public class CreateGainLossCommand extends BatchCommand<CreateGainLossCommand.CreateGainLossItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 损益单行（单端点 purpose 语义：报损/报溢所在库房）。
     */
    @Getter
    @Setter
    public static class CreateGainLossItemCommand {
        /** 业务单号；修订时首行必填既有单号，新建时留空由领域生成。 */
        private String busNo;
        /** 供应类型持久化码（{@link com.zxplat.domain.share.enums.SupplyType} 损益类）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 盈亏数量（拆零；盈为正、亏为负、可无差异为 0）。 */
        private BigDecimal itemQuantity;
        /** 实盘数量（拆零，≥0）。 */
        private BigDecimal totalQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 损益库房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 损益库房位置主键（单端点真源）。 */
        private Long purposeLocationId;
        /** 批号。 */
        private String lotNumber;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 经手人主键。 */
        private Long practitionerId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（库列 {@code remake}）。 */
        private String remake;
        /** 单价（可选，展示/计价辅助）。 */
        private BigDecimal price;
        /** 实盘金额。 */
        private BigDecimal totalPrice;
        /** 盈亏金额（盈为正、亏为负）。 */
        private BigDecimal profitLossPrice;
        /** 实盘扫到的剩余追溯码逗号串（IF-TRC-14 草稿暂存）。 */
        private String traceNo;
    }
}
