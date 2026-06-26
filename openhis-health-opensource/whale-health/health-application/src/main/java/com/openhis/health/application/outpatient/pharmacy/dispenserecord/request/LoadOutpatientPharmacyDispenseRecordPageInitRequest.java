/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.outpatient.pharmacy.dispenserecord.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊发退药记录页初始化请求（IF-OPR-01）；空体占位。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊发退药记录页初始化请求")
public class LoadOutpatientPharmacyDispenseRecordPageInitRequest {
}
