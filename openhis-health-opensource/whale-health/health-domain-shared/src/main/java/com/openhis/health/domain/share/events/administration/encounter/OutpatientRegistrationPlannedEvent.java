/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 门诊就诊已计划事件。
 *
 * <p>除构造器必填维度外，可携带患者/科室/号源/挂号员等<strong>展示快照</strong>，
 * 供 {@code EncounterLifecycleProjector} 等读模型投影器通过 getter 或约定属性名物化列表行。</p>
 */
@Getter
@Setter
public class OutpatientRegistrationPlannedEvent extends DomainEvent {

    /** 患者标识。 */
    private Long patientId;
    /** 号源类型标识。 */
    private Long serviceTypeId;
    /** 机构科室标识。 */
    private Long organizationId;
    /** 就诊业务号。 */
    private String busNo;

    /** 患者院内业务号（病历号）。 */
    private String patientBusNo;
    /** 患者姓名快照。 */
    private String patientName;
    /** 患者姓名拼音首字母检索码快照。 */
    private String pyStr;
    /** 患者姓名五笔首字母检索码快照。 */
    private String wbStr;
    /** 性别编码（与 AdministrativeGender 等一致）。 */
    private String genderEnum;
    /** 性别展示文案。 */
    private String genderEnumText;
    /** 联系电话。 */
    private String phone;
    /** 出生日期。 */
    private LocalDate birthDate;
    /** 年龄展示（如「35岁」），挂号单投影用。 */
    private String patientAgeText;
    /** 身份证号快照（门诊信息读模型投影用）。 */
    private String idCard;
    /** 挂号科室名称快照。 */
    private String organizationName;
    /** 号源类型名称快照。 */
    private String serviceTypeName;
    /** 号序/开始时间（通常取挂号发生时刻）。 */
    private LocalDateTime startTime;
    /** 展示排序。 */
    private Integer displayOrder;
    /** 挂号员主键。 */
    private Long registrarId;
    /** 挂号员姓名。 */
    private String registrarName;
    /**
     * 挂号时指定的接诊医师主键快照；未指定时为 {@code null}，列表医生姓名待正式接诊事件回填。
     */
    private Long registrationDoctorId;
    /**
     * 挂号时指定的接诊医师姓名快照；与 {@link #registrationDoctorId} 成对出现，供列表与挂号单投影展示。
     */
    private String registrationDoctorName;
    /** 就诊类别编码（门诊信息投影用）。 */
    private String classEnum;
    /**
     * 就诊状态枚举
     */
    private String statusEnum;
    /**
     * 挂号单号（由写模型生成后携带，投影器直接赋值，无需自行拼接）。
     * 向后兼容：可通过 setter 注入；旧构造器调用不受影响。
     */
    private String slipNo;

    /** 无参构造器（反序列化用）。 */
    public OutpatientRegistrationPlannedEvent() {
        super();
    }

    /**
     * 向后兼容构造器；slipNo 通过 setter 注入。
     *
     * @param encounterId  就诊标识（聚合根 ID）
     * @param patientId    患者标识
     * @param serviceTypeId 号源类型标识
     * @param organizationId 机构科室标识
     * @param busNo        就诊业务号
     */
    public OutpatientRegistrationPlannedEvent(Long encounterId, Long patientId, Long serviceTypeId,
                                              Long organizationId, String busNo) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
        this.serviceTypeId = serviceTypeId;
        this.organizationId = organizationId;
        this.busNo = busNo;
    }
}
