/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 病区病房树查询请求（IF-IP-NUR-01）；登录者从会话解析，无业务字段。
 */
@Data
@Accessors(chain = true)
@Schema(description = "病区病房树查询请求")
public class LoadWardTreeRequest {
}
