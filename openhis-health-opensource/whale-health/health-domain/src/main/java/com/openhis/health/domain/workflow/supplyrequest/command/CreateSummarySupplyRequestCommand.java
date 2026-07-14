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
 * 创建药品汇总供应请求命令（type 27，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createSummarySupplyRequest}）。
 *
 * <p>枚举语义字段为持久化 {@code getCode()}（B.10.8.1）。</p>
 * <p>type 27 为单端点：应用层仅装配 {@code purposeLocationId}/{@code purposeTypeEnum}（发放药房）；
 * {@code source_*} 不落库。</p>
 */
@Getter
@Setter
public class CreateSummarySupplyRequestCommand extends BatchCommand<CreateSummarySupplyRequestCommand.CreateSummarySupplyRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 汇总单行。
     */
    @Getter
    @Setter
    public static class CreateSummarySupplyRequestItemCommand {
        /** 业务单号。 */
        private String busNo;
        /** 汇总主键。 */
        private Long summaryId;
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
        /** 患者主键。 */
        private Long patientId;
        /** 批号。 */
        private String lotNumber;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 发放药房类型持久化码（由应用层按 {@code adm_location} 推导）。 */
        private String purposeTypeEnum;
        /** 发放药房主键（{@code MedicationDispense.location_id}）。 */
        private Long purposeLocationId;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
    }
}
