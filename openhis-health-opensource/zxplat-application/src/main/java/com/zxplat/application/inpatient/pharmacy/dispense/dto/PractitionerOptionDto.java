/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 调配药师候选行（住院发药 IF-IPP-01 初始化分页）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "调配药师候选行")
public class PractitionerOptionDto {

    /** 医务人员主键 {@code adm_practitioner.id}（调配药师或领药人候选）。 */
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
