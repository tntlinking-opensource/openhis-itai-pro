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

package com.openhis.health.application.inpatient.financial.settle.response;

import com.openhis.whale.core.application.dto.OptionDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 住院结算页初始化响应（IF-IP-STL-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算页初始化响应")
public class LoadInpatientSettlePageInitResponse {

    @Schema(description = "结算队列页签选项")
    private List<OptionDto> settleQueueTabOptions;

    @Schema(description = "费用呈现模式选项")
    private List<OptionDto> presentationModeOptions;

    @Schema(description = "支付渠道选项")
    private List<OptionDto> paymentChannelOptions;

    @Schema(description = "默认队列页签 code")
    private String defaultSettleQueueTab;

    @Schema(description = "默认费用呈现模式 code")
    private String defaultPresentationMode;
}
