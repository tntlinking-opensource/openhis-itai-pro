/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.appointment;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 预约/候诊项已创建领域事件。
 *
 * <p>携带数据对齐 DM-WKF-002 §5.1：appointmentId（聚合根 id）、appointmentKind、
 * encounterId（若有）、patientId（若有）、displayOrder（若有）、busNo、operatorId。
 * AppointmentOrigin 摘要通过 originChannel 字段传递，其余来源字段由投影器按需订阅实体全量。</p>
 */
@Getter
@Setter
public class AppointmentCreatedEvent extends DomainEvent {

    /** 预约分类（OUTPATIENT_BOOKING / CHECK_IN_ONLY）。 */
    private String appointmentKind;

    /** 业务单号。 */
    private String busNo;

    /** 患者标识（门诊预约/报到必填；可空）。 */
    private Long patientId;

    /** 就诊标识（候诊队列形态必填；纯预约阶段可空）。 */
    private Long encounterId;

    /** 显示顺序（候诊队列形态必填语义）。 */
    private Integer displayOrder;

    /** 来源渠道摘要（AppointmentOrigin.originChannel）。 */
    private String originChannel;

    /** 操作人标识。 */
    private Long operatorId;

    /**
     * 默认构造器（框架反序列化用）。
     */
    public AppointmentCreatedEvent() {
        super();
    }

    /**
     * 全量载荷构造器。
     *
     * @param aggregateId     聚合根主键
     * @param appointmentKind 预约分类
     * @param busNo           业务单号
     * @param patientId       患者标识（可空）
     * @param encounterId     就诊标识（可空）
     * @param displayOrder    显示顺序（可空）
     * @param originChannel   来源渠道摘要（可空）
     * @param operatorId      操作人标识
     */
    public AppointmentCreatedEvent(Long aggregateId, String appointmentKind, String busNo,
                                   Long patientId, Long encounterId, Integer displayOrder,
                                   String originChannel, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.APPOINTMENT);
        this.appointmentKind = appointmentKind;
        this.busNo = busNo;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.displayOrder = displayOrder;
        this.originChannel = originChannel;
        this.operatorId = operatorId;
    }
}
