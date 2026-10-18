/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.summarydispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 汇总单合并 Supply 行（IF-IPS-02；无批号）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "汇总单合并明细行")
public class InpatientPharmacySummarySlipLineDto {

    /** 汇总 Supply 行主键 {@code wkf_supply_request.id}（type 27；追溯码核发后落 Delivery）。 */
    @Schema(description = "供应请求行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long supplyRequestId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long itemId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 拆零汇总数量。 */
    @Schema(description = "拆零汇总数量")
    private BigDecimal itemQuantity;

    /** 拆零单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /**
     * 界面数量文案（BR-IP-SUM-015）：输液一律拆零直显；非输液恰为整包装时包装单位凑整
     * （如 {@code 1盒}），否则拆零直显；禁止满包+余量拼接；金额仍用 {@code totalPrice} 只读累加。
     */
    @Schema(description = "界面展示数量文案（输液拆零；非输液整包装凑整）")
    private String displayQuantityText;

    /** 生产厂家展示。 */
    @Schema(description = "生产厂家展示")
    private String manufacturerName;

    /** 发放药房名称（与单头 purposeLocationName 一致）。 */
    @Schema(description = "发放药房名称")
    private String purposeLocationName;

    /** 账单单价。 */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /** 组内 ChargeItem.total_price 累加。 */
    @Schema(description = "总价")
    private BigDecimal totalPrice;

    /** 追溯码（汇总核发后来自 SupplyDelivery；待发时由前端暂存）。 */
    @Schema(description = "追溯码")
    private String traceNo;

    /**
     * 包装单位码（产品定义；待扫换算用）。
     */
    @Schema(description = "包装单位码")
    private String packageUnitCode;

    /**
     * 包装相对拆零倍数。
     */
    @Schema(description = "包装相对拆零倍数")
    private BigDecimal packageUnitRatio;

    /**
     * 拆零单位码。
     */
    @Schema(description = "拆零单位码")
    private String splitUnitCode;

    /**
     * 追溯码贴码单位（优先主档；无主档时回落药典）。
     */
    @Schema(description = "追溯码贴码单位（主档优先，否则药典）")
    private String traceUnitCode;

    /**
     * 追溯码贴码单位展示文案（{@code UNIT_CODE}；扫码弹窗药品明细列用）。
     */
    @Schema(description = "追溯码贴码单位展示文案")
    private String traceUnitCodeText;

    /**
     * 追溯码贴码比例（优先主档；无主档时回落药典；1 码单位 = N 拆零）。
     */
    @Schema(description = "追溯码贴码比例（1码=N拆零；主档优先否则药典）")
    private BigDecimal traceUnitRatio;

    /**
     * 主表该品批是否已有追溯码（有码批=true；无码批仅业务行、软提示）。
     */
    @Schema(description = "主表该品批是否已有追溯码")
    private Boolean lotHasTraceMaster;

    /**
     * 回填待扫用的代表批号（合并行本身禁止输出多批号列表；取首个发放行批号）。
     */
    @Schema(description = "代表批号（待扫换算/匹配）")
    private String representativeLotNumber;
}
