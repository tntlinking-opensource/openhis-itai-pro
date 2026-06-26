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

package com.openhis.health.application.basic.printlog.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 打印审计日汇总行 DTO（IF-BAS-PRT-02）。
 */
@Data
public class PrintDailySummaryItemDto {

  private LocalDate day;

  private String busNo;

  private Integer successCount;

  private Integer failedCount;
}
