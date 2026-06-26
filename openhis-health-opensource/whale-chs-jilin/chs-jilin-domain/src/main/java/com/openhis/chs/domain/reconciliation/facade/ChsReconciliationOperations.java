/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.reconciliation.facade;

import com.openhis.chs.domain.reconciliation.dto.request.ApplyJilinClear3203ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearOrg3206ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryClearStatus3205ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDaily3201Request;
import com.openhis.chs.domain.reconciliation.dto.request.ReconDetail3202Request;
import com.openhis.chs.domain.reconciliation.dto.request.ListLocalReconRecordsRequest;
import com.openhis.chs.domain.reconciliation.dto.request.QueryCrossProvinceRecon3209ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.RevokeJilinClear3204ARequest;
import com.openhis.chs.domain.reconciliation.dto.request.SummarizeClearAmountRequest;
import com.openhis.chs.domain.reconciliation.dto.response.ApplyJilinClear3203AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearAmountSummaryDto;
import com.openhis.chs.domain.reconciliation.dto.response.ClearOrg3206AResult;
import com.openhis.chs.domain.reconciliation.dto.response.ClearStatus3205ARowDto;
import com.openhis.chs.domain.reconciliation.dto.response.CrossProvinceReconRowDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalClearApplyDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDetailDto;
import com.openhis.chs.domain.reconciliation.dto.response.LocalReconDailyDto;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDaily3201Result;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetail3202Result;
import com.openhis.chs.domain.reconciliation.dto.response.ReconDetailRowDto;

import java.util.List;

/** 吉林对账清算能力（3201 / 3202 / 3203A–3206A / 3209A）。 */
public interface ChsReconciliationOperations {

    ClearOrg3206AResult queryClearOrganizations(QueryClearOrg3206ARequest request);

    ClearAmountSummaryDto summarizeClearAmounts(SummarizeClearAmountRequest request);

    ApplyJilinClear3203AResult applyJilinClear(ApplyJilinClear3203ARequest request);

    List<ClearStatus3205ARowDto> queryClearApplyStatus(QueryClearStatus3205ARequest request);

    void revokeJilinClear(RevokeJilinClear3204ARequest request);

    ReconDaily3201Result reconcileDailyLedger(ReconDaily3201Request request);

    List<ReconDetailRowDto> previewReconDetailRows(SummarizeClearAmountRequest request);

    ReconDetail3202Result reconcileDetailLedger(ReconDetail3202Request request);

    List<LocalReconDetailDto> listLocalReconDetailRecords(ListLocalReconRecordsRequest request);

    List<LocalClearApplyDto> listLocalClearApplies(ListLocalReconRecordsRequest request);

    List<LocalReconDailyDto> listLocalReconDailyRecords(ListLocalReconRecordsRequest request);

    List<CrossProvinceReconRowDto> queryCrossProvinceReconFailures(QueryCrossProvinceRecon3209ARequest request);
}
