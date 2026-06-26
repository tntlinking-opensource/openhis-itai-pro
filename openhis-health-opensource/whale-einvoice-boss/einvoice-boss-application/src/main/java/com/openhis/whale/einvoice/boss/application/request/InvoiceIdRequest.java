package com.openhis.whale.einvoice.boss.application.request;

import lombok.Data;

/**
 * 单票据主键请求。
 */
@Data
public class InvoiceIdRequest {

    /** health 票据主键。 */
    private Long invoiceId;
}
