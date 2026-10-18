/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.support.dto;

import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院结算费用明细行 DTO（IF-IP-STL-04/07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算费用明细行")
public class InpatientSettleChargeLineDto {

    /** 收费明细主键 {@code adm_charge_item.id}。 */
    @Schema(description = "收费明细 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long chargeItemId;

    /** 产品/项目展示名（目录读模型）。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 规格/包装展示（目录 totalVolume 等）。 */
    @Schema(description = "规格展示")
    private String specText;

    /** 费用大类 code（产品类目）。 */
    @Schema(description = "费用大类 code")
    private String categoryCode;

    /** 费用大类展示名。 */
    @Schema(description = "费用大类中文")
    private String categoryDisplay;

    /** 单价（charge_item.unit_price）。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量（charge_item.quantity_value）。 */
    @Schema(description = "数量")
    private BigDecimal quantityValue;

    /** 计量单位 code。 */
    @Schema(description = "单位")
    private String quantityUnit;

    /** 行合计（charge_item.total_price）。 */
    @Schema(description = "行合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal lineAmount;

    /** 收费状态 code（通常为 PENDING_SETTLEMENT）。 */
    @Schema(description = "收费状态 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String statusEnum;

    /** 收费状态展示文案。 */
    @Schema(description = "收费状态展示")
    private String statusEnumText;

    /** 业务发生时间（occurrence_time）。 */
    @Schema(description = "业务发生时间")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    private LocalDateTime occurrenceTime;

    /** 费用来源可读文案（generate_source 展示）。 */
    @Schema(description = "来源可读文案")
    private String sourceText;

    /** 收银台是否默认勾选本行。 */
    @Schema(description = "是否默认勾选", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean selected;

    /** 医保等级 code（术语 CHRGITM_LV，取自产品目录）。 */
    @Schema(description = "医保等级 code")
    private Integer chrgitmLv;

    /** 医保等级展示名（甲类/乙类/丙类等）。 */
    @Schema(description = "医保等级展示")
    private String chrgitmLvText;
}
