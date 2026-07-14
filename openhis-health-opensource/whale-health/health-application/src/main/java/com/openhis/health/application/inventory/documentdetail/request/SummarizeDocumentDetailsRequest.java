/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.documentdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 单据详情合计入参（IF-INV-DDC-{域}-03）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "单据详情合计 IF-INV-DDC-03")
public class SummarizeDocumentDetailsRequest extends DocumentDetailFilterRequest {
}
