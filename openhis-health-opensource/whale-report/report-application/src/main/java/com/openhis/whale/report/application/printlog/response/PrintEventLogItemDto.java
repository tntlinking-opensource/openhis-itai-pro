package com.openhis.whale.report.application.printlog.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 打印事件明细行（列表/详情查询出参）。
 */
@Getter
@Setter
public class PrintEventLogItemDto {

    /** 事件主键 */
    private Long id;
    /** 事件类型，如 PRINT / PREVIEW */
    private String eventType;
    /** 打印目标类型，如 TEMPLATE / REPORT */
    private String targetType;
    /** 目标编码，如 charge_receipt */
    private String targetCode;
    /** 模板版本号（可空） */
    private Integer templateVersion;
    /** 数据集 pack 编码 */
    private String datasetCode;
    /** 业务关联键值（结构化，禁止整段 JSON 字符串透出） */
    private Map<String, Object> bizRef;
    /** 操作人用户主键 */
    private Long operatorId;
    /** 操作人姓名 */
    private String operatorName;
    /** 操作人所属机构 */
    private Long organizationId;
    /** 打印份数 */
    private Integer copies;
    /** 结果状态 SUCCESS / FAILED */
    private String status;
    /** 失败错误码 */
    private String errorCode;
    /** 失败错误信息 */
    private String errorMessage;
    /** 内容哈希（PDF 等） */
    private String contentHash;
    /** 内容 MIME */
    private String contentType;
    /** 打印机名称 */
    private String printerName;
    /** 客户端任务 id */
    private String clientJobId;
    /** 窗口耗时 ms */
    private Integer winDurationMs;
    /** 渲染耗时 ms */
    private Integer renderDurationMs;
    /** 来源 WEB / CLIENT 等 */
    private String source;
    /** 父事件 id（重打链路） */
    private Long parentEventId;
    /** 创建时间 */
    private LocalDateTime createdAt;
}
