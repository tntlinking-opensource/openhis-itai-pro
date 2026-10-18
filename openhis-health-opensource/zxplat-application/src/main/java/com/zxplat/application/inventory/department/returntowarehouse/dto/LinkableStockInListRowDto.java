/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.department.returntowarehouse.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 可关联科室入库单列表行（IF-DEP-RTW-12：按 {@code bus_no} 聚合，一行一单）。
 *
 * <p>展示名字段由 AppService 在返回前批量富化，对偶 {@code IF-WHS-RT-12}/{@code IF-PHA-RTW-12}。</p>
 */
@Getter
@Setter
@Schema(description = "可关联科室入库单列表行")
public class LinkableStockInListRowDto {

    /** 入库单号 busNo。 */
    @Schema(description = "入库单号 busNo")
    private String busNo;

    /** 入库目的科室药房 purposeLocationId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "目的科室药房 purposeLocationId")
    private Long purposeLocationId;

    /**
     * 目的科室药房展示名：由位置主数据解析。
     */
    @Schema(description = "目的科室药房名称 purposeLocationName")
    private String purposeLocationName;

    /** 供应商 supplierId。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "供应商 supplierId")
    private Long supplierId;

    /** 供应商名称 supplierName。 */
    @Schema(description = "供应商名称 supplierName")
    private String supplierName;

    /**
     * 制单人（申请人）主键：取入库单头行 {@code applicantId}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "制单人主键 applicantId")
    private Long applicantId;

    /**
     * 制单人展示名：由申请人主键解析。
     */
    @Schema(description = "制单人名称 creatorName")
    private String creatorName;

    /** 总金额 totalAmount。 */
    @Schema(description = "总金额 totalAmount")
    private BigDecimal totalAmount;

    /** 审批时间 approvalTime。 */
    @Schema(description = "审批时间 approvalTime")
    private LocalDateTime approvalTime;

    /**
     * 制单时间：优先头行 {@code occurrenceTime}，其次 {@code applyTime}，最后回落 {@code approvalTime}。
     */
    @Schema(description = "制单时间 applyTime")
    private LocalDateTime applyTime;

    /** 是否仍可退库 returnable。 */
    @Schema(description = "是否仍可退库 returnable")
    private Boolean returnable;
}
