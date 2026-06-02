package com.openhis.whale.mrms.application.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SubmitHomepageRequest {
    @NotNull
    private Long caseRecordId;
}
