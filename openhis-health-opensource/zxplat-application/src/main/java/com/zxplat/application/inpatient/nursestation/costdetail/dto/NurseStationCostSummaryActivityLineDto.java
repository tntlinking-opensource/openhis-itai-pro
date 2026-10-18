/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.costdetail.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用汇总行展开明细：单次执行或划价记录（IF-IP-CDT-02，presentationMode=SUMMARY）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用汇总-执行/划价明细行")
public class NurseStationCostSummaryActivityLineDto {

    /** 收费明细主键（组内账单锚点）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /**
     * 逻辑业务时间（occurrence；组内倒序锚点）。
     * <p>页面列优先读 {@link #displayDateTime}。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "逻辑业务时间（occurrence）")
    private LocalDateTime activityDateTime;

    /**
     * 页面展示时间（与 activityDateTime 同源：recorded / 发放 / 滚费 occurrence）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "展示时间（执行/划价事实时刻）")
    private LocalDateTime displayDateTime;

    /** 操作人展示名（划价/执行跟 Procedure 主执行人或 DeviceDispense 发放·配药人；enterer_* 为收费人）。 */
    @Schema(description = "操作人展示名")
    private String operatorName;

    /** 本次账单数量（原样）。 */
    @Schema(description = "本次账单数量")
    private BigDecimal quantityValue;

    /** 账单单位 code。 */
    @Schema(description = "账单单位 code")
    private String quantityUnit;

    /** 账单单位展示名。 */
    @Schema(description = "账单单位展示名")
    private String quantityUnitDisplay;

    /**
     * 本次账单行合计（原样；退费/冲销负金额保留，与明细视图 {@code totalPrice} 同口径）。
     */
    @Schema(description = "本次账单行合计（原样；退费负金额保留）")
    private BigDecimal totalPrice;

    /** 活动类型 code：PRICING / EXECUTE（BR-IP-CDT-018）。 */
    @Schema(description = "活动类型 code")
    private String activityTypeEnum;

    /** 活动类型展示：划价 / 执行 / 取消划价 / 取消执行。 */
    @Schema(description = "活动类型展示")
    private String activityTypeDisplay;

    /** 取消执行或取消划价醒目标识（BR-IP-CDT-018）。 */
    @Schema(description = "取消类记录醒目标识")
    private Boolean cancelHighlight;
}
