package com.openhis.whale.booking.application.policy.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GetRegistrationPolicyRequest {

    @NotNull
    private Long organizationId;
}
