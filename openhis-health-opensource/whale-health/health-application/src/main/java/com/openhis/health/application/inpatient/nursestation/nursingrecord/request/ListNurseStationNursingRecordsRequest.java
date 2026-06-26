/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-NRC-02：按就诊与日期范围列表护理记录请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "列表护理记录请求", requiredProperties = {"encounterId", "patientId"})
public class ListNurseStationNursingRecordsRequest {

    /** 当前选中就诊。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者 id（可见性 Gate 用）。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 记录日期下界（含）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "记录日期下界")
    private LocalDate recordedAtFrom;

    /** 记录日期上界（含）。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "记录日期上界")
    private LocalDate recordedAtTo;

    /** 是否升序；默认 false 为时间轴降序（BR-IP-NRC-010）。 */
    @Schema(description = "是否升序排序")
    private Boolean sortAsc;
}
