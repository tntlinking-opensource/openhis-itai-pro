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
 * 创建或修订请领 / 药房入库类供应请求命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createRequisition}）。
 *
 * <p>枚举语义字段均为各业务枚举 {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateRequisitionCommand extends BatchCommand<CreateRequisitionCommand.CreateRequisitionItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 新建入库壳单时若需跳过待审核直接进入终态，则写入目标 {@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}（如药库出库同意生成的
     * {@link com.openhis.health.domain.share.enums.SupplyType#PHARMACY_STOCK_IN} 壳单须为 {@link com.openhis.health.domain.share.enums.SupplyStatus#AGREE}，AS-INV-WHS-003）。
     * 空白表示沿用领域默认初态（通常为待审核）。
     */
    private String bootstrapFinalStatusCode;

    /**
     * 请领或入库类单行。
     */
    @Getter
    @Setter
    public static class CreateRequisitionItemCommand {
        /** 业务单号；特定类型修订时首行须带既有单号。 */
        private String busNo;
        /** 来源业务单号（入库类场景等）。 */
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
        /** 供应商主键（药房入库入账等）。 */
        private Long supplierId;
        /** 批号。 */
        private String lotNumber;
        /** 生产日期。 */
        private LocalDateTime startTime;
        /** 有效期/失效日期。 */
        private LocalDateTime endTime;
        /** 追溯码。 */
        private String traceNo;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 经手人/业务员 practitioner 主键（与申请人可不同，入库类场景）。 */
        private Long practitionerId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /**
         * 审批人主键（出库 agree 生成入库壳单时沿用出库审批人；普通请领新建可空）。
         */
        private Long approverId;
        /**
         * 审批时间（壳单引导 AGREE 时与出库审批时刻对齐；普通请领新建可空）。
         */
        private LocalDateTime approvalTime;
        /** 原因说明。 */
        private String reason;
    }
}
