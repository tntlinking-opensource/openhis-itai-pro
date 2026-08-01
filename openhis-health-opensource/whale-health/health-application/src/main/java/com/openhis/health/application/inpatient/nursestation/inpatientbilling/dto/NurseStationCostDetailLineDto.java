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
 * 费用明细行 DTO（IF-IP-CDT-02，presentationMode=DETAIL）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细行")
public class NurseStationCostDetailLineDto {

    /** 收费明细主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "收费明细主键")
    private Long chargeItemId;

    /** 展示用产品 id（组套子项取父项 product_id，BR-IP-CDT-006）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "展示用产品 id")
    private Long productId;

    /** 展示用产品表名。 */
    @Schema(description = "展示用产品表名")
    private String productTableName;

    /** 项目名称。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String itemSpec;

    /** 账单数量（原样）。 */
    @Schema(description = "账单数量")
    private BigDecimal quantityValue;

    /** 账单单位 code。 */
    @Schema(description = "账单单位 code")
    private String quantityUnit;

    /** 账单单位展示名。 */
    @Schema(description = "账单单位展示名")
    private String quantityUnitDisplay;

    /** 账单单价（原样，BR-IP-CDT-012）。 */
    @Schema(description = "账单单价")
    private BigDecimal unitPrice;

    /** 账单行合计（原样；退费负金额保留）。 */
    @Schema(description = "账单行合计")
    private BigDecimal totalPrice;

    /**
     * 逻辑业务时间（划价/滚费 = 账单 occurrence；医嘱执行 = Procedure.occurrence_time）。
     * <p>日期过滤与倒序锚本字段；页面「业务时间」列优先读 {@link #displayDateTime}。</p>
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "逻辑业务时间（occurrence）")
    private LocalDateTime businessDateTime;

    /**
     * 页面展示时间（医嘱执行 = Procedure.recorded_time；划价/滚费 = 同 businessDateTime）。
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "展示时间（执行/划价事实时刻）")
    private LocalDateTime displayDateTime;

    /** 费用状态 code。 */
    @Schema(description = "费用状态 code")
    private String statusEnum;

    /** 费用状态展示名。 */
    @Schema(description = "费用状态展示名")
    private String statusDisplay;

    /** 生成来源 code。 */
    @Schema(description = "生成来源 code")
    private String generateSourceEnum;

    /** 生成来源展示名。 */
    @Schema(description = "生成来源展示名")
    private String generateSourceDisplay;

    /** 退费醒目标识（REFUNDING/REFUNDED 为 true，BR-IP-CDT-013）。 */
    @Schema(description = "退费醒目标识")
    private Boolean refundHighlight;

    /** 上游业务表（费用大类映射源）。 */
    @Schema(description = "上游业务表")
    private String serviceTable;

    /** 费用大类块 code：MEDICATION/SERVICE/DEVICE。 */
    @Schema(description = "费用大类块 code")
    private String expenseBlockCode;
}
