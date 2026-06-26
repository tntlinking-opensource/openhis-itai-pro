package com.openhis.whale.einvoice.boss.application.account;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.application.boss.BossResponseParser;
import com.openhis.whale.einvoice.boss.application.boss.BossSignClient;
import com.openhis.whale.einvoice.boss.application.issue.BossInvokeLogWriter;
import com.openhis.whale.einvoice.boss.contracts.constants.BossServiceIds;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 博思入账状态查询（getEBillAccountStatus · P3）。
 */
@Service
@RequiredArgsConstructor
public class BossAccountQueryService {

    private static final long DEFAULT_TENANT_ID = 1L;

    private final BossSignClient bossSignClient;
    private final BossResponseParser responseParser;
    private final BossInvokeLogWriter invokeLogWriter;

    /**
     * 按票面信息查询入账状态。
     *
     * @param billBatchCode 电子票据代码
     * @param billNo 电子票据号码
     * @return 入账状态
     */
    public BossAccountStatus query(String billBatchCode, String billNo) {
        JSONObject body = new JSONObject();
        body.put("billBatchCode", billBatchCode);
        body.put("billNo", billNo);
        String requestJson = body.toJSONString();
        long started = System.currentTimeMillis();
        String response = bossSignClient.invoke(BossServiceIds.GET_E_BILL_ACCOUNT_STATUS, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        BossInvokeResult invokeResult = responseParser.parse(response);
        invokeLogWriter.writeWithoutTask(
                DEFAULT_TENANT_ID,
                BossServiceIds.GET_E_BILL_ACCOUNT_STATUS,
                billNo,
                invokeResult,
                durationMs,
                requestJson,
                response);

        BossAccountStatus status = new BossAccountStatus();
        status.setSuccess(invokeResult.isSuccess());
        status.setResultCode(invokeResult.getResultCode());
        status.setRawMessage(invokeResult.getRawMessage());
        JSONObject business = invokeResult.getBusinessJson();
        if (business != null) {
            status.setAccountStatus(readText(business, "accountStatus", "accStatus", "state"));
            status.setAccountDate(readText(business, "accountDate", "accDate", "accountTime"));
        }
        return status;
    }

    private String readText(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return StringUtils.trimToNull(json.getString(key));
            }
        }
        return null;
    }

    @Data
    public static class BossAccountStatus {
        private boolean success;
        private String resultCode;
        private String rawMessage;
        private String accountStatus;
        private String accountDate;
    }
}
