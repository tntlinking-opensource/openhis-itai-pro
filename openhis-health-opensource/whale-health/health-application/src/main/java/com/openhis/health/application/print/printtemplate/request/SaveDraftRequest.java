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

package com.openhis.health.application.print.printtemplate.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 保存草稿请求（IF-BAS-PRT-14）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存画布打印模板草稿请求")
public class SaveDraftRequest {

  /** 已有草稿主键；与 busNo 至少提供一个。 */
  @Schema(description = "已有草稿主键", example = "1234567890123456789")
  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  /** 模板业务键；新建草稿时使用。 */
  @Schema(description = "模板业务键", example = "PT20260101001")
  private String busNo;

  /** 业务场景码。 */
  @Schema(description = "业务场景码", example = "outpatient.rx.print")
  private String scenarioCode;

  /** 模板展示名称。 */
  @Schema(description = "模板展示名称", example = "门诊处方 A5")
  private String name;

  /** hiprint 版式 JSON 对象或字符串。 */
  @Schema(description = "hiprint 版式 JSON")
  @JsonProperty("PrintJson")
  private Object printJson;

  /** 示例打印数据 JSON 对象或字符串。 */
  @Schema(description = "示例打印数据 JSON")
  private Object samplePrintData;
}
