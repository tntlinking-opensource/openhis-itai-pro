/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.supplyrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SupplyRequestCreatedEvent 领域事件。
 *
 * <p>须在发布前写入与 {@code wkf_supply_request} 首行一致的明细快照（见 {@code SupplyRequestDomainService#publishCreated}）。</p>
 */
@Getter
@Setter
public class SupplyRequestCreatedEvent extends DomainEvent {

    /** 业务单号。 */
    private String busNo;

    /** 单据类型。 */
    private String typeEnum;

    /** 单据状态（初态）。 */
    private String statusEnum;

    /** 来源单据号（执行单场景可选）。 */
    private String originalBusNo;

    /** 汇总编号（药品汇总单可选）。 */
    private Long summaryId;

    /** 患者标识（药品汇总单可选）。 */
    private Long patientId;

    /** 明细行数（同 busNo 聚合的行数，可选）。 */
    private Integer lineCount;

    /** 操作人标识。 */
    private Long operatorId;

    /** 供应类别编码。 */
    private String categoryEnum;

    /** 来源类型编码（SupplyRequestCreatedEvent）。 */
    private String sourceTypeEnum;

    /** 来源库位主键（SupplyRequestCreatedEvent）。 */
    private Long sourceLocationId;

    /** 目的类型编码（SupplyRequestCreatedEvent）。 */
    private String purposeTypeEnum;

    /** 目的库位主键（SupplyRequestCreatedEvent）。 */
    private Long purposeLocationId;

    /** 明细项表名（SupplyRequestCreatedEvent）。 */
    private String itemTable;

    /** 明细项主键（SupplyRequestCreatedEvent）。 */
    private Long itemId;

    /** 明细项数量（SupplyRequestCreatedEvent）。 */
    private BigDecimal itemQuantity;

    /** 单位编码（SupplyRequestCreatedEvent）。 */
    private String unitCode;

    /** 药品/物资批号。 */
    private String lotNumber;

    /** 单价（SupplyRequestCreatedEvent）。 */
    private BigDecimal price;

    /** 合计单价（SupplyRequestCreatedEvent）。 */
    private BigDecimal totalPrice;

    /** 供应商主键（SupplyRequestCreatedEvent）。 */
    private Long supplierId;

    /** 申请人主键（SupplyRequestCreatedEvent）。 */
    private Long applicantId;

    /** 申请时间（SupplyRequestCreatedEvent）。 */
    private LocalDateTime applyTime;

    /** 原因（SupplyRequestCreatedEvent）。 */
    private String reason;

    /** 备注（SupplyRequestCreatedEvent）。 */
    private String remake;

    /** 发生时间（SupplyRequestCreatedEvent）。 */
    private LocalDateTime occurrenceTime;

    /** 效期区间起点（与 {@code wkf_supply_request.start_time} 对齐）。 */
    private LocalDateTime startTime;

    /** 效期区间止点（与 {@code wkf_supply_request.end_time} 对齐）。 */
    private LocalDateTime endTime;

    /** 医务人员主键（SupplyRequestCreatedEvent）。 */
    private Long practitionerId;

    /** 追溯码字符串（可选）。 */
    private String traceNo;

    /**
     * 无参构造（反序列化/框架实例化用）。
     */
    public SupplyRequestCreatedEvent() {
        super();
    }

    public SupplyRequestCreatedEvent(
        Long aggregateId,
        String busNo,
        String typeEnum,
        String statusEnum,
        Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SUPPLY_REQUEST);
        this.busNo = busNo;
        this.typeEnum = typeEnum;
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
