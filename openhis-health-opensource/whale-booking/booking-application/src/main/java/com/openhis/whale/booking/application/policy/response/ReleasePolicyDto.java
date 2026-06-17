package com.openhis.whale.booking.application.policy.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReleasePolicyDto {

    private Long id;
    private Long organizationId;
    private String policyCode;
    private String policyName;
    private Integer advanceDays;
    private Integer slotMinutes;
    private Integer capacityPerSlot;
    private String channelQuotaJson;
}
