package com.openhis.whale.report.application.template.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ListTemplatesRequest {

    /** 名称 / 编码模糊匹配 */
    private String keyword;

    /** published | draft；空表示全部 */
    private String status;

    /** page | roll 等；空表示全部 */
    private String pipeline;

    /** P1 / P2 / P3；空表示全部 */
    private String priority;
}
