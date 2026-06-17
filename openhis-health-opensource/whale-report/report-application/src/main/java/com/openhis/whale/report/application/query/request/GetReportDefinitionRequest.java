package com.openhis.whale.report.application.query.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetReportDefinitionRequest {

    @NotNull
    private Long id;
}
