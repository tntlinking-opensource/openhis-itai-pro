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
 * 创建或修订药库订货单命令（领域入口 {@link com.zxplat.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createPurchaseOrder}）。
 *
 * <p>凡 {@code typeEnum}/{@code categoryEnum}/{@code sourceTypeEnum}/{@code purposeTypeEnum} 等与库 {@code *_enum} 列对应字段，一律为各业务枚举
 * {@link com.zxplat.domain.share.enums.EnumInterface#getCode()} 持久化字符串（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreatePurchaseOrderCommand extends BatchCommand<CreatePurchaseOrderCommand.CreatePurchaseOrderItemCommand> {

    /** 操作人主键（事件与审计）。 */
    private Long operatorId;

    /**
     * 修订订货单时前端提交的期望 {@code version}，须与同号各行 JPA {@code @Version} 一致（对齐进货单修订语义）。
     */
    private Integer expectedRowVersionForRevision;

    /**
     * 订货单行：新建时 {@code busNo} 可空由领域生成；修订时首行须带既有单号。
     */
    @Getter
    @Setter
    public static class CreatePurchaseOrderItemCommand {
        /** 业务单号；修订路径必填。 */
        private String busNo;
        /** 供应类型持久化码（通常为药库订货 {@link com.zxplat.domain.share.enums.SupplyType#CABINET_PURCHASE_ORDER}）。 */
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
        /**
         * 可选来源科室采购申请单号（{@code original_bus_no}）；空表示手工直建。
         *
         * <p>非空时须指向已同意 {@link com.zxplat.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}，
         * 且未被其它未删除订货占用（BR-WHS-PO-010/012）。修订时由领域自存量保留，禁止切换。</p>
         */
        private String originalBusNo;
        /** 供应商主键。 */
        private Long supplierId;
        /** 期望/业务时间（制单时刻，由前端传入）。 */
        private LocalDateTime occurrenceTime;
        /** 经手人医务人员主键（由前端选择传入）。 */
        private Long practitionerId;
        /** 申请人主键（草稿保存由应用层绑定会话医务人员；提交审批时由领域再次覆盖为会话申请人）。 */
        private Long applicantId;
        /** 申请时间；草稿阶段为 null，提交审批后由领域写入当前时间。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（落库 {@code remake}）。 */
        private String remake;
        /** 单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
