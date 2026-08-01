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
 * 创建或修订药库退供应商命令（见 {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createReturnToSupplier}）。
 *
 * <p>枚举语义字段均为持久化 {@code getCode()}（B.10.8.1），与 {@link com.openhis.health.domain.workflow.supplyrequest.entity.SupplyRequest} 对齐。</p>
 */
@Getter
@Setter
public class CreateReturnToSupplierCommand extends BatchCommand<CreateReturnToSupplierCommand.CreateReturnToSupplierItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

    /**
     * 退供应商单行。
     */
    @Getter
    @Setter
    public static class CreateReturnToSupplierItemCommand {
        /** 业务单号；修订路径必填，新建路径可空。 */
        private String busNo;
        /** 来源进货单业务单号（{@code original_bus_no}），必填。 */
        private String originalBusNo;
        /** 供应类型持久化码（固定药库退供应商）。 */
        private String typeEnum;
        /** 物品来源表（药品/耗材表名）。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 退货数量（拆零量，须为正）。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码（须与进货行一致）。 */
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
        /** 供应商主键。 */
        private Long supplierId;
        /** 经手人医务人员主键（{@code practitioner_id}）；可空，由前端显式选择。 */
        private Long practitionerId;
        /** 业务发生时间。 */
        private LocalDateTime occurrenceTime;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注。 */
        private String remake;
        /**
         * 采购发票号（与来源进货单一致或业务重填；3504 {@code purc_invo_no}）。
         */
        private String invoiceNo;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;
        /**
         * 行单价（包装锚）；落库前由 {@code SupplyRequestWritePricingSupport} 按进货落库价或批号/目录价权威重算。
         */
        private BigDecimal price;
        /**
         * 行合计金额；落库前由写价门禁按拆零数量重算（BR-MON-001）。
         */
        private BigDecimal totalPrice;
    }
}
