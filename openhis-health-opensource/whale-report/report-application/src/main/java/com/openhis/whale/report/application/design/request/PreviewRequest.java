package com.openhis.whale.report.application.design.request;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class PreviewRequest {

    private String templateCode;

    /** 元素 pack（通常等于模板的 datasetCode）；设计器 inline 预览必传，否则默认 outpatient_prescription。 */
    private String packCode;

    private Map<String, Object> params;

    private String layoutJson;

    private Boolean useSampleData;
}
