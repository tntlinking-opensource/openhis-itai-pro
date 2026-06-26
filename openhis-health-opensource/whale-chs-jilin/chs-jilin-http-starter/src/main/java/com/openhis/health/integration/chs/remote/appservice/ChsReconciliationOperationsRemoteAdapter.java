package com.openhis.health.integration.chs.remote.appservice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.openhis.chs.domain.reconciliation.dto.request.ApplyJilinClear3203ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearOrg3206ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearStatus3205ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDaily3201Request;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDetail3202Request;
import com.openhis.chs.domain.reconciliation.dto.request.QueryCrossProvinceRecon3209ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.RevokeJilinClear3204ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.SummarizeClearAmountRequest;
import com.openhis.chs.domain.reconciliation.dto.response.ApplyJilinClear3203AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearAmountSummaryDto;
import com.openhis.chs.domain.reconciliation.dto.response.ClearOrg3206AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearStatus3205ARowDto;
import com.openhis.chs.domain.reconciliation.dto.response.CrossProvinceReconRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalClearApplyDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDailyDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDetailDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailRowDto;
import com.openhis.chs.domain.reconciliation.facade.ChsReconciliationOperations;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import com.openhis.chs.domain.reconciliation.dto.request.ListLocalReconRecordsRequest;

@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.chs.deployment.mode", havingValue = "remote")
public class ChsReconciliationOperationsRemoteAdapter implements ChsReconciliationOperations {

    private static final String SERVICE = "chsReconciliation";

    private final ChsRemoteAppServiceClient remoteClient;

    @Override
    public ClearOrg3206AResult queryClearOrganizations(QueryClearOrg3206ARequest request) {
        return remoteClient.post(SERVICE, "queryClearOrganizations", request, ClearOrg3206AResult.class);
    }

    @Override
    public ClearAmountSummaryDto summarizeClearAmounts(SummarizeClearAmountRequest request) {
        return remoteClient.post(SERVICE, "summarizeClearAmounts", request, ClearAmountSummaryDto.class);
    }

    @Override
    public ApplyJilinClear3203AResult applyJilinClear(ApplyJilinClear3203ARequest request) {
        return remoteClient.post(SERVICE, "applyJilinClear", request, ApplyJilinClear3203AResult.class);
    }

    @Override
    public List<ClearStatus3205ARowDto> queryClearApplyStatus(QueryClearStatus3205ARequest request) {
        return remoteClient.post(
            SERVICE,
            "queryClearApplyStatus",
            request,
            new TypeReference<List<ClearStatus3205ARowDto>>() {});
    }

    @Override
    public void revokeJilinClear(RevokeJilinClear3204ARequest request) {
        remoteClient.post(SERVICE, "revokeJilinClear", request, Void.class);
    }

    @Override
    public ReconDaily3201Result reconcileDailyLedger(ReconDaily3201Request request) {
        return remoteClient.post(SERVICE, "reconcileDailyLedger", request, ReconDaily3201Result.class);
    }

    @Override
    public List<ReconDetailRowDto> previewReconDetailRows(SummarizeClearAmountRequest request) {
        return remoteClient.post(
            SERVICE,
            "previewReconDetailRows",
            request,
            new TypeReference<List<ReconDetailRowDto>>() {});
    }

    @Override
    public ReconDetail3202Result reconcileDetailLedger(ReconDetail3202Request request) {
        return remoteClient.post(SERVICE, "reconcileDetailLedger", request, ReconDetail3202Result.class);
    }

    @Override
    public List<LocalReconDetailDto> listLocalReconDetailRecords(ListLocalReconRecordsRequest request) {
        return remoteClient.post(
            SERVICE,
            "listLocalReconDetailRecords",
            request,
            new TypeReference<List<LocalReconDetailDto>>() {});
    }

    @Override
    public List<LocalClearApplyDto> listLocalClearApplies(ListLocalReconRecordsRequest request) {
        return remoteClient.post(
            SERVICE,
            "listLocalClearApplies",
            request,
            new TypeReference<List<LocalClearApplyDto>>() {});
    }

    @Override
    public List<LocalReconDailyDto> listLocalReconDailyRecords(ListLocalReconRecordsRequest request) {
        return remoteClient.post(
            SERVICE,
            "listLocalReconDailyRecords",
            request,
            new TypeReference<List<LocalReconDailyDto>>() {});
    }

    @Override
    public List<CrossProvinceReconRowDto> queryCrossProvinceReconFailures(
            QueryCrossProvinceRecon3209ARequest request) {
        return remoteClient.post(
            SERVICE,
            "queryCrossProvinceReconFailures",
            request,
            new TypeReference<List<CrossProvinceReconRowDto>>() {});
    }
}
