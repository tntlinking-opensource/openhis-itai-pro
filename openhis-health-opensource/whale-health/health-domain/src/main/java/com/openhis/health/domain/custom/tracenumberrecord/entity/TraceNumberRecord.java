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

package com.openhis.health.domain.custom.tracenumberrecord.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.TraceDirection;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

/**
 * 追溯码记录聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cus_trace_number_record")
public class TraceNumberRecord extends BaseAuditedEntity {

    @Column(name = "item_table", nullable = false)
    private String itemTable;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "location_type_enum", nullable = false)
    private String locationTypeEnum;

    @Column(name = "location_id", nullable = false)
    private Long locationId;

    @Column(name = "location_store_id")
    private Long locationStoreId;

    @Column(name = "lot_number", nullable = false)
    private String lotNumber;

    @Column(name = "trace_no", nullable = false, length = 50000)
    private String traceNo;

    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    @Column(name = "unit_code")
    private String unitCode;

    /** 业务操作类型：持久化为 {@link com.openhis.health.domain.share.enums.TraceNumberRecordOperationType#getCode()} 字符串。 */
    @Column(name = "operation_type", nullable = false, length = 64)
    private String operationType;

    @Column(name = "bus_no")
    private String busNo;

    /**
     * 判断当前记录是否为入库方向（TraceDirection.IN），用于多向统计中区分入/出库条目。
     *
     * @return true 表示入库，false 表示出库或其他方向
     */
    public boolean isInbound() {
        return TraceDirection.IN.getCode().equals(this.statusEnum);
    }

    /**
     * 断言记录必填字段。
     */
    public void assertRequiredFields() {
        if (StringUtils.isBlank(this.itemTable)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "项目表类型不能为空");
        }
        if (this.itemId == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "项目标识不能为空");
        }
        if (StringUtils.isBlank(this.locationTypeEnum)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "位置类型不能为空");
        }
        if (this.locationId == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "仓库标识不能为空");
        }
        if (StringUtils.isBlank(this.lotNumber)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "批号不能为空");
        }
        if (StringUtils.isBlank(this.traceNo)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "追溯码不能为空");
        }
        if (this.statusEnum == null) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "轨迹方向不能为空");
        }
        if (StringUtils.isBlank(this.operationType)) {
            throw new BusinessException(ErrorCodeConstants.TRACE_NUMBER_RECORD_INVALID_ARGUMENT, "操作类型不能为空");
        }
    }
}

