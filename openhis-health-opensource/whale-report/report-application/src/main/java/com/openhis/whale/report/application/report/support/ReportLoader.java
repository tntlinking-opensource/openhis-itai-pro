package com.openhis.whale.report.application.report.support;

import com.openhis.whale.report.application.report.response.ReportColumnDto;
import com.openhis.whale.report.application.report.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.report.request.ReportQueryPageRequest;

import java.util.List;

/**
 * C 类报表数据加载器（按 {@code reportCode} 注册）。
 */
public interface ReportLoader {

    /**
     * 报表编码，与 {@code rpt_definition.report_code} 一致。
     *
     * @return 出厂或院点报表编码
     */
    String reportCode();

    /**
     * 报表展示名称。
     *
     * @return 列表与打印标题
     */
    String reportName();

    /**
     * 默认列定义（Port 未返回列时使用）。
     *
     * @return 列元数据列表
     */
    List<ReportColumnDto> columns();

    /**
     * 加载分页数据。
     *
     * @param request 筛选与分页参数
     * @return 列、行、summary 等
     */
    ReportQueryPageResultDto load(ReportQueryPageRequest request);
}
