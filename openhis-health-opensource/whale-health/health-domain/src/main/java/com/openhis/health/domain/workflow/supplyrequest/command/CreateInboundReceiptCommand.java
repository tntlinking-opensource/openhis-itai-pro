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
 * 创建药库入库收货单（进货）或进货验收类单据的命令载体。
 *
 * <p>凡命令中带 {@code Enum} 后缀或与库 {@code *_enum} 列对应的字符串字段，一律承载各业务枚举的
 * {@link com.openhis.health.domain.share.enums.EnumInterface#getCode()}，与持久化 VARCHAR 对齐（B.10.8.1），禁止排序号或数字串中转。</p>
 *
 * <p>领域入口：{@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createInboundReceipt(CreateInboundReceiptCommand)}。</p>
 */
@Getter
@Setter
public class CreateInboundReceiptCommand extends BatchCommand<CreateInboundReceiptCommand.CreateInboundReceiptItemCommand> {

    /** 操作人主键（审计与事件载荷）。 */
    private Long operatorId;

    /** 修订同号单据时期望的乐观锁版本（与同 {@code bus_no} 下各行 {@code version} 一致）。 */
    private Integer expectedRowVersionForRevision;

    /**
     * 单行明细命令（新建与修订重插共用）。
     */
    @Getter
    @Setter
    public static class CreateInboundReceiptItemCommand {
        /** 业务单号；修订路径须携带既有单号。 */
        private String busNo;
        /** 来源订货等业务单号。 */
        private String originalBusNo;
        /** 单据类型（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 数量。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。 */
        private String categoryEnum;
        /** 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
        private String sourceTypeEnum;
        /** 源位置主键。 */
        private Long sourceLocationId;
        /** 目标位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
        private String purposeTypeEnum;
        /** 目标位置主键。 */
        private Long purposeLocationId;
        /** 供应商主键。 */
        private Long supplierId;
        /** 批号。 */
        private String lotNumber;
        /** 追溯码。 */
        private String traceNo;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
        /** 期望/发生时间（制单时刻，由前端传入）。 */
        private LocalDateTime occurrenceTime;

        /** 经手人医务人员主键（由前端选择传入）。 */
        private Long practitionerId;

        /** 效期区间起点（落库 {@code wkf_supply_request.start_time}）。 */
        private LocalDateTime startTime;

        /** 效期区间止点（落库 {@code wkf_supply_request.end_time}）。 */
        private LocalDateTime endTime;

        /** 申请人主键（草稿保存由应用层绑定会话医务人员；提交审批时由领域再次覆盖为会话申请人）。 */
        private Long applicantId;
        /** 申请时间；草稿阶段为 null，提交审批后由领域写入当前时间。 */
        private LocalDateTime applyTime;
        /** 原因编码或说明。 */
        private String reason;
        /** 行备注（库列 remake）。 */
        private String remake;
        /**
         * 采购发票号（库列 {@code invoice_no}；同号多行写入相同值）。
         */
        private String invoiceNo;
    }
}
