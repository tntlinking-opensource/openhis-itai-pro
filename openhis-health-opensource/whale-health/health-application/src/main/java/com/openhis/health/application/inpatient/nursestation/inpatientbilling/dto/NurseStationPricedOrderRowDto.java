/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已划价列表行（IF-IP-PRC-05，BR-IP-PRC-015）。
 *
 * <p>数据来源 OrderInfo 骨架 + ChargeItem + 可选 DeviceDispense；区分 DIRECT/SUPPLEMENT。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "已划价列表行")
public class NurseStationPricedOrderRowDto {

    @Schema(description = "稳定行键")
    /** 稳定行键。 */
    private String listRowKey;

    @Schema(description = "OrderInfo/请求主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** OrderInfo/请求主键。 */
    private Long orderId;

    /** 医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST。 */
    @Schema(description = "医嘱类型：SERVICE_REQUEST / DEVICE_REQUEST")
    private String orderType;

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String productDisplayName;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitDisplay;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 总价。 */
    @Schema(description = "总价")
    private BigDecimal totalPrice;

    /**
     * 划价客户可见时间（BR-IP-PRC-030）：诊疗=净剩 Procedure.recorded_time；
     * 耗材=主发放 dispense_time（无则 prepare_time）。
     */
    @Schema(description = "划价客户可见时间（recorded/发放）")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime pricedTime;

    /** 费用状态 code。 */
    @Schema(description = "费用状态 code")
    private String chargeStatusEnum;

    /** 费用状态展示。 */
    @Schema(description = "费用状态展示")
    private String chargeStatusDisplay;

    /** 发放状态 code。 */
    @Schema(description = "发放状态 code")
    private String dispenseStatusEnum;

    /** 发放状态展示。 */
    @Schema(description = "发放状态展示")
    private String dispenseStatusDisplay;

    /** 划价模式：DIRECT / SUPPLEMENT。 */
    @Schema(description = "划价模式：DIRECT / SUPPLEMENT")
    private String pricingModeCode;

    /** 划价模式展示。 */
    @Schema(description = "划价模式展示")
    private String pricingModeDisplay;

    /**
     * 上级医嘱来源表（补费子行；与域 {@code based_on_table} 一致，如 {@code med_medication_request}、{@code wkf_service_request}）。
     */
    @Schema(description = "上级医嘱来源表（补费）")
    private String basedOnTable;

    /**
     * 上级医嘱来源主键（补费子行；与域 {@code based_on_id} 一致，对应父医嘱 {@code requestId}）。
     */
    @Schema(description = "上级医嘱来源主键（补费）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 上级医嘱来源主键（补费）。 */
    private Long basedOnId;
}
