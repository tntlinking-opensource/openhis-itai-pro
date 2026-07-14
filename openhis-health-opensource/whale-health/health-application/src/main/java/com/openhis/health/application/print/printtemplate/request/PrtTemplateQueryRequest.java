/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.print.printtemplate.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 画布打印模板分页查询请求（IF-BAS-PRT-11）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "画布打印模板分页查询请求")
public class PrtTemplateQueryRequest extends AbstractHealthPageRequest {

  /** 模板状态：draft / published / archived。 */
  @Schema(description = "模板状态：draft / published / archived", example = "published")
  private String status;
}
