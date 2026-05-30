package com.openhis.whale.mrms.application.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class SaveProcedureLinesRequest {
    @NotNull
    private Long caseRecordId;
    private List<SaveHomepageRequest.ProcedureLineRequest> lines;
}
