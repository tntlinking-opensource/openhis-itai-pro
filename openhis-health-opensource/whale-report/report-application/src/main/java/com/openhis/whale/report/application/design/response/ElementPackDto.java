package com.openhis.whale.report.application.design.response;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ElementPackDto {

    private String packCode;
    private String pipeline;
    private List<PrintElementDefDto> elements;
    private Map<String, Object> sampleData;
}
