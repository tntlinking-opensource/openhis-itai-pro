package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量票据主键请求（批量补开 P2）。
 */
@Data
public class BatchInvoiceIdsRequest {

    private List<Long> invoiceIds = new ArrayList<>();
}
