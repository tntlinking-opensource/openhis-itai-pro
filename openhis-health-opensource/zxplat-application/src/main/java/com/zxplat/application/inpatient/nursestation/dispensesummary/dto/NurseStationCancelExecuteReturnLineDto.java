/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 取消执行退药明细行（IF-IP-SUM-10）。
 *
 * <p>仅展示因取消已核发执行而产生的 {@code REFUND} 发放行；不暴露药房确认写能力。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "取消执行退药明细行")
public class NurseStationCancelExecuteReturnLineDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 退药发放行主键 {@code med_medication_dispense.id}。 */
    @Schema(description = "退药发放行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 原药嘱主键 {@code med_req_id}（取消执行挂原正向药嘱）。 */
    @Schema(description = "原药嘱主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称（目录展示）。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格文案。 */
    @Schema(description = "规格文案")
    private String specificationText;

    /** 退药数量（绝对值）。 */
    @Schema(description = "退药数量（绝对值）")
    private BigDecimal quantity;

    /** 单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /** 退药状态 code（PENDING_REFUND / REFUNDED）。 */
    @Schema(description = "退药状态 code")
    private String refundStatusEnum;

    /** 退药状态展示（待退药 / 已退药）。 */
    @Schema(description = "退药状态展示")
    private String refundStatusEnumText;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 发放药房名称。 */
    @Schema(description = "发放药房名称")
    private String locationName;

    /** 挂接的 CANCEL Procedure 主键（BR-IP-CXL-010）。 */
    @Schema(description = "CANCEL 执行记录主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long procedureId;

    /**
     * 取消执行人 / 退药人主键（CANCEL Procedure {@code recorder_id}）。
     */
    @Schema(description = "取消执行人主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cancelOperatorId;

    /**
     * 取消执行人 / 退药人姓名。
     */
    @Schema(description = "取消执行人姓名")
    private String cancelOperatorName;

    /**
     * 取消执行时间点时刻（CANCEL Procedure {@code occurrence_time}，继承原 COMPLETED 计划点）。
     */
    @Schema(description = "取消执行时间点时刻")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime cancelOccurrenceTime;

    /**
     * 退药总价（原 COMPLETED 槽位执行账单 total_price 绝对值之和；含取消后已软删行）。
     */
    @Schema(description = "退药总价")
    private BigDecimal totalPrice;
}
