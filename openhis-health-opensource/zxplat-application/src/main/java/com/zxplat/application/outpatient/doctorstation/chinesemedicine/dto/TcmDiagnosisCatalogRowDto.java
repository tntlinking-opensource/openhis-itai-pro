/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.chinesemedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-31：中医病名/证候目录分页行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "中医病名或证候目录行")
public class TcmDiagnosisCatalogRowDto {

    /** 诊断定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义主键")
    private Long definitionId;

    /** 诊断编码。 */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /** 诊断名称。 */
    @Schema(description = "诊断名称")
    private String conditionName;

    /** 目录来源编码。 */
    @Schema(description = "目录来源编码")
    private String sourceCode;

    /** 目录来源展示文案。 */
    @Schema(description = "目录来源展示文案")
    private String sourceText;

    /**
     * 医保诊断编码（目录对码 {@code yb_no}）；未对码时为空，前端展示为「-」。
     */
    @Schema(description = "医保诊断编码；未对码为空")
    private String ybNo;
}
