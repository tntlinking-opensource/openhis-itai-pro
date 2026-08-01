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
 * 创建科室领用执行单命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeptIssue}）。
 *
 * <p>单端点语义：仅写 {@code purpose_location_id}/{@code purpose_type_enum}（科室药房），{@code source_*} 保持空；
 * {@code item_quantity} 为领用正数（拆零）。领用科室写入 {@code consuming_organization_id}（行政 Organization），
 * 与科室药房解耦，由应用层校验后传入。</p>
 *
 * <p>修订：首行 {@code busNo} 非空时表示同号全量替换（软删+重插），见
 * {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeptIssue}。</p>
 */
@Getter
@Setter
public class CreateDeptIssueCommand extends BatchCommand<CreateDeptIssueCommand.CreateDeptIssueItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 科室领用单行。
     */
    @Getter
    @Setter
    public static class CreateDeptIssueItemCommand {
        /** 业务单号；修订时首行必填既有单号，新建时留空由领域生成。 */
        private String busNo;
        /** 供应类型持久化码（须为 {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_ISSUE}）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 领用数量（拆零、正数）。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 科室药房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 科室药房位置主键（单端点真源）。 */
        private Long purposeLocationId;
        /**
         * 领用科室（行政 Organization 主键；落库 {@code consuming_organization_id}）。
         *
         * <p>与科室药房解耦：任意启用科室均可领用本药房库存。</p>
         */
        private Long consumingOrganizationId;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
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
        /**
         * 批号效期止点（落库 {@code end_time}；与请领/进货同构，供详情回显有效期）。
         */
        private LocalDateTime endTime;
        /** 单价（可选，展示/计价辅助）。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
