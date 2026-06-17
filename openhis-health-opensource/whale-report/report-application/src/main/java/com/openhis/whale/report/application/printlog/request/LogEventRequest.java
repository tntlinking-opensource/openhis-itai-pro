package com.openhis.whale.report.application.printlog.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class LogEventRequest {

    @NotBlank
    private String eventType;

    @NotBlank
    private String targetType;

    @NotBlank
    private String targetCode;

    private Integer templateVersion;

    private String datasetCode;

    private Map<String, Object> bizRef;

    @Min(0)
    private Integer copies;

    @NotBlank
    private String status;

    private String errorCode;

    private String errorMessage;

    private String contentHash;

    private String contentType;

    private String printerName;

    private String clientJobId;

    private Integer winDurationMs;

    private Integer renderDurationMs;

    private Long parentEventId;

    private String source;
}
