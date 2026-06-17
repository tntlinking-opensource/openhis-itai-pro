package com.openhis.whale.booking.application.register.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者检索请求（4.7）。
 */
@Getter
@Setter
public class PagePatientsForSelectRequest {

    /** 关键字。 */
    private String keyword;

    /** 页码（1-based）。 */
    @NotNull
    private Integer pageNo;

    /** 页大小。 */
    @NotNull
    private Integer pageSize;
}
