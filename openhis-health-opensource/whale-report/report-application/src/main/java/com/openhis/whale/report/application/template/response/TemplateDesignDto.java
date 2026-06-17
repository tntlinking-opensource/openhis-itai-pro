package com.openhis.whale.report.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 设计器加载：含 layoutJson 对象（仅设计态接口，不下发到列表/试打列表）。
 */
@Getter
@Setter
public class TemplateDesignDto {

    private Long id;
    private String code;
    private String name;
    private String pipeline;
    private String datasetCode;
    private String status;
    private Integer version;
    private Long tenantId;
    private Boolean editable;
    private String source;
    private Map<String, Object> layoutJson;
}
