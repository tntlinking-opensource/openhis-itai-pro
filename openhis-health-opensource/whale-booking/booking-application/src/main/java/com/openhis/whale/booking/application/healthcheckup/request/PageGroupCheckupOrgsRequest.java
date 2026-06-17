package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 团检单位分页请求（9.3）。
 */
@Getter
@Setter
public class PageGroupCheckupOrgsRequest {

    private String keyword;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
