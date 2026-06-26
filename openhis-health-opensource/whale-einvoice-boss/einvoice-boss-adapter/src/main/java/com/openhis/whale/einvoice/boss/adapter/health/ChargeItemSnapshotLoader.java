package com.openhis.whale.einvoice.boss.adapter.health;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.OutpatientChargeConstants;
import com.openhis.health.domain.share.enums.AccountTypeCode;
import com.openhis.whale.einvoice.boss.contracts.dto.BossChargeLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 adm_charge_item 加载开票明细快照。
 */
@Component
@RequiredArgsConstructor
public class ChargeItemSnapshotLoader {

    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 按收费明细主键集合加载行快照。
     *
     * @param chargeItemIds 收费明细主键
     * @param registrationFee 是否挂号费单线
     * @return 行快照列表
     */
    public List<BossChargeLineSnapshot> loadByIds(List<Long> chargeItemIds, boolean registrationFee) {
        List<BossChargeLineSnapshot> lines = new ArrayList<>();
        if (chargeItemIds == null || chargeItemIds.isEmpty()) {
            return lines;
        }
        ChargeItemQueryCommand query = new ChargeItemQueryCommand();
        query.setIds(chargeItemIds);
        List<ChargeItem> rows = chargeItemQueryService.loadChargeItemsByIds(query);
        int lineNo = 0;
        for (ChargeItem row : rows) {
            lineNo = lineNo + 1;
            BossChargeLineSnapshot snapshot = new BossChargeLineSnapshot();
            snapshot.setChargeItemId(row.getId());
            snapshot.setDefinitionId(row.getDefinitionId());
            if (registrationFee) {
                snapshot.setInternalCode("REG_FEE");
            } else {
                snapshot.setInternalCode("DEF_" + row.getDefinitionId());
            }
            snapshot.setAmount(row.getTotalPrice());
            snapshot.setQuantity(row.getQuantityValue());
            snapshot.setUnit(row.getQuantityUnit());
            snapshot.setUnitPrice(row.getUnitPrice());
            lines.add(snapshot);
        }
        return lines;
    }

    /**
     * 根据票据类型推断是否挂号费场景。
     *
     * @param invoiceTypeCode 票据类型码
     * @return 是否挂号
     */
    public boolean isRegistrationInvoice(String invoiceTypeCode) {
        return AccountTypeCode.REG.getCode().equals(invoiceTypeCode);
    }

    /**
     * 根据票据类型推断是否门诊收费场景。
     *
     * @param invoiceTypeCode 票据类型码
     * @return 是否门诊
     */
    public boolean isOutpatientInvoice(String invoiceTypeCode) {
        return OutpatientChargeConstants.INVOICE_TYPE_OUTPATIENT.equals(invoiceTypeCode);
    }
}
