/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.ordertemplate.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 组套模板维护页初始化请求（IF-BAS-OTL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "组套模板维护页初始化请求")
public class InitOrderTemplateMaintenanceRequest {
}
