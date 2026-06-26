/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.dailysettlement.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院日结页初始化请求（IF-IP-DCL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院日结页初始化请求")
public class LoadInpatientDailySettlementPageInitRequest {
}
