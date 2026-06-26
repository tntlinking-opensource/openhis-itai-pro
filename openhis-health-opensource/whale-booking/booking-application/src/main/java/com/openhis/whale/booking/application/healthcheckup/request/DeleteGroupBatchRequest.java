package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检批次请求（6.x 旧版 API 别名，对应 DeleteGroupCheckupBatchRequest）。
 */
@Getter
@Setter
public class DeleteGroupBatchRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long id;
}
