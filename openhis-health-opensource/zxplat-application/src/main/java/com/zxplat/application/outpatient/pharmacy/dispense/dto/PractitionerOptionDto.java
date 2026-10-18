/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 调配药师候选行（门诊发药 IF-OPP-01 初始化分页）。
 *
 * <p>含机构上下文字段，区别于纯 {@code value/label} 的 {@link com.zxplat.framework.core.application.dto.OptionDto} 下拉项（B.10.5 例外：工作站列表行）。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "调配药师候选行")
public class PractitionerOptionDto {

    @Schema(description = "药师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long practitionerId;

    /** 展示姓名。 */
    @Schema(description = "展示姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    /** 所属机构主键。 */
    @Schema(description = "所属机构主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orgId;

    /** 所属机构名称。 */
    @Schema(description = "所属机构名称")
    private String orgName;
}
