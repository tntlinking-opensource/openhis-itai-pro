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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 画布打印模板列表行 DTO（按 busNo 聚合后的逻辑模板视图）。
 */
@Data
public class PrtTemplateListItemDto {

  @JsonSerialize(using = ToStringSerializer.class)
  private Long id;

  private String busNo;

  private String scenarioCode;

  private String name;

  /** 展示态：draft / published / archived */
  private String status;

  /** 当前生效发布版本；无 published 时为 null */
  private Integer version;

  private Integer schemaVersion;

  private Boolean hasDraft;

  @JsonSerialize(using = ToStringSerializer.class)
  private Long draftId;

  @JsonSerialize(using = ToStringSerializer.class)
  private Long publishedId;

  /** 当前租户是否可删除（出厂平台模板为 false） */
  private Boolean deletable;
}
