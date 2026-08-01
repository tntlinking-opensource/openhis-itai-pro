/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.response;

import com.openhis.health.application.physicalexam.financial.charge.dto.PhysicalExamChargeQueueRowDto;
import com.openhis.whale.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 体检收费队列分页响应（IF-PE-CHG-02/03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "体检收费队列分页响应")
public class PagePhysicalExamChargeQueueResponse extends PagedResult<PhysicalExamChargeQueueRowDto> {
}
