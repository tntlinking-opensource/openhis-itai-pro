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

package com.openhis.health.application.print.printjob.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 打印准备请求（IF-BAS-PRT-31）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "打印准备请求")
public class PreparePrintRequest {

  /** 模板 busNo；与 scenarioCode 二选一。 */
  @Schema(description = "模板 busNo（与 scenarioCode 二选一）", example = "PT20260101001")
  private String busNo;

  /** 业务场景码；与 busNo 二选一，优先于 bindingCode。 */
  @Schema(description = "业务场景码（与 busNo 二选一）", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 兼容旧客户端：等同 scenarioCode。 */
  @Schema(description = "兼容旧客户端的场景码", example = "outpatient.rx.print")
  private String bindingCode;
}
