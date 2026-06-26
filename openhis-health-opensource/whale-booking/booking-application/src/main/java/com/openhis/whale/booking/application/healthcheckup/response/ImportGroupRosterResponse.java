package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 导入团检名单响应（6.x 旧版 API 别名，对应 ImportGroupCheckupRosterResponse）。
 */
@Getter
@Setter
public class ImportGroupRosterResponse {

    /** 成功导入条数。 */
    private Integer importedCount;
}
