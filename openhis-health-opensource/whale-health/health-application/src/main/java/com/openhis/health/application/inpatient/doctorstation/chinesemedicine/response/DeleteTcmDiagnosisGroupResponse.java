/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** IF-DOR-33：删除中医病证组响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "删除中医病证组响应")
public class DeleteTcmDiagnosisGroupResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
