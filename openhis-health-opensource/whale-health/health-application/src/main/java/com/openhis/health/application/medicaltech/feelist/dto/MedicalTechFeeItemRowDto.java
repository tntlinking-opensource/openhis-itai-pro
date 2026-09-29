/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.medicaltech.feelist.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 医技费用清单行（AS-MT-FEE-001 IF-MT-FEE-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技费用清单行")
public class MedicalTechFeeItemRowDto {

    /** 收费明细主键 {@code adm_charge_item.id}。 */
    @Schema(description = "收费明细主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 项目名称（产品定义展示名）。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 项目编码（产品定义 busNo 或等价）。 */
    @Schema(description = "项目编码")
    private String itemCode;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 行金额。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;

    /** 收费状态码（{@link com.openhis.health.domain.share.enums.ChargeItemStatus#getCode()}）。 */
    @Schema(description = "收费状态码")
    private String chargeStatusEnum;

    /** 收费状态展示文案。 */
    @Schema(description = "收费状态展示")
    private String chargeStatusEnumText;

    /** 上游服务表名（如 {@code wkf_service_request}/{@code wkf_device_request}）。 */
    @Schema(description = "服务表锚点")
    private String serviceTable;

    /** 上游服务主键。 */
    @Schema(description = "服务主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long serviceId;

    /**
     * 划价时间（{@code adm_charge_item.occurrence_time}）。
     *
     * <p>护士执行入账/医技划价写入的费用发生时刻。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "划价时间")
    private LocalDateTime pricedTime;

    /**
     * 收费时间（{@code adm_charge_item.entered_date}，BR-OPC-016）。
     *
     * <p>真正结算（或退费）写入的收费人戳时刻；待结算时通常为空。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "收费时间")
    private LocalDateTime occurredTime;

    /** 开立科室展示名（{@code requesting_org_id}）。 */
    @Schema(description = "开立科室")
    private String relatedOrgDisplay;

    /**
     * 确费人主键；空=未确费。
     *
     * <p>有值即本行已确费（一键确费写入）。</p>
     */
    @Schema(description = "确费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long feeConfirmerId;

    /**
     * 确费人展示名；与 {@link #feeConfirmerId} 成对；未确费时为空。
     */
    @Schema(description = "确费人展示名")
    private String feeConfirmerName;

    /** 确费时刻；与 feeConfirmerId 成对。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "确费时刻")
    private LocalDateTime feeConfirmedAt;

    /**
     * 计费人主键（划价/执行操作人）。
     *
     * <p>优先 Procedure 主执行人 / DeviceDispense 发放·配药人；否则回落上游 Request
     * {@code requester_id}。与 {@code enterer_id}（收费人，BR-OPC-016）分列。</p>
     */
    @Schema(description = "计费人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long billingPractitionerId;

    /**
     * 计费人展示名；与 {@link #billingPractitionerId} 成对。
     */
    @Schema(description = "计费人展示名")
    private String billingPractitionerName;
}
