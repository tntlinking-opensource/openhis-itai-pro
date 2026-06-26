package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检单位请求（6.x 旧版 API 别名，对应 DeleteGroupCheckupOrgRequest）。
 */
@Getter
@Setter
public class DeleteGroupOrgRequest {

    /** 团检单位主键。 */
    @NotNull
    private Long id;
}
