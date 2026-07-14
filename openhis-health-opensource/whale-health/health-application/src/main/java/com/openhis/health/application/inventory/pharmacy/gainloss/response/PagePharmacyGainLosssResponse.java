/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.pharmacy.gainloss.response;

import com.openhis.health.application.inventory.pharmacy.gainloss.dto.PharmacyGainLossListRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.EqualsAndHashCode;

/**
 * 损益单分页列表出参（IF-PHA-GL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "损益单分页列表 IF-PHA-GL-02")
public class PagePharmacyGainLosssResponse extends PagedResult<PharmacyGainLossListRowDto> {
}
