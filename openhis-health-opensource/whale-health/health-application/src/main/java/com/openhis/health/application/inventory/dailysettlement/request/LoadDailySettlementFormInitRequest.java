/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 库房日结页初始化入参（IF-INV-SET-01）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房日结页初始化 IF-INV-SET-01")
public class LoadDailySettlementFormInitRequest {
}
