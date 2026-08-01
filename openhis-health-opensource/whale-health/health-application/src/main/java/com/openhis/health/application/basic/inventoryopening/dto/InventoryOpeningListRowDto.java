/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存期初列表聚合行（IF-BAS-INV-OPN-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库存期初列表行")
public class InventoryOpeningListRowDto {

    /** 业务单号。 */
    @Schema(description = "单号")
    private String busNo;

    /** OPENING 供应类型码。 */
    @Schema(description = "单据类型")
    private String typeEnum;

    /** 目标库房主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "库房主键")
    private Long locationId;

    /** 库房名称。 */
    @Schema(description = "库房名称")
    private String locationName;

    /** 供应状态码。 */
    @Schema(description = "状态码")
    private String statusEnum;

    /** 状态展示文案。 */
    @Schema(description = "状态展示")
    private String statusDisplay;

    /** 制单人姓名。 */
    @Schema(description = "制单人")
    private String applicantName;

    /** 审批人姓名。 */
    @Schema(description = "审批人")
    private String approverName;

    /** 申请时间。 */
    @Schema(description = "申请时间")
    private LocalDateTime applyTime;

    /** 审批时间。 */
    @Schema(description = "审批时间")
    private LocalDateTime approvalTime;

    /** 明细行数。 */
    @Schema(description = "行数")
    private Integer lineCount;

    /** 数量汇总（可选）。 */
    @Schema(description = "数量汇总")
    private BigDecimal totalQuantity;

    /** 进价总额（行 totalPrice 合计；缺省时按拆零量×包装进价重算）。 */
    @Schema(description = "进价总额")
    private BigDecimal totalPrice;

    /** 是否同库当前唯一 AGREE 有效单。 */
    @Schema(description = "当前有效")
    private Boolean currentEffective;
}
