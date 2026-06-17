package com.openhis.whale.report.application.binding.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolvePrintBindingRequest {

    @NotBlank
    private String bindingCode;
}
