/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port;

import com.openhis.whale.chs.jilin.port.model.UploadAfterDispenseCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterGainLossCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterInboundCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterPurchaseReturnCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterReturnCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterStocktakeCommand;
import com.openhis.whale.chs.jilin.port.model.UploadAfterTransferCommand;

/** 进销存上报 Port（门诊/住院发药退药、进货、调拨）。 */
public interface InventoryInsurancePort {

    /** 门诊/住院/汇总发药后 3505（推荐入口）。 */
    void uploadAfterDispense(UploadAfterDispenseCommand command);

    /** @deprecated 请使用 {@link #uploadAfterDispense(UploadAfterDispenseCommand)} */
    default void uploadAfterOutpatientDispense(UploadAfterDispenseCommand command) {
        uploadAfterDispense(command);
    }

    void uploadAfterOutpatientReturn(UploadAfterReturnCommand command);

    void uploadAfterInboundAgree(UploadAfterInboundCommand command);

    /** 药库向供应商退货同意后 3504。 */
    void uploadAfterPurchaseReturn(UploadAfterPurchaseReturnCommand command);

    /** 药库盘点同意：3501 实盘 + 3502 盈亏（CHS 启用且 Port 装配时自动上报）。 */
    void uploadAfterStocktakeAgree(UploadAfterStocktakeCommand command);

    /** 药库出库/药房入库/调拨同意：3502（CHS 启用且 Port 装配时自动上报）。 */
    void uploadAfterTransferAgree(UploadAfterTransferCommand command);

    /** 损益单同意：3502 盈亏（CHS 启用且 Port 装配时自动上报）。 */
    void uploadAfterGainLossAgree(UploadAfterGainLossCommand command);
}
