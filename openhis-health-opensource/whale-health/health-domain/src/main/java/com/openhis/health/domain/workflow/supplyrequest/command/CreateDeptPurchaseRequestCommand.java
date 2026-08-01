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
 * 创建或修订科室采购申请单命令（领域入口
 * {@link com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestDomainService#createDeptPurchaseRequest}）。
 *
 * <p>单端点语义：仅写 {@code purpose_location_id}/{@code purpose_type_enum}（科室药房），{@code source_*} 与
 * {@code supplier_id} 保持空；产品定义级选品（无批号）。修订：首行 {@code busNo} 非空表示同号全量替换。</p>
 *
 * <p>凡 {@code *_enum} 字段一律为枚举 {@code getCode()} 持久化字符串（B.10.8.1）。</p>
 */
@Getter
@Setter
public class CreateDeptPurchaseRequestCommand
    extends BatchCommand<CreateDeptPurchaseRequestCommand.CreateDeptPurchaseRequestItemCommand> {

    /** 操作人主键（事件与审计）。 */
    private Long operatorId;

    /**
     * 修订时前端提交的期望 {@code version}，须与同号各行 JPA {@code @Version} 一致（对偶订货乐观锁）。
     */
    private Integer expectedRowVersionForRevision;

    /**
     * 科室采购申请行：新建时 {@code busNo} 可空；修订时首行须带既有单号。
     */
    @Getter
    @Setter
    public static class CreateDeptPurchaseRequestItemCommand {
        /** 业务单号；修订路径必填。 */
        private String busNo;
        /** 供应类型持久化码（须为 {@link com.openhis.health.domain.share.enums.SupplyType#DEPT_PURCHASE_REQUEST}）。 */
        private String typeEnum;
        /** 物品来源表。 */
        private String itemTable;
        /** 物品定义主键。 */
        private Long itemId;
        /** 申请数量（拆零）。 */
        private BigDecimal itemQuantity;
        /** 计量单位编码。 */
        private String unitCode;
        /** 供应类别持久化码。 */
        private String categoryEnum;
        /** 科室药房位置类型持久化码（单端点真源）。 */
        private String purposeTypeEnum;
        /** 科室药房位置主键（单端点真源）。 */
        private Long purposeLocationId;
        /** 期望/业务时间（制单时刻）。 */
        private LocalDateTime occurrenceTime;
        /** 经手人医务人员主键。 */
        private Long practitionerId;
        /** 申请人主键。 */
        private Long applicantId;
        /** 申请时间；草稿阶段可空。 */
        private LocalDateTime applyTime;
        /** 原因说明。 */
        private String reason;
        /** 行备注（落库 {@code remake}）。 */
        private String remake;
        /** 协商/参考单价。 */
        private BigDecimal price;
        /** 行总价。 */
        private BigDecimal totalPrice;
    }
}
