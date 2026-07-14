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
 * 创建科室退领执行单命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeptIssueReturn}）。
 *
 * <p>单端点语义：仅写 {@code purpose_location_id}/{@code purpose_type_enum}（与源领用单相同科室药房），{@code source_*} 保持空；
 * {@code original_bus_no} 必填，指向已同意 {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE}。
 * {@code item_quantity} 为退回正数（拆零）。可退余量校验在应用层（行键不含追溯码）。</p>
 *
 * <p>修订：首行 {@code busNo} 非空时表示同号全量替换（软删+重插），见
 * {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeptIssueReturn}。</p>
 */
@Getter
@Setter
public class CreateDeptIssueReturnCommand extends BatchCommand<CreateDeptIssueReturnCommand.CreateDeptIssueReturnItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 科室退领单行。
     */
    @Getter
    @Setter
    public static class CreateDeptIssueReturnItemCommand {
        /** 业务单号；修订时首行必填既有单号，新建时留空由领域生成。 */
        private String busNo;
        /** 源领用单业务单号（{@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE}）。 */
        private String originalBusNo;
        /** 供应类型持久化码（须为 {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE_RETURN}）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 退回数量（拆零、正数）。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 科室药房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 科室药房位置主键（单端点真源）。 */
        private Long purposeLocationId;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码（源领用行有码时应用层须校验同码）。 */
        private String traceNo;
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
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
