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

package com.openhis.health.domain.custom.printlog.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import com.openhis.whale.core.domain.IMultiTenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 打印审计日志聚合根（PrintLog，append-only）。
 */
@Getter
@Setter
@Entity
@Table(name = "prt_print_event_log")
public class PrtPrintEventLog extends BaseAuditedEntity implements IMultiTenant {

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "trace_id", nullable = false, length = 64)
  private String traceId;

  @Column(name = "bus_no", nullable = false, length = 64)
  private String busNo;

  @Column(name = "binding_code", length = 64)
  private String bindingCode;

  @Column(name = "template_version")
  private Integer templateVersion;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  @Column(name = "printer_name", length = 128)
  private String printerName;

  @Column(name = "copies")
  private Integer copies;

  @Column(name = "content_hash", length = 64)
  private String contentHash;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message", length = 512)
  private String errorMessage;

  @Column(name = "biz_ref_json", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String bizRefJson;

  @Column(name = "client_type", length = 32)
  private String clientType;

  @Column(name = "operator_id")
  private Long operatorId;

  @Column(name = "operator_name", length = 128)
  private String operatorName;
}
