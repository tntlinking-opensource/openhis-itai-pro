/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.prepay.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 住院预交金页初始化请求（IF-IP-PREP-00）。 */
@Data
@Accessors(chain = true)
@Schema(description = "住院预交金页初始化请求")
public class LoadInpatientPrepayPageInitRequest {
}
