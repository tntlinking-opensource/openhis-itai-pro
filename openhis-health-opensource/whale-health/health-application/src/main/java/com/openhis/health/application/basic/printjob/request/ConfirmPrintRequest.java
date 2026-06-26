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

package com.openhis.health.application.basic.printjob.request;

import lombok.Data;

/**
 * 打印确认请求（IF-BAS-PRT-32）。
 */
@Data
public class ConfirmPrintRequest {

  private String traceId;

  private String busNo;

  private String scenarioCode;

  private Integer templateVersion;

  /** @deprecated 兼容旧客户端，等同 scenarioCode */
  private String bindingCode;

  private String status;

  private String printer;

  private Integer copies;

  private Object printData;

  private Object bizRef;

  private String clientType;

  private String errorCode;

  private String errorMessage;
}
