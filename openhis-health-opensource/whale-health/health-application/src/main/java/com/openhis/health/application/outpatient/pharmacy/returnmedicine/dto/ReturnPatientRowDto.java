/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 退药患者队列行（IF-PHA-02-02）。
 */
@Data
/** 退药患者队列行。 */
@Schema(description = "退药患者队列行")
public class ReturnPatientRowDto {

    @Schema(description = "就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    @Schema(description = "患者姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String patientName;

    /** 性别编码。 */
    @Schema(description = "性别编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String genderEnum;

    /** 性别展示。 */
    @Schema(description = "性别展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String genderEnumText;

    /** 年龄展示。 */
    @Schema(description = "年龄展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String age;

    /** 身份证号。 */
    @Schema(description = "身份证号（脱敏）")
    private String idCard;

    /** 就诊科室名称。 */
    @Schema(description = "就诊科室名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String departmentName;

    /** 退药状态编码。 */
    @Schema(description = "退药状态编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnum;

    /** 退药状态展示。 */
    @Schema(description = "退药状态展示", requiredMode = Schema.RequiredMode.REQUIRED)
    private String refundEnumText;

    /** 就诊号。 */
    @Schema(description = "就诊号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encounterNo;

    /** 就诊日期/接诊时间。 */
    @Schema(description = "就诊日期/接诊时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private String receptionTime;
}
