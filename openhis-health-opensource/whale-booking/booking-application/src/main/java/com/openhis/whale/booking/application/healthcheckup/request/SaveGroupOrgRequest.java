package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SaveGroupOrgRequest {

    private Long id;
    private String orgCode;

    @NotBlank
    private String orgName;

    private String contactName;
    private String contactPhone;
    private String settlementMode;
}
