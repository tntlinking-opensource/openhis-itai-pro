/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.inventoryopening.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 下载期初导入模板请求（IF-BAS-INV-OPN-12）；可空体。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "下载期初导入模板")
public class DownloadInventoryOpeningImportTemplateRequest {
}
