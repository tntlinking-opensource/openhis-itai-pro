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

package com.openhis.health.domain.workflow.servicerequest.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 服务申请明细子实体。
 *
 * <p>对应表：wkf_service_request_detail。每行通过 {@code target_table + target_id} 指向一条主数据
 *（如身体结构、标本定义）；同一 {@code service_req_id} 下可有多行，每行仅一个目标引用。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_service_request_detail")
public class ServiceRequestDetail extends BaseAuditedEntity {

    /** 外键：服务申请标识。 */
    @Column(name = "service_req_id", nullable = false)
    private Long serviceReqId;

    /** 明细目标表名（如 {@code dia_body_structure}、{@code adm_specimen_definition}）。 */
    @Column(name = "target_table")
    private String targetTable;

    /** 明细目标表主键。 */
    @Column(name = "target_id")
    private Long targetId;

    /** 明细备注。 */
    @Column(name = "remark")
    private String remark;

    /**
     * 明细归属断言（BR-SRQ-011）。
     *
     * @throws BusinessException 参数非法
     */
    public void assertBelongsToServiceRequest() {
        if (this.serviceReqId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请明细 serviceReqId 不能为空");
        }
    }

    /**
     * 明细目标引用断言（BR-SRQ-DTL-001）。
     *
     * @throws BusinessException 目标表或目标主键非法
     */
    public void assertTargetValid() {
        if (this.targetTable == null || this.targetTable.isBlank()) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请明细 targetTable 不能为空");
        }
        if (this.targetId == null) {
            throw new BusinessException(ErrorCodeConstants.SERVICE_REQUEST_INVALID_ARGUMENT, "服务申请明细 targetId 不能为空");
        }
    }

    /**
     * 持久化前完整断言：归属 + 目标引用。
     *
     * @throws BusinessException 参数非法
     */
    public void assertPersistable() {
        this.assertBelongsToServiceRequest();
        this.assertTargetValid();
    }
}
