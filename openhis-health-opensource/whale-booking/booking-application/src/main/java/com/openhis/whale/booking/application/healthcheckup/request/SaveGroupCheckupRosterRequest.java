package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检名单请求（9.5）。
 */
@Getter
@Setter
public class SaveGroupCheckupRosterRequest {

    private Long id;

    @NotNull
    private Long batchId;

    private Long patientId;

    @NotBlank
    private String fullName;

    private String idCardNo;

    private String studentNo;

    private String mobilePhone;
}
