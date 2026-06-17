package com.openhis.whale.report.application.query.support;

import com.openhis.whale.report.application.query.response.ReportColumnDto;
import com.openhis.whale.report.application.query.response.ReportQueryPageResultDto;
import com.openhis.whale.report.application.query.request.ReportQueryPageRequest;

import java.util.List;

/**
 * C 类报表数据加载器（按 {@code reportCode} 注册）。
 */
public interface ReportLoader {

    String reportCode();

    String reportName();

    List<ReportColumnDto> columns();

    ReportQueryPageResultDto load(ReportQueryPageRequest request);
}
