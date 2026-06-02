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
 * <p>对应表：wkf_service_request_detail。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "wkf_service_request_detail")
public class ServiceRequestDetail extends BaseAuditedEntity {

    /** 外键：服务申请标识。 */
    @Column(name = "service_req_id", nullable = false)
    private Long serviceReqId;

    /** 明细类型编码。 */
    @Column(name = "type_code")
    private String typeCode;

    /** 明细来源表名。 */
    @Column(name = "order_detail_table")
    private String orderDetailTable;

    /** 明细来源标识。 */
    @Column(name = "order_detail_id")
    private Long orderDetailId;

    /** 明细扩展内容。 */
    @Column(name = "detail_json")
    private String detailJson;

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
}

