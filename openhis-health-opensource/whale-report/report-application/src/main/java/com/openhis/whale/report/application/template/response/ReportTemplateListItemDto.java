package com.openhis.whale.report.application.template.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 打印模板列表项（不含 layoutJson）。
 */
@Getter
@Setter
public class ReportTemplateListItemDto {

    private Long id;
    private String code;
    private String name;
    private String pipeline;
    private String layoutMode;
    private String pageSize;
    private String orientation;
    private String datasetCode;
    private String status;
    private Integer version;
    /** platform | tenant */
    private String source;
    private Long tenantId;
    private LocalDateTime updatedAt;
    /** 02-打印清单 序号，如 OP-11 */
    private String bizSeq;
    /** P1 / P2 / P3 */
    private String priority;
}
