package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateReportDefinitionRequest {

    @NotBlank
    private String reportCode;

    @NotBlank
    private String name;

    private String remark;
}
