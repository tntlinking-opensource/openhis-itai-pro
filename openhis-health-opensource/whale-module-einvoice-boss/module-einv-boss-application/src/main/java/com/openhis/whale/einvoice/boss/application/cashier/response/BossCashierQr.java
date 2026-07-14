/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.application.cashier.response;

import lombok.Data;

/**
 * 收费窗二维码读模型。
 */
@Data
public class BossCashierQr {

    private String billNo;
    private String billQrCode;
    private String pictureUrl;
    private String pictureNetUrl;
}
