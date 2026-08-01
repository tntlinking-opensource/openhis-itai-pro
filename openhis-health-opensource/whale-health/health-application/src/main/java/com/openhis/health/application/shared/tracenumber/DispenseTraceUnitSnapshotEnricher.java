/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.tracenumber;

import com.openhis.health.domain.custom.tracenumber.queries.model.TraceUnitSnapshotQueryModel;
import com.openhis.health.domain.custom.tracenumber.service.TraceNumberQueryService;
import com.openhis.health.domain.share.constants.TableNameConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 发药行主档贴码快照与「有码批」标志回填（门诊/住院/汇总共用）。
 *
 * <p>比值取 {@code cus_trace_number}（本库优先，否则全院同品同批），禁止用目录比顶替。
 * {@code lotHasTraceMaster} 表示主表该品批是否曾建码（与在院/剩余无关），供扫码软/硬分叉。</p>
 */
@Component
@RequiredArgsConstructor
public class DispenseTraceUnitSnapshotEnricher {

    private final TraceNumberQueryService traceNumberQueryService;

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
            request.unitApplier().accept(snapshot.getTraceUnitCode(), snapshot.getTraceUnitRatio());
        }
    }

    /**
     * 单行回填请求。
     *
     * @param locationId           发放库房（可空）
     * @param itemId               药品定义主键
     * @param lotNumber            批号（可空）
     * @param unitApplier          写入 {@code (traceUnitCode, traceUnitRatio)}；可空
     * @param lotHasMasterApplier  写入 {@code lotHasTraceMaster}；可空
     */
    public record Request(
        Long locationId,
        Long itemId,
        String lotNumber,
        BiConsumer<String, BigDecimal> unitApplier,
        Consumer<Boolean> lotHasMasterApplier
    ) {
        /**
         * 兼容仅回填贴码单位/比例的旧调用。
         *
         * @param locationId 发放库房
         * @param itemId     药品
         * @param lotNumber  批号
         * @param unitApplier 单位比写入器
         */
        public Request(
            Long locationId,
            Long itemId,
            String lotNumber,
            BiConsumer<String, BigDecimal> unitApplier
        ) {
            this(locationId, itemId, lotNumber, unitApplier, null);
        }
    }
}
