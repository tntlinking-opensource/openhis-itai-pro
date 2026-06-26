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

package com.openhis.health.application.basic.printbinding.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 绑定配置加载响应（IF-BAS-PRT-21）。
 */
@Data
public class BindingConfigDto {

  private List<BindingItemDto> bindings = new ArrayList<>();

  private List<TemplateCandidateDto> candidates = new ArrayList<>();
}
