package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建个检预约请求（9.7）。
 */
@Getter
@Setter
public class CreateIndividualHealthCheckupBookingRequest {

    /** 机构/科室主键。 */
    @NotNull
    private Long organizationId;

    /** 患者主键。 */
    @NotNull
    private Long patientId;

    /** 体检套餐主键。 */
    @NotNull
    private Long packageId;

    /** 号源主键。 */
    @NotNull
    private Long slotId;

    /** 预约来源渠道。 */
    private String originChannel;
}
