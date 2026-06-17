package com.openhis.whale.report.application.layout.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class PageOverlayConfig {

    private List<Float> printOffsetMm;
    private String guideImageUrl;
}
