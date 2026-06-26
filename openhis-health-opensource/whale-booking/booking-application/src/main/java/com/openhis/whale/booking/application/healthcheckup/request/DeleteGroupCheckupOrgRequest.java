package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检单位请求（9.3）。
 */
@Getter
@Setter
public class DeleteGroupCheckupOrgRequest {

    /** 团检单位主键。 */
    @NotNull
    private Long id;
}
