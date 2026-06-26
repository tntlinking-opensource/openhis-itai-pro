/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.request;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * IF-IP-TMP-02：加载体温单曲线与网格请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "加载体温单数据请求", requiredProperties = {
    "encounterId", "patientId", "windowRange"
})
public class GetNurseStationTemperatureSheetDataRequest {

    /** 当前选中就诊。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者 id，须与就诊一致。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 窗口天数 code：3d/7d/14d。 */
    @NotBlank(message = "窗口范围不能为空")
    @Schema(description = "窗口范围：3d/7d/14d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String windowRange;

    /** 页码（0-based）；默认 0。 */
    @Schema(description = "页码（0-based）")
    private Integer pageIndex;

    /** 录入抽屉回显日；有值时额外返回该日 6 槽+补充项。 */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = GeneralSharedConstants.DATE_DISPLAY_PATTERN)
    @Schema(description = "录入抽屉回显日")
    private LocalDate focusRecordDate;
}
