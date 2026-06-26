package com.openhis.whale.einvoice.boss.application.issue;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.openhis.whale.einvoice.boss.application.boss.BossInvokeResult;
import com.openhis.whale.einvoice.boss.application.boss.BossResponseParser;
import com.openhis.whale.einvoice.boss.application.boss.BossSignClient;
import com.openhis.whale.einvoice.boss.application.config.EinvoiceBossProperties;
import com.openhis.whale.einvoice.boss.contracts.constants.BossServiceIds;
import com.openhis.whale.einvoice.boss.contracts.dto.BossInvoiceIssueResult;
import com.openhis.whale.einvoice.boss.contracts.dto.BossIssueSnapshot;
import com.openhis.whale.einvoice.boss.contracts.reader.HealthInvoiceSnapshotReader;
import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.openhis.whale.einvoice.boss.domain.document.repository.EinvBossDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * busNo 查票补偿（getEBillByBusNo）。
 * <p>由 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 管理 UoW。</p>
 */
@Service
@RequiredArgsConstructor
public class BossRecoverAppService {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final BossSignClient bossSignClient;
    private final BossResponseParser responseParser;
    private final HealthInvoiceSnapshotReader healthInvoiceSnapshotReader;
    private final EinvBossDocumentRepository documentRepository;
    private final EinvoiceBossProperties properties;
    private final BossInvokeLogWriter invokeLogWriter;

    /**
     * 按 invoiceId 向博思查票并返回结果。
     *
     * @param invoiceId 票据主键
     * @return 查票结果
     */
    public Optional<BossInvoiceIssueResult> recoverByInvoiceId(Long invoiceId) {
        BossIssueSnapshot snapshot = healthInvoiceSnapshotReader.loadForInvoice(invoiceId);
        JSONObject body = new JSONObject();
        body.put("busNo", snapshot.getBusNo());
        body.put("busDateTime", formatTime(snapshot.getBillDateTime()));
        String requestJson = JSON.toJSONString(body);
        long started = System.currentTimeMillis();
        String response = bossSignClient.invoke(BossServiceIds.GET_E_BILL_BY_BUS_NO, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        BossInvokeResult invokeResult = responseParser.parse(response);
        documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).ifPresent(document ->
                invokeLogWriter.writeWithoutTask(
                        document.getTenantId(),
                        BossServiceIds.GET_E_BILL_BY_BUS_NO,
                        snapshot.getBusNo(),
                        invokeResult,
                        durationMs,
                        requestJson,
                        response));
        if (!invokeResult.isSuccess()) {
            return Optional.empty();
        }
        BossInvoiceIssueResult result = responseParser.toIssueResult(invokeResult, snapshot.getBusNo(), snapshot.getTotalAmt());
        documentRepository.findByInvoiceIdAndIsDeletedFalse(invoiceId).ifPresent(doc -> updateDocument(doc, result));
        return Optional.of(result);
    }

    /**
     * 按 busNo 查票。
     *
     * @param busNo 业务流水号
     * @param busDateTime 原业务发生时间
     * @return 查票结果
     */
    public Optional<BossInvoiceIssueResult> recoverByBusNo(String busNo, LocalDateTime busDateTime) {
        JSONObject body = new JSONObject();
        body.put("busNo", busNo);
        body.put("busDateTime", formatTime(busDateTime));
        String requestJson = JSON.toJSONString(body);
        long started = System.currentTimeMillis();
        String response = bossSignClient.invoke(BossServiceIds.GET_E_BILL_BY_BUS_NO, requestJson);
        int durationMs = (int) (System.currentTimeMillis() - started);
        BossInvokeResult invokeResult = responseParser.parse(response);
        Optional<EinvBossDocument> document = documentRepository.findByBusNoAndIsDeletedFalse(busNo);
        document.ifPresent(doc -> invokeLogWriter.writeWithoutTask(
                doc.getTenantId(),
                BossServiceIds.GET_E_BILL_BY_BUS_NO,
                busNo,
                invokeResult,
                durationMs,
                requestJson,
                response));
        if (!invokeResult.isSuccess()) {
            return Optional.empty();
        }
        BigDecimal total = document.map(EinvBossDocument::getTotalAmt).orElse(null);
        return Optional.of(responseParser.toIssueResult(invokeResult, busNo, total));
    }

    private void updateDocument(EinvBossDocument document, BossInvoiceIssueResult result) {
        document.setBossBillNo(result.getBillNo());
        document.setBossBatchCode(result.getBatchCode());
        document.setRandom(result.getRandom());
        document.setBillQrCode(result.getBillQrCode());
        document.setPictureUrl(result.getPictureUrl());
        document.setPictureNetUrl(result.getPictureNetUrl());
        documentRepository.save(document);
    }

    private String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }
}
