/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.support;

import com.openhis.health.domain.share.utils.InventoryDetailSnapshotBusNoSupport;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 库房日结业务日窗解析（BR-INV-SET-004）。
 *
 * <p>与库存零点备份（每日约 00:05，见 AS-INV-WHS-004 **BR-INV-DTL-028**）配套：备份单号自然日 {@code D}
 * 表示 {@code D} 日抓拍，库存内容为 {@code (D-1)} 自然日日终；期间业务自期初备份日 {@code D₀} 当日
 * {@code 00:00:00} 起统计（含 {@code D₀} 全天）。</p>
 */
@Component
public class DailySettlementWindowResolver {

    /**
     * 由期初/期末备份单号解析供应/发药审批统计窗（双闭 {@code LocalDateTime} 边界）。
     *
     * @param startBackupBusNo 期初备份单号
     * @param endBackupBusNo   期末备份单号，可空表示实时期末
     * @return 时间窗上下文
     */
    public DailySettlementWindowContext resolveContext(
        String startBackupBusNo,
        String endBackupBusNo
    ) {
        DailySettlementWindowContext context = new DailySettlementWindowContext();
        context.setStartBackupBusNo(trimToNull(startBackupBusNo));
        context.setEndBackupBusNo(trimToNull(endBackupBusNo));
        LocalDate openingDate = this.parseBackupBusNoDate(context.getStartBackupBusNo());
        context.setOpeningBackupDate(openingDate);
        LocalDate endingDate;
        boolean endUsesRealtime = false;
        // 未选期末备份：期末取查询时刻实时库存
        if (context.getEndBackupBusNo() == null) {
            endUsesRealtime = true;
            endingDate = LocalDate.now();
        } else {
            // 选了期末备份：解析备份日作为期末锚点
            endingDate = this.parseBackupBusNoDate(context.getEndBackupBusNo());
        }
        context.setEndingDate(endingDate);
        context.setEndUsesRealtime(endUsesRealtime);
        // 零点备份：期初 ABU 日 D₀ 的库存为 (D₀−1) 日终；期间自 D₀ 00:00 起含当天业务（BR-INV-SET-004）
        LocalDateTime windowStart = openingDate.atStartOfDay();
        LocalDateTime windowEnd;
        if (endUsesRealtime) {
            // 实时期末：上界为当前查询时刻
            windowEnd = LocalDateTime.now();
        } else {
            // 期末备份日 D₁ 的 ABU 库存为 (D₁−1) 日终；期间上界对齐该日终末秒
            windowEnd = endingDate.minusDays(1).atTime(LocalTime.MAX);
        }
        context.setApprovalWindowStartInclusive(windowStart);
        context.setApprovalWindowEndInclusive(windowEnd);
        return context;
    }

    /**
     * 自 ABU 备份单号解析自然日（ABU+yyyy-MM-dd+序号）。
     *
     * @param backupBusNo 备份单号
     * @return 自然日
     */
    public LocalDate parseBackupBusNoDate(String backupBusNo) {
        return InventoryDetailSnapshotBusNoSupport.parseBackupDate(backupBusNo);
    }
}
