/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.finance.priceadjustment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 新鲜窗口内未签发医嘱草稿提示行（IF-PRC-08/09/10）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "新鲜未签发草稿提示")
public class FreshUnsignedDraftTipDto {

    /** 医嘱申请主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "医嘱申请主键")
    private Long requestId;

    /** 项目表名。 */
    @Schema(description = "项目表名")
    private String itemTable;

    /** 项目定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "项目定义主键")
    private Long itemId;

    /** 项目名称（药品/耗材定义名）。 */
    @Schema(description = "项目名称")
    private String itemName;

    /** 开立医生主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "开立医生主键")
    private Long doctorId;

    /** 开立医生姓名。 */
    @Schema(description = "开立医生姓名")
    private String doctorName;

    /** 患者主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键")
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 有效开立时刻。 */
    @Schema(description = "开立时刻")
    private LocalDateTime openedAt;
}
