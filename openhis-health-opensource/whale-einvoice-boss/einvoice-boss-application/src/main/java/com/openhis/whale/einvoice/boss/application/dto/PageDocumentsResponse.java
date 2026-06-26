package com.openhis.whale.einvoice.boss.application.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 电子发票分页查询响应。
 */
@Data
public class PageDocumentsResponse {

    private List<BossDocumentListItemDto> rows = new ArrayList<>();
    private long total;
}
