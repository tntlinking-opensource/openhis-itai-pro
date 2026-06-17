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

package com.openhis.health.application.inpatient.pharmacy.returnmedicine.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * 住院退药患者队列行 DTO（IF-IPR-02）。
 */
@Data
@Accessors(chain = true)
/** 住院退药患者队列行。 */
@Schema(description = "住院退药患者队列行")
public class InpatientReturnPatientRowDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "就诊标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别 code。 */
    @Schema(description = "性别 code")
    private String genderEnum;

    /** 性别展示。 */
    @Schema(description = "性别展示")
    private String genderEnumText;

    /** 年龄文本。 */
    @Schema(description = "年龄文本")
    private String age;

    /** 身份证（脱敏）。 */
    @Schema(description = "身份证（脱敏）")
    private String idCard;

    /** 病区名称。 */
    @Schema(description = "病区名称")
    private String wardName;

    /** 床位名称。 */
    @Schema(description = "床位名称")
    private String bedName;

    /** 住院号（encounterBusNo）。 */
    @Schema(description = "住院号")
    private String encounterNo;

    /** 聚合退药态 code：PENDING_REFUND / REFUNDED。 */
    @Schema(description = "聚合退药态 code")
    private String refundEnum;

    /** 聚合退药态展示。 */
    @Schema(description = "聚合退药态展示")
    private String refundEnumText;
}
