/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.inpatient.nursestation.nursingrecord.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-NRC-01：护理记录 Tab 页初始化请求（无业务字段）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "护理记录页初始化请求")
public class LoadNurseStationNursingRecordInitRequest {
}
