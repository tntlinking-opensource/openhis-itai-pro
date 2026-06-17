package com.openhis.whale.report.application.layout.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LayoutPage {

    private String size = "A4";
    private String orientation = "portrait";
    private List<Float> marginMm;
    private String layoutMode = "flow";
    /** 热敏 roll 纸宽（mm）；pipeline=roll 时使用。 */
    private Float widthMm;
    /** page 管线自定义纸高（mm）；size=custom 时使用。 */
    private Float heightMm;
    private PageOverlayConfig overlay;
}
