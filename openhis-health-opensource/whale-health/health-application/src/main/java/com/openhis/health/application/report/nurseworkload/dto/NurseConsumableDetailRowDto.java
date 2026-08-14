/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.nurseworkload.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 护士耗材使用明细行（IF-RPT-NWL-05）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "护士耗材使用明细行")
public class NurseConsumableDetailRowDto {

    /** 发放主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "发放主键")
    private Long dispenseId;

    /** 耗材定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材定义主键")
    private Long deviceDefId;

    /** 耗材名称。 */
    @Schema(description = "耗材名称")
    private String deviceName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specText;

    /** 高值标志。 */
    @Schema(description = "高值标志")
    private Boolean hvcmFlag;

    /** 高/低值展示名。 */
    @Schema(description = "高/低值展示名")
    private String hvcmDisplay;

    /** 生成来源码。 */
    @Schema(description = "生成来源码")
    private String generateSourceEnum;

    /** 生成来源展示名。 */
    @Schema(description = "生成来源展示名")
    private String generateSourceDisplay;

    /** 护士主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "护士主键")
    private Long practitionerId;

    /** 护士姓名。 */
    @Schema(description = "护士姓名")
    private String practitionerName;

    /** 业务时间。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务时间")
    private LocalDateTime businessTime;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 单位码。 */
    @Schema(description = "单位码")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitCodeLabel;

    /** 就诊主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊主键")
    private Long encounterId;

    /** 就诊号。 */
    @Schema(description = "就诊号")
    private String encounterBusNo;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 售价。 */
    @Schema(description = "售价")
    private BigDecimal saleAmount;

    /** 进价（批号进价包装锚）。 */
    @Schema(description = "进价")
    private BigDecimal purchaseAmount;
}
