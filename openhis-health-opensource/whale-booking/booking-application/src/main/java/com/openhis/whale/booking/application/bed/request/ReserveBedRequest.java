package com.openhis.whale.booking.application.bed.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 床位预留请求。
 */
@Getter
@Setter
public class ReserveBedRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 床位地点主键。 */
    @NotNull
    private Long bedLocationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 拟入院日期。 */
    @NotNull
    private LocalDate intendedAdmissionDate;

    /** 备注说明（可选）。 */
    private String commentText;
}
