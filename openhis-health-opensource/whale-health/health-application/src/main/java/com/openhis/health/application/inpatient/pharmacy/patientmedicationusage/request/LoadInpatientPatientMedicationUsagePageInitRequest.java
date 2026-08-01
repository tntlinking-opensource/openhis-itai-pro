/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-PMU-01：住院患者药品使用页初始化请求。
 *
 * <p>无业务字段；服务端以 {@code LocalDate.now()} 计算默认日期区间。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用页初始化请求（可空体）")
public class LoadInpatientPatientMedicationUsagePageInitRequest {
}
