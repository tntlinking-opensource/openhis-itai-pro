/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-28：长期药品改量响应（新草稿待签发）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院医生站-长期药品改量响应")
public class ChangeLongTermMedicationDoseInpatientDoctorStationAdviceResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 提示文案。 */
    @Schema(description = "提示文案")
    private String message;

    /** 新建长期药品草稿主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "新建草稿主键")
    private Long newMedicationRequestId;

    /** 源嘱主键（已停或原已停）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源嘱主键")
    private Long sourceMedicationRequestId;

    /** 新嘱 based_on_id（= 源嘱 id）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "based_on 源嘱主键")
    private Long basedOnId;
}
