/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.dto;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 体温单曲线点（仅主表体温，IF-IP-TMP-02/04 · BR-IP-TMP-008）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单曲线点")
public class TemperatureSheetTemperaturePointDto {

    /** 生命体征主表 id {@code dia_vital_sign.id}。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "生命体征主表 id")
    private Long vitalSignId;

    /** 记录时点（主表 {@code recorded_at}）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "记录时点")
    private LocalDateTime recordedAt;

    /** 体温值（主表 temperature）。 */
    @Schema(description = "体温值")
    private String temperature;
}
