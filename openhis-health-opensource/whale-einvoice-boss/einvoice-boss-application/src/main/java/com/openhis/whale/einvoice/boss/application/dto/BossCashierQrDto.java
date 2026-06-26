package com.openhis.whale.einvoice.boss.application.dto;

import lombok.Data;

/**
 * 收费窗二维码读模型。
 */
@Data
public class BossCashierQrDto {

    private String billNo;
    private String billQrCode;
    private String pictureUrl;
    private String pictureNetUrl;
}
