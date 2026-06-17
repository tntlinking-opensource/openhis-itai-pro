package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GroupOrgDto {

    private Long id;
    private String orgCode;
    private String orgName;
    private String contactName;
    private String contactPhone;
    private String settlementMode;
}
