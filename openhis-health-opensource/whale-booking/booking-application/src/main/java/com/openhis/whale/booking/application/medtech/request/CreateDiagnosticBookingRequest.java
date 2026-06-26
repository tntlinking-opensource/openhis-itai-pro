package com.openhis.whale.booking.application.medtech.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建医技预约请求。
 */
@Getter
@Setter
public class CreateDiagnosticBookingRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 医技医嘱主键。 */
    @NotNull
    private Long serviceRequestId;

    /** 号源主键。 */
    @NotNull
    private Long slotId;

    /** 就诊主键（可选）。 */
    private Long encounterId;

    /** 预约来源渠道（可选）。 */
    private String originChannel;
}
