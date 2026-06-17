package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检单位请求（9.3）。
 */
@Getter
@Setter
public class SaveGroupCheckupOrgRequest {

    private Long id;

    private String orgCode;

    @NotBlank
    private String orgName;

    private String contactName;

    private String contactPhone;

    private String settlementMode;

    private String contractYear;
}
