package com.openhis.whale.mrms.application.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveDiagnosisLinesRequest {
    @NotNull
    private Long caseRecordId;
    @NotBlank
    private String diagTypeCode;
    private List<SaveHomepageRequest.DiagnosisLineRequest> lines;
}
