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

package com.openhis.health.application.emergency.emgtriage.station.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 患者非身份证证件子项（映射 {@code PatientIdentifierItemCommand}）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者证件子项")
public class PatientIdentifierItemDto {

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "标识行主键")
    private Long id;

    @Schema(
            description = "证件类型编码；急诊三无临时分诊号使用类型编码 PROVISIONAL_TRIAGE_NO（与 GeneralSharedConstants.PROVISIONAL_TRIAGE_NO_TYPE_CODE 一致）",
            requiredMode = Schema.RequiredMode.REQUIRED,
            example = "PASSPORT")
    private String typeCode;

    @Schema(description = "证件号码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String identifierNo;

    @Schema(description = "标识状态编码")
    private String stateEnum;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "有效开始")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "有效结束")
    private LocalDateTime endTime;
}
