package com.openhis.whale.report.application.layout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LayoutBoxDelta {

    @JsonProperty("dxMm")
    private Float dxMm = 0f;
    @JsonProperty("dyMm")
    private Float dyMm = 0f;
}
