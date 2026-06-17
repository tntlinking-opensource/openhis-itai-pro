package com.openhis.whale.report.application.binding.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PrintBindingTemplateOptionDto {

    private String code;
    private String name;
    private String pipeline;
    private String layoutMode;
    private String status;
    private String source;
}
