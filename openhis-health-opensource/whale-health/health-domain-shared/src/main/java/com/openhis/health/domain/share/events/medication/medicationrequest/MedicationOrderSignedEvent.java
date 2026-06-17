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

package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品医嘱已签发领域事件（DM-MED-003 §5.1.4）。
 *
 * <p>最小载荷含处方号及 save 后 {@code status_enum}（通常为 {@code RequestStatus.ACTIVE}），
 * 供 {@code OrderPanoramaProjector#onMedicationOrderSigned} 写入 OrderInfo 待校对队列。</p>
 */
@Getter
@Setter
public class MedicationOrderSignedEvent extends DomainEvent {

    /** 处方号。 */
    private String prescriptionNo;

    /** 签发后状态（RequestStatus#getCode()，通常为 ACTIVE）。 */
    private String statusEnum;

    /** 操作人用户主键。 */
    private Long operatorId;

    /**
     * 默认构造函数（反序列化用）。
     */
    public MedicationOrderSignedEvent() {
        super();
    }

    /**
     * 构造药品签发事件（兼容旧调用；statusEnum 为空时投影器不覆盖状态列）。
     *
     * @param id             药品申请主键（aggregateId）
     * @param prescriptionNo 处方号
     * @param operatorId     操作人用户主键
     */
    public MedicationOrderSignedEvent(Long id, String prescriptionNo, Long operatorId) {
        this(id, prescriptionNo, null, operatorId);
    }

    /**
     * 构造药品签发事件（含签发后状态，供 OrderInfo 读模型投影）。
     *
     * @param id             药品申请主键（aggregateId）
     * @param prescriptionNo 处方号
     * @param statusEnum     签发后状态码（save 后实体 status_enum）
     * @param operatorId     操作人用户主键
     */
    public MedicationOrderSignedEvent(
        Long id, String prescriptionNo, String statusEnum, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.prescriptionNo = prescriptionNo;
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
