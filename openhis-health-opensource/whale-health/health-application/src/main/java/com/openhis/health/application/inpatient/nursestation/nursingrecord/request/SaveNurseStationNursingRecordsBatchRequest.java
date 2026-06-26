/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-NRC-04：纸质「保存全部」批量保存请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "批量保存护理记录请求", requiredProperties = {
    "encounterId", "patientId", "records"
})
public class SaveNurseStationNursingRecordsBatchRequest {

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

    /** 待保存行（含 rowType）。 */
    @NotEmpty(message = "待保存记录不能为空")
    @Schema(description = "待保存行列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SaveNursingRecordItemRequest> records;
}
