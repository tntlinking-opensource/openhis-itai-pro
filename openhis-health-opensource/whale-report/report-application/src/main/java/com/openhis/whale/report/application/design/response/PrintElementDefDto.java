package com.openhis.whale.report.application.design.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class PrintElementDefDto {

    private String elementCode;
    private String label;
    private String renderType;
    private String bindPath;
    private String layoutMode;
    private Boolean required;
    private String defaultLabel;
    private Map<String, Object> defaultStyle;
    private List<Map<String, Object>> kvItems;
    private List<Map<String, Object>> columns;
    private Map<String, Object> defaultBox;
}
