package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveReleasePolicyRequest {

    private Long id;

    private Long organizationId;

    @NotBlank
    private String policyCode;

    @NotBlank
    private String policyName;

    @NotNull
    private Integer advanceDays;

    @NotNull
    private Integer slotMinutes;

    @NotNull
    private Integer capacityPerSlot;

    private String channelQuotaJson;
}
