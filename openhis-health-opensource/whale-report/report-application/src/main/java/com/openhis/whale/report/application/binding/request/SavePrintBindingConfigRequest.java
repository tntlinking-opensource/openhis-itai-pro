package com.openhis.whale.report.application.binding.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class SavePrintBindingConfigRequest {

    @NotEmpty
    private Map<String, String> bindings = new LinkedHashMap<>();
}
