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
 * IF-IP-ARG-09：床位自动计费已滚费用行（供配置展开与取消执行锚点）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "自动计费已滚费用行")
public class NurseStationAutoRollPostedChargeRowDto {

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /**
     * 执行记录主键（取消执行入参；无则不可取消）。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行记录主键")
    private Long procedureId;

    /** 业务发生时间（occurrence）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "业务发生时间")
    private LocalDateTime occurrenceTime;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantityValue;

    /** 数量单位 code。 */
    @Schema(description = "数量单位")
    private String quantityUnit;

    /** 单价（行级 6 位）。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 行金额（行级 6 位）。 */
    @Schema(description = "行金额")
    private BigDecimal totalPrice;

    /** 收费状态 code（ChargeItemStatus）。 */
    @Schema(description = "收费状态 code")
    private String statusEnum;

    /** 收费状态展示文案。 */
    @Schema(description = "收费状态展示")
    private String statusDisplay;

    /**
     * 是否允许本 Tab 发起取消执行。
     *
     * <p>待结算且 procedureId 非空时为 true；已结算/退费链路不可取消。</p>
     */
    @Schema(description = "是否可取消执行")
    private Boolean cancelable;
}
