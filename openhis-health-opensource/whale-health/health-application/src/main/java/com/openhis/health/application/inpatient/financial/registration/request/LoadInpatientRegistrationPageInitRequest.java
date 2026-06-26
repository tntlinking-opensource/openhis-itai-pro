/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/** 住院登记页初始化请求（IF-IP-REG-00）。 */
@Data
@Accessors(chain = true)
@Schema(description = "住院登记页初始化请求")
public class LoadInpatientRegistrationPageInitRequest {
}
