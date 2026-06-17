package com.openhis.whale.report.application.design.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ValidateLayoutRequest {

    private String packCode;
    private String layoutJson;
    private Boolean forPublish;
}
