/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 供应请求读模型（按业务单号可能返回多行，一行对应一条聚合明细）。
 *
 * <p>DOC-CONFLICT：DomainModel-SDD §4.6.1 曾仅列 id/busNo/typeEnum；应用侧订货详情（AS-INV-WHS-001）需头信息与行字段全集，本模型扩展与实体 {@code wkf_supply_request} 对齐。</p>
 *
 * <p>凡 {@code *Enum} 后缀或与库 {@code *_enum} 列对应的 {@link String} 字段，值均为相应领域枚举的 {@code getCode()} 持久化串（B.10.8.1），与写模型、读模型投影一致。</p>
 */
@Getter
@Setter
public class SupplyRequestModel {

    /** 主键。 */
    private Long id;
    /** 业务单号。 */
    private String busNo;
    /** 供应类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyType#getCode()}）。 */
    private String typeEnum;
    /** 供应状态持久化码（{@link com.openhis.health.domain.share.enums.SupplyStatus#getCode()}）。 */
    private String statusEnum;
    /** 供应类别持久化码（{@link com.openhis.health.domain.share.enums.SupplyCategory#getCode()}）。 */
    private String categoryEnum;
    /** 源位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
    private String sourceTypeEnum;
    /** 来源位置标识。 */
    private Long sourceLocationId;
    /** 目标位置类型持久化码（{@link com.openhis.health.domain.share.enums.SupplyRequestType#getCode()}）。 */
    private String purposeTypeEnum;
    /** 目标位置标识。 */
    private Long purposeLocationId;
    /**
     * 领用科室（行政 Organization 主键；库列 {@code consuming_organization_id}）。
     *
     * <p>{@code DEPT_ISSUE}/{@code DEPT_ISSUE_RETURN} 落库；其它类型通常为空。历史单可空。</p>
     */
    private Long consumingOrganizationId;
    /** 供应商。 */
    private Long supplierId;
    /** 物品来源表。 */
    private String itemTable;
    /** 物品定义主键。 */
    private Long itemId;
    /** 行数量：入出库等为执行数量；盘点等为盈亏数量（可负）。 */
    private BigDecimal itemQuantity;
    /** 实盘/合计数量（盘点等场景）。 */
    private BigDecimal totalQuantity;
    /** 计量单位编码。 */
    private String unitCode;
    /** 期望到货等业务时点。 */
    private LocalDateTime occurrenceTime;

    /** 经手人医务人员主键（与 {@code wkf_supply_request.practitioner_id} 对齐）。 */
    private Long practitionerId;

    /** 效期区间起点（库列 {@code start_time}）。 */
    private LocalDateTime startTime;

    /** 效期区间止点（库列 {@code end_time}）。 */
    private LocalDateTime endTime;

    /** 申请时间。 */
    private LocalDateTime applyTime;
    /** 审批时间。 */
    private LocalDateTime approvalTime;
    /** 申请人。 */
    private Long applicantId;
    /** 审批人。 */
    private Long approverId;
    /** 单价。 */
    private BigDecimal price;
    /** 行总价（盘点等为实盘金额）。 */
    private BigDecimal totalPrice;
    /** 盘点盈亏金额（盈为正、亏为负）。 */
    private BigDecimal profitLossPrice;
    /** 原因说明。 */
    private String reason;
    /** 批号。 */
    private String lotNumber;
    /** 追溯码。 */
    private String traceNo;
    /** 行备注（库列 remake）。 */
    private String remake;
    /**
     * 采购发票号（库列 {@code invoice_no}；同号多行共享，供进销存 {@code purc_invo_no}；未填默认 {@code 0}）。
     */
    private String invoiceNo;
    /** 来源业务单号。 */
    private String originalBusNo;
    /** 汇总编号。 */
    private Long summaryId;
    /** 患者主键（汇总领药行；与 {@code wkf_supply_request.patient_id} 对齐）。 */
    private Long patientId;
    /** JPA 乐观锁版本（{@code wkf_supply_request.version}）。 */
    private Integer version;
}
