/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplydelivery.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建药品汇总发放命令（汇总路径首态非「创建即完成」，见 {@link com.openhis.health.domain.workflow.supplydelivery.service.SupplyDeliveryDomainService#createSummarySupplyDelivery}）。
 *
 * <p>实体 {@code item_table} 等在领域侧可固定为药品定义表；数量与单位等为业务值，状态列仍走 {@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()}（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateSummarySupplyDeliveryCommand extends BatchCommand<CreateSummarySupplyDeliveryCommand.CreateSummarySupplyDeliveryItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 汇总发放单行。
     */
    @Getter
    @Setter
    public static class CreateSummarySupplyDeliveryItemCommand {
        /** 汇总主键。 */
        private Long summaryId;
        /** 上游供应请求行主键。 */
        private Long supplyRequestId;
        /** 患者主键。 */
        private Long patientId;
        /** 物品定义主键（药品）。 */
        private Long itemId;
        /** 批号。 */
        private String lotNumber;
        /** 数量。 */
        private BigDecimal quantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 接收人主键。 */
        private Long receiverId;
        /** 追溯码串（汇总路径落 {@code wkf_supply_delivery.trace_no}，不写入发放行）。 */
        private String traceNo;
    }
}
