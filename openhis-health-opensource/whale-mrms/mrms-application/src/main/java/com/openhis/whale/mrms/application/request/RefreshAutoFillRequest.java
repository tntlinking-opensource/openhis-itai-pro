package com.openhis.whale.mrms.application.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RefreshAutoFillRequest {
    @NotNull
    private Long caseRecordId;
    @NotEmpty
    private List<String> sections;
    private boolean forceOverwrite;
}
