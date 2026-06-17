package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位 DTO。
 */
@Getter
@Setter
public class GroupCheckupOrgDto {

    private Long id;

    private String orgCode;

    private String orgName;

    private String contactName;

    private String contactPhone;

    private String settlementMode;

    private String contractYear;
}
