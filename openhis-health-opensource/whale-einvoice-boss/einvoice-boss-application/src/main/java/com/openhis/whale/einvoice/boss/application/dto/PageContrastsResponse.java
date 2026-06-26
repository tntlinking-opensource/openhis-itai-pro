package com.openhis.whale.einvoice.boss.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 收费对照分页响应。
 */
@Data
public class PageContrastsResponse {

    private List<ChargeContrastDto> rows = new ArrayList<>();
    private long total;
}
