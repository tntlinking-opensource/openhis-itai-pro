/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.doctorstation.chinesemedicine.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** IF-DOR-38：签退或删除中药处方响应。 */
@Data
@Accessors(chain = true)
@Schema(description = "签退或删除中药处方响应")
public class WithdrawOrDeleteTcmRxResponse {

    /** 是否成功。 */
    @Schema(description = "是否成功")
    private Boolean success;
}
