package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateReportDefinitionRequest {

    @NotNull
    private Long id;

    private String name;

    private String remark;
}
