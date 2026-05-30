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

package com.openhis.health.application.emergency.doctorstation.gate.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "急诊开嘱前三项门禁响应")
public class VerifyBeforeOrderResponse {

    @Schema(description = "是否全部通过")
    private Boolean allPassed;

    @Schema(description = "挂号有效性是否通过")
    private Boolean registrationValid;

    @Schema(description = "诊断完整性是否通过")
    private Boolean diagnosisComplete;

    @Schema(description = "分诊关联是否通过")
    private Boolean triageLinked;

    @Schema(description = "挂号门禁说明")
    private String registrationMessage;

    @Schema(description = "诊断门禁说明")
    private String diagnosisMessage;

    @Schema(description = "分诊门禁说明")
    private String triageMessage;
}
