/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.financial.dailysettlement.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊日结页初始化请求（IF-OP-DCL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊日结页初始化请求")
public class LoadOutpatientDailySettlementPageInitRequest {
}
