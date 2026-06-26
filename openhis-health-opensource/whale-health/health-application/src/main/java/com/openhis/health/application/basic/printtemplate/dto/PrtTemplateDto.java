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

package com.openhis.health.application.basic.printtemplate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 画布打印模板详情 DTO（设计态加载 / 保存后返回）。
 */
@Data
public class PrtTemplateDto {

  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  private String busNo;

  private String scenarioCode;

  private String name;

  private String status;

  private Integer version;

  private Integer schemaVersion;

  @JsonProperty("PrintJson")
  private Object printJson;

  private Object samplePrintData;
}
