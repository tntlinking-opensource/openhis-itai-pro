package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除团检名单请求（9.5）。
 */
@Getter
@Setter
public class DeleteGroupCheckupRosterRequest {

    @NotNull
    private Long id;
}
