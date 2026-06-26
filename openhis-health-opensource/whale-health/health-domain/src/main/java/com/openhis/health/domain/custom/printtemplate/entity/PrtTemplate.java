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

package com.openhis.health.domain.custom.printtemplate.entity;

import com.openhis.health.domain.custom.printtemplate.constants.PrintTemplateStatus;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * 画布打印模板聚合根（PrintTemplate）。
 */
@Getter
@Setter
@Entity
@Table(name = "prt_template")
public class PrtTemplate extends BaseAuditedEntity implements IMultiTenant {

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "bus_no", nullable = false, length = 64)
  private String busNo;

  /** 业务场景码（如 outpatient.rx.print）；published 行用于运行时按场景解析。 */
  @Column(name = "scenario_code", length = 64)
  private String scenarioCode;

  @Column(name = "name", nullable = false, length = 128)
  private String name;

  @Column(name = "status", nullable = false, length = 16)
  private String status;

  /** 模板业务版本（非乐观锁列）。 */
  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "print_json", nullable = false, columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String printJson;

  @Column(name = "schema_version", nullable = false)
  private Integer schemaVersion;

  @Column(name = "sample_print_data", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String samplePrintData;

  /**
   * 断言当前行可作为 publish 源（BR-PRT-004）。
   */
  public void assertDraftForPublish() {
    if (!PrintTemplateStatus.DRAFT.equals(this.status)) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "当前状态不允许发布");
    }
    if (Boolean.TRUE.equals(this.getIsDeleted())) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "已删除模板不允许发布");
    }
  }

  /**
   * 软删模板行。
   */
  public void markDeleted() {
    this.setIsDeleted(Boolean.TRUE);
  }
}
