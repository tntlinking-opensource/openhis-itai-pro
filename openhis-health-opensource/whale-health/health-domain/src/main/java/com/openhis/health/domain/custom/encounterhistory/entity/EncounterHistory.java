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

package com.openhis.health.domain.custom.encounterhistory.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊历史里程碑聚合根。
 *
 * <p>本聚合采用「追加式事实行」表达就诊过程关键里程碑；默认仅新增，不驱动 Encounter 主状态迁移。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "cus_encounter_history")
public class EncounterHistory extends BaseAuditedEntity {

    /**
     * 里程碑类型名称（过程类型业务键）。
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * 就诊标识。
     */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /**
     * 请求标识（如 ServiceRequest）。
     */
    @Column(name = "request_id")
    private Long requestId;

    /**
     * 请求表名（与 requestId 成对出现）。
     */
    @Column(name = "request_table_name")
    private String requestTableName;

    /**
     * 原科室标识。
     */
    @Column(name = "original_organization_id")
    private Long originalOrganizationId;

    /**
     * 目标科室标识。
     */
    @Column(name = "target_organization_id")
    private Long targetOrganizationId;

    /**
     * 原位置标识（病区/床位等）。
     */
    @Column(name = "original_location_id")
    private Long originalLocationId;

    /**
     * 目标位置标识（病区/床位等）。
     */
    @Column(name = "target_location_id")
    private Long targetLocationId;

    /**
     * 出院方式编码（仅离院类里程碑使用）。
     */
    @Column(name = "out_way_code")
    private String outWayCode;

    /**
     * 断言基础必填字段（BR-EHR-001/002）。
     */
    public void assertRequiredFields() {
        if (this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "就诊标识不能为空");
        }
        if (this.name == null || this.name.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "里程碑类型不能为空");
        }
    }

    /**
     * 断言请求引用成对（BR-EHR-003）。
     */
    public void assertRequestRefPair() {
        boolean requestIdPresent = this.requestId != null;
        boolean tablePresent = this.requestTableName != null && !this.requestTableName.trim().isEmpty();
        if (requestIdPresent != tablePresent) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "请求标识与请求表名必须成对出现");
        }
    }

    /**
     * 断言转科类里程碑科室变迁不全空（BR-EHR-004）。
     */
    public void assertTransferOrgTransitionNotEmpty() {
        if (this.originalOrganizationId == null && this.targetOrganizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "转科里程碑原科室与目标科室不得全空");
        }
    }

    /**
     * 断言离院类里程碑出院方式编码必填（BR-EHR-005）。
     */
    public void assertDischargeOutWayPresent() {
        if (this.outWayCode == null || this.outWayCode.trim().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_HISTORY_INVALID_ARGUMENT, "离院里程碑出院方式编码不能为空");
        }
    }

    /**
     * 标记里程碑逻辑废弃（BR-EHR-007）。
     */
    public void markRetired() {
        this.setIsDeleted(Boolean.TRUE);
    }
}
