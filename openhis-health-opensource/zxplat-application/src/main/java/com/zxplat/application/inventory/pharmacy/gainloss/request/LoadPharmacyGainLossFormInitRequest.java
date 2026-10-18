/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inventory.pharmacy.gainloss.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 药房损益页初始化入参（IF-PHA-GL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "损益页初始化 IF-PHA-GL-01")
public class LoadPharmacyGainLossFormInitRequest {
}
