package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 导入团检名单响应（9.5）。
 */
@Getter
@Setter
public class ImportGroupCheckupRosterResponse {

    /** 成功导入条数。 */
    private Integer importedCount;
}
