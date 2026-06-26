/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.pharmacy.dispenserecord.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院发退药记录页初始化请求（IF-IPR-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发退药记录页初始化请求")
public class LoadInpatientPharmacyDispenseRecordPageInitRequest {
}
