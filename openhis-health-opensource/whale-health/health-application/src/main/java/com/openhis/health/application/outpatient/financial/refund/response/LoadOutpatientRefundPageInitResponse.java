/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.outpatient.financial.refund.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 门诊退费页初始化响应（IF-REF-01）。
 */
@Data
@Accessors(chain = true)
/** 门诊退费页初始化响应。 */
@Schema(description = "门诊退费页初始化响应")
public class LoadOutpatientRefundPageInitResponse {

    @Schema(description = "收费项状态筛选项")
    /** 收费项状态筛选项（业务字段）。 */
    private List<OptionDto> chargeItemStatusOptions;
}
