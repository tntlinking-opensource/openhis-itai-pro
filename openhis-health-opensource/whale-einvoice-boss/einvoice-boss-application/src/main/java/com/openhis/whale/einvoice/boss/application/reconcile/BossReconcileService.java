package com.openhis.whale.einvoice.boss.application.reconcile;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.application.boss.BossResponseParser;
import com.openhis.whale.einvoice.boss.application.boss.BossSignClient;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.application.issue.BossInvokeLogWriter;
import com.openhis.whale.einvoice.boss.contracts.constants.BossServiceIds;
import com.openhis.whale.einvoice.boss.contracts.enums.IssueKind;
import com.openhis.whale.einvoice.boss.contracts.enums.ReconcileDateMode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 博思财务日结核对 HTTP 调用（checkTotalData*）。
 */
@Service
@RequiredArgsConstructor
public class BossReconcileService {

    private static final DateTimeFormatter BOSS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long DEFAULT_TENANT_ID = 1L;

    private final BossSignClient bossSignClient;
    private final BossResponseParser responseParser;
    private final EinvoiceBossProperties properties;
    private final BossInvokeLogWriter invokeLogWriter;

    /**
     * 向博思查询指定日期的总笔数核对数据。
     *
     * @param checkDate 核对日期
     * @param dateMode BUS_DATE 或 IVC_DATE
     * @param issueKind 业务线（可空）
     * @param pageNo 页码
     * @param pageSize 页大小
     * @return 博思侧统计
     */
    public BossReconcileStats queryBossTotal(
            LocalDate checkDate,
            String dateMode,
            String issueKind,
            int pageNo,
            int pageSize) {
        String serviceId = ReconcileDateMode.IVC_DATE.getCode().equals(dateMode)
                ? BossServiceIds.CHECK_TOTAL_DATA_BY_IVC_DATE
                : BossServiceIds.CHECK_TOTAL_DATA;

        JSONObject body = new JSONObject();
        String dateText = BOSS_DATE.format(checkDate);
        if (ReconcileDateMode.IVC_DATE.getCode().equals(dateMode)) {
            body.put("ivcDate", dateText);
        } else {
            body.put("busDate", dateText);
        }
        String placeCode = properties.getPlatform().getPlaceCode();
        if (StringUtils.isNotBlank(placeCode)) {
            body.put("placeCode", placeCode);
        }
        String busType = resolveBusType(issueKind);
        if (StringUtils.isNotBlank(busType)) {
            body.put("busType", busType);
        }
        body.put("pageNo", String.valueOf(pageNo));
        body.put("pageSize", String.valueOf(pageSize));

        String requestJson = body.toJSONString();
        long started = System.currentTimeMillis();
        String response = bossSignClient.invoke(serviceId, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        BossInvokeResult invokeResult = responseParser.parse(response);
        invokeLogWriter.writeWithoutTask(
                DEFAULT_TENANT_ID,
                serviceId,
                dateText,
                invokeResult,
                durationMs,
                requestJson,
                response);

        BossReconcileStats stats = new BossReconcileStats();
        stats.setSuccess(invokeResult.isSuccess());
        stats.setResultCode(invokeResult.getResultCode());
        stats.setRawMessage(invokeResult.getRawMessage());
        JSONObject business = invokeResult.getBusinessJson();
        if (business != null) {
            stats.setTotalCount(readLong(business, "allPageTotalCount", "totalCount", "totalNum", "allPageTotalNum"));
            stats.setTotalAmount(readDecimal(business, "allPageTotalAmt", "totalAmt", "totalAmount", "allPageTotalAmount"));
            stats.setPageCount(readLong(business, "count", "pageCount", "totalCount"));
        }
        return stats;
    }

    private String resolveBusType(String issueKind) {
        if (!StringUtils.isNotBlank(issueKind)) {
            return null;
        }
        for (IssueKind kind : IssueKind.values()) {
            if (kind.getCode().equalsIgnoreCase(issueKind.trim())) {
                return kind.getBusType();
            }
        }
        return null;
    }

    private long readLong(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return json.getLongValue(key);
            }
        }
        return 0L;
    }

    private BigDecimal readDecimal(JSONObject json, String... keys) {
        for (String key : keys) {
            if (json.containsKey(key) && json.get(key) != null) {
                return json.getBigDecimal(key);
            }
        }
        return BigDecimal.ZERO;
    }

    /**
     * 博思核对统计。
     */
    @Data
    public static class BossReconcileStats {
        private boolean success;
        private String resultCode;
        private String rawMessage;
        private long totalCount;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private long pageCount;
    }
}
