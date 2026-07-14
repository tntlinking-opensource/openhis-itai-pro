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
 * 创建科室耗材类汇总/盘点供应请求命令（type 28，见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeviceSummarySupplyRequest}）。
 *
 * <p>枚举语义字段为持久化 {@code getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateDeviceSummarySupplyRequestCommand extends BatchCommand<CreateDeviceSummarySupplyRequestCommand.CreateDeviceSummarySupplyRequestItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 科室耗材汇总/盘点单行。
     */
    @Getter
    @Setter
    public static class CreateDeviceSummarySupplyRequestItemCommand {
        /** 业务单号。 */
        private String busNo;
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
        /** 库房类型持久化码。 */
        private String purposeTypeEnum;
        /** 库房主键。 */
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
