/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建调拨执行单命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createTransfer}）。
 *
 * <p>枚举语义字段均为持久化 {@code getCode()}（B.10.8.1）；源、目位置须满足领域创建门控。
 * 可选 {@link #bootstrapFinalStatusCode} 用于调出 agree 后生成对侧调入壳单（DM §6.1.8）。
 * 壳单行须写入 {@code approverId}/{@code approvalTime}（bootstrap AGREE 不走 {@code approvalToAgree}）。</p>
 */
@Getter
@Setter
public class CreateTransferCommand extends BatchCommand<CreateTransferCommand.CreateTransferItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 可选：建单后直接落指定终态（如调出 agree 后生成对侧调入壳单为 {@code AGREE}）。
     * 须为合法 {@link com.openhis.health.domain.share.enums.SupplyStatus} 持久化 code。
     */
    private String bootstrapFinalStatusCode;

    /**
     * 调拨单行。
     */
    @Getter
    @Setter
    public static class CreateTransferItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 来源业务单号。 */
        private String originalBusNo;
        /** 供应类型持久化码。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 数量。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 源位置类型持久化码。 */
        private String sourceTypeEnum;
        /** 源位置主键。 */
        private Long sourceLocationId;
        /** 目标位置类型持久化码。 */
        private String purposeTypeEnum;
        /** 目标位置主键。 */
        private Long purposeLocationId;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /**
         * 审批人主键（调出 agree 生成调入壳单时沿用调出审批人；主动调出新建可空）。
         */
        private Long approverId;
        /**
         * 审批时刻（壳单引导 AGREE 时与调出审批时刻对齐；主动调出新建可空）。
         */
        private LocalDateTime approvalTime;
        /** 原因。 */
        private String reason;
        /** 批号（调拨行/壳单追溯）。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;
        /** 供应商标识。 */
        private Long supplierId;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
        /** 经手人（医务人员主键）。 */
        private Long practitionerId;
    }
}
