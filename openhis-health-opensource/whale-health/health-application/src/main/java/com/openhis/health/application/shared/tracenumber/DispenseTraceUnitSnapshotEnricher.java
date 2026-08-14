/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.custom.tracenumber.queries.model.TraceUnitSnapshotQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 发药行主档贴码快照与「有码批」标志回填（门诊/住院/汇总共用）。
 *
 * <p>有主档时：比值取 {@code cus_trace_number}（本库优先，否则全院同品同批），覆盖调用方预填的药典值。
 * 无主档快照时本组件不写单位比——调用方须先以药品目录（药典）{@code trace_unit_*} 预填，供扫码弹窗展示与待扫软算。
 * {@code lotHasTraceMaster} 表示主表该品批是否曾建码（与在院/剩余无关），供扫码软/硬分叉。
 * 贴码单位展示文案由本组件一次预热 {@code UNIT_CODE} 术语表后回填，调用方勿再单条查术语。</p>
 */
@Component
@RequiredArgsConstructor
public class DispenseTraceUnitSnapshotEnricher {

    private final TraceNumberQueryService traceNumberQueryService;
    /** 单位术语预热（贴码单位 code → 展示文案）。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 按库房+药品+批号批量解析主档贴码快照与有码批标志并写回调用方。
     *
     * @param requests 待回填行；空列表无操作
     */
    public void enrich(List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return;
        }
        List<TraceNumberQueryService.TraceUnitSnapshotLookupKey> snapshotKeys = new ArrayList<>();
        List<TraceNumberQueryService.ItemLotMasterExistsLookupKey> existsKeys = new ArrayList<>();
        for (Request request : requests) {
            if (request == null || request.itemId() == null) {
                continue;
            }
            snapshotKeys.add(new TraceNumberQueryService.TraceUnitSnapshotLookupKey(
                request.locationId(),
                TableNameConstants.MED_MEDICATION_DEFINITION,
                request.itemId(),
                request.lotNumber()
            ));
            existsKeys.add(new TraceNumberQueryService.ItemLotMasterExistsLookupKey(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                request.itemId(),
                request.lotNumber()
            ));
        }
        Map<String, TraceUnitSnapshotQueryModel> snapshotByKey =
            this.traceNumberQueryService.batchResolvePreferredTraceUnitSnapshots(snapshotKeys);
        Map<String, Boolean> existsByKey =
            this.traceNumberQueryService.batchExistsMasterByItemLot(existsKeys);
        // Pre：本批请求只预热一次 UNIT_CODE，供贴码单位文案转译
        Map<String, String> unitLabelByCode =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        for (Request request : requests) {
            if (request == null || request.itemId() == null) {
                continue;
            }
            String existsMapKey = this.traceNumberQueryService.buildItemLotMasterExistsMapKey(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                request.itemId(),
                request.lotNumber());
            boolean lotHasMaster = Boolean.TRUE.equals(existsByKey.get(existsMapKey));
            // Asm：有码批标志（无码批扫码仅软提示、不写主表流水）
            if (request.lotHasMasterApplier() != null) {
                request.lotHasMasterApplier().accept(lotHasMaster);
            }
            if (request.unitApplier() == null) {
                continue;
            }
            String snapshotMapKey = this.traceNumberQueryService.buildTraceUnitSnapshotMapKey(
                request.locationId(), request.itemId(), request.lotNumber());
            TraceUnitSnapshotQueryModel snapshot = snapshotByKey.get(snapshotMapKey);
            // 无主档快照时不写单位比（前端回落药典软算待扫）
            if (snapshot == null) {
                continue;
            }
            String code = snapshot.getTraceUnitCode();
            String codeText = OrderCatalogDisplayAssembler.resolveCategoryDisplay(unitLabelByCode, code);
            request.unitApplier().accept(code, snapshot.getTraceUnitRatio(), codeText);
        }
    }

    /**
     * 写入主档贴码单位 / 比例 / 术语展示文案。
     */
    @FunctionalInterface
    public interface TraceUnitSnapshotApplier {

        /**
         * 回填一行贴码快照。
         *
         * @param traceUnitCode     贴码单位 code；可空
         * @param traceUnitRatio    1 码对应拆零量；可空
         * @param traceUnitCodeText {@code UNIT_CODE} 展示文案；术语未命中时为 null
         */
        void accept(String traceUnitCode, BigDecimal traceUnitRatio, String traceUnitCodeText);
    }

    /**
     * 单行回填请求。
     *
     * @param locationId           发放库房（可空）
     * @param itemId               药品定义主键
     * @param lotNumber            批号（可空）
     * @param unitApplier          写入 {@code (traceUnitCode, traceUnitRatio, traceUnitCodeText)}；可空
     * @param lotHasMasterApplier  写入 {@code lotHasTraceMaster}；可空
     */
    public record Request(
        Long locationId,
        Long itemId,
        String lotNumber,
        TraceUnitSnapshotApplier unitApplier,
        Consumer<Boolean> lotHasMasterApplier
    ) {
        /**
         * 兼容仅回填贴码单位/比例/文案、不写有码批标志的调用。
         *
         * @param locationId  发放库房
         * @param itemId      药品
         * @param lotNumber   批号
         * @param unitApplier 单位比与文案写入器
         */
        public Request(
            Long locationId,
            Long itemId,
            String lotNumber,
            TraceUnitSnapshotApplier unitApplier
        ) {
            this(locationId, itemId, lotNumber, unitApplier, null);
        }
    }
}
