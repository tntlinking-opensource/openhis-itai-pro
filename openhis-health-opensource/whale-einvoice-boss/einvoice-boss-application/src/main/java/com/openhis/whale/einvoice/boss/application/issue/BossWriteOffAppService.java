package com.openhis.whale.einvoice.boss.application.issue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.application.boss.BossResponseParser;
import com.openhis.whale.einvoice.boss.application.boss.BossSignClient;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.contracts.constants.BossServiceIds;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.einvoice.boss.contracts.constants.EinvBossErrorCodes;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 博思冲红（writeOffEBill）。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW。</p>
 */
@Service
@RequiredArgsConstructor
public class BossWriteOffAppService {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final BossSignClient bossSignClient;
    private final BossResponseParser responseParser;
    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvoiceBossProperties properties;
    private final BossInvokeLogWriter invokeLogWriter;

    /**
     * 同步冲红。
     *
     * @param invoiceId 票据主键
     * @param reason 冲红原因
     * @return 冲红结果
     */
    public BossInvoiceIssueResult writeOff(Long invoiceId, String reason) {
        EinvBossDocument document = documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId)
                .orElseThrow(() -> new BusinessException(EinvBossErrorCodes.EINV_DOCUMENT_NOT_FOUND, "无电子票据单据"));
        BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(invoiceId);
        JSONObject body = new JSONObject();
        body.put("billBatchCode", document.getBossBatchCode());
        body.put("billNo", document.getBossBillNo());
        body.put("reason", StringUtils.defaultIfBlank(reason, "退费冲红"));
        body.put("operator", String.valueOf(snapshot.getOperatorId()));
        body.put("busDateTime", formatTime(LocalDateTime.now()));
        body.put("placeCode", properties.getPlatform().getPlaceCode());
        String requestJson = JSON.toJSONString(body);
        long started = System.currentTimeMillis();
        String response = bossSignClient.invoke(BossServiceIds.WRITE_OFF_E_BILL, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        BossInvokeResult invokeResult = responseParser.parse(response);
        invokeLogWriter.writeWithoutTask(
                document.getTenantId(),
                BossServiceIds.WRITE_OFF_E_BILL,
                document.getBusNo(),
                invokeResult,
                durationMs,
                requestJson,
                response);
        return responseParser.toIssueResult(invokeResult, document.getBusNo(), document.getTotalAmt());
    }

    private String formatTime(LocalDateTime time) {
        return BOSS_TIME.format(time);
    }
}
