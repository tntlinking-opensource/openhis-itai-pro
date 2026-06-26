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

package com.openhis.health.domain.custom.printtemplate.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存画布打印模板草稿命令。
 */
@Getter
@Setter
public class SaveDraftCommand {

  private Long tenantId;
  private Long organizationId;
  private String busNo;
  private String scenarioCode;
  private String name;
  private String printJson;
  private String samplePrintData;
  private Long existingId;
}
