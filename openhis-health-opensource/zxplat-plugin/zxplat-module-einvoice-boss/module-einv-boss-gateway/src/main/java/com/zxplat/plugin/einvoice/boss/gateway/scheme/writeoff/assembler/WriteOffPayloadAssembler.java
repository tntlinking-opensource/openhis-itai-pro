/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.writeoff.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.domain.document.entity.EinvBossDocument;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import com.zxplat.plugin.einvoice.boss.gateway.config.EinvoiceBossGatewayProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 冲红（{@code writeOffEBill}）业务 JSON 组装。
 *
 * <p>输入为已开票 {@link EinvBossDocument} 的蓝票批次号/票号及冲红原因；
 * 由 {@code BossWriteOffOutboundSupport} 在抢占冲红锁成功后调用。</p>
 */
@Component
@RequiredArgsConstructor
public class WriteOffPayloadAssembler {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final EinvoiceBossGatewayProperties properties;

    /**
     * 组装冲红请求体。
     *
     * @param document 已开票 boss 单据（含蓝票 batchCode/billNo）
     * @param snapshot 操作员等上下文
     * @param reason 冲红原因；空时默认「退费冲红」
     * @param busDateTime 业务时间；空时取当前时间
     * @return 业务 JSON 明文字符串
     */
    public String assemble(
            EinvBossDocument document,
            BossIssueSnapshot snapshot,
            String reason,
            LocalDateTime busDateTime) {
        JSONObject body = new JSONObject();
        body.put("billBatchCode", document.getBossBatchCode());
        body.put("billNo", document.getBossBillNo());
        body.put("reason", StringUtils.defaultIfBlank(reason, "退费冲红"));
        body.put("operator", String.valueOf(snapshot.getOperatorId()));
        body.put("busDateTime", formatTime(busDateTime));
        body.put("placeCode", properties.getPlatform().getPlaceCode());
        return JSON.toJSONString(body);
    }

    /**
     * 格式化为博思业务时间戳。
     *
     * @param time 业务时间；{@code null} 取当前时间
     * @return {@code yyyyMMddHHmmssSSS}
     */
    private String formatTime(LocalDateTime time) {
        return BOSS_TIME.format(time != null ? time : LocalDateTime.now());
    }
}
