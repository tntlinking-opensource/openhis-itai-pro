package com.openhis.whale.report.application.query.request;

import lombok.Data;

@Data
public class ListReportDefinitionsRequest {

    /** draft | published；空表示全部 */
    private String status;

    private String keyword;
}
