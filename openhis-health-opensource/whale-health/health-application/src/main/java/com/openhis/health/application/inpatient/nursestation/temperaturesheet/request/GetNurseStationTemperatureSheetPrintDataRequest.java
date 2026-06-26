/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.temperaturesheet.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-TMP-04：体温单打印读侧请求（与 IF-02 同读路径，按打印范围收窄日期）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体温单打印数据请求", requiredProperties = {
    "encounterId", "patientId", "windowRange", "printScope"
})
public class GetNurseStationTemperatureSheetPrintDataRequest {

    /** 当前选中就诊。 */
    @NotNull(message = "就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /** 左侧卡片患者 id。 */
    @NotNull(message = "患者标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long patientId;

    /** 窗口天数 code（打印当前页时使用）。 */
    @NotBlank(message = "窗口范围不能为空")
    @Schema(description = "窗口范围：3d/7d/14d", requiredMode = Schema.RequiredMode.REQUIRED)
    private String windowRange;

    /** 页码（0-based；printScope=CURRENT_PAGE 时使用）。 */
    @Schema(description = "页码（0-based）")
    private Integer pageIndex;

    /** 打印范围：CURRENT_PAGE / ALL。 */
    @NotBlank(message = "打印范围不能为空")
    @Schema(description = "打印范围：CURRENT_PAGE 或 ALL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String printScope;
}
