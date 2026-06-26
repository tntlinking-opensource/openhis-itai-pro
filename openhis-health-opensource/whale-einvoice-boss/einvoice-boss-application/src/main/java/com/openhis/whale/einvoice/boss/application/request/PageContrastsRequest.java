package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

/**
 * 收费对照分页查询。
 */
@Data
public class PageContrastsRequest {

    private String keyword;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
