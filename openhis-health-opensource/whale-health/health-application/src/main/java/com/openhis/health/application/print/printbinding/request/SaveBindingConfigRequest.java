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

package com.openhis.health.application.print.printbinding.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 保存绑定配置请求（IF-BAS-PRT-22）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "保存业务场景模板绑定配置请求")
public class SaveBindingConfigRequest {

  /** 全量 bindingCode → busNo 映射；保存时覆盖租户全部 override。 */
  @Schema(description = "全量 bindingCode→busNo 映射", requiredMode = Schema.RequiredMode.REQUIRED)
  private Map<String, String> bindings;
}
