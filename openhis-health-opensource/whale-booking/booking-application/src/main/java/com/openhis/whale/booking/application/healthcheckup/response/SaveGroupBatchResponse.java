package com.openhis.whale.booking.application.healthcheckup.response;

import lombok.Getter;
import lombok.Setter;

/**
 * 保存团检批次响应（6.x 旧版 API 别名，对应 SaveGroupCheckupBatchResponse）。
 */
@Getter
@Setter
public class SaveGroupBatchResponse {

    /** 团检批次主键。 */
    private Long id;
}
