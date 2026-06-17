/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/** IF-DOR-32：全量保存中医病证组响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "全量保存中医病证组响应")
public class SaveTcmDiagnosisGroupsBatchResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;

    /** 保存后的组号列表。 */
    @Schema(description = "保存后的组号列表")
    private List<String> savedGroupKeys = new ArrayList<>();
}
