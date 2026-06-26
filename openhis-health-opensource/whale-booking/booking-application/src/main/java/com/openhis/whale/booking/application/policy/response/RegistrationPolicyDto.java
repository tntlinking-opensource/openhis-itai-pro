package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 院点挂号策略 DTO。
 */
@Getter
@Setter
public class RegistrationPolicyDto {

    /** 主键。 */
    private Long id;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 现场挂号号源模式（如 APPOINTMENT_FIRST）。 */
    private String walkInSlotMode;

    /** 挂号时是否自动报到入队。 */
    private Boolean autoCheckInOnRegister;

    /** 退号处置策略（如 RELEASE_SLOT_ONLY / CANCEL_APPOINTMENT）。 */
    private String onCancelPolicy;

    /** 是否允许从预约修改接诊医师。 */
    private Boolean allowEditPractitionerFromAppointment;
}
