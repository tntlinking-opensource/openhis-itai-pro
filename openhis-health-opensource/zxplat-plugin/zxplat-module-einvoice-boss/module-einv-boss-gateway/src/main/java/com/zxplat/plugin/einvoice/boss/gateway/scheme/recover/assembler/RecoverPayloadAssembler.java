/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.gateway.scheme.recover.assembler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zxplat.plugin.einvoice.boss.domain.issue.result.BossIssueSnapshot;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 查票补偿（{@code getEBillByBusNo}）业务 JSON 组装。
 *
 * <p>按业务流水号向博思查询票面，用于 Worker 超时补偿与人工查票。</p>
 */
@Component
public class RecoverPayloadAssembler {

    private static final DateTimeFormatter BOSS_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /**
     * 按流水号与业务时间组装查票请求体。
     *
     * @param busNo 业务流水号
     * @param busDateTime 原开票业务时间；空时取当前时间
     * @return 业务 JSON 明文字符串
     */
    public String assemble(String busNo, LocalDateTime busDateTime) {
        JSONObject body = new JSONObject();
        body.put("busNo", busNo);
        body.put("busDateTime", formatTime(busDateTime));
        return JSON.toJSONString(body);
    }

    /**
     * 从 health 快照提取 busNo 与 billDateTime 组装查票请求。
     *
     * @param snapshot 开票快照
     * @return 业务 JSON 明文字符串
     */
    public String assemble(BossIssueSnapshot snapshot) {
        return assemble(snapshot.getBusNo(), snapshot.getBillDateTime());
    }

    /**
     * 格式化为博思业务时间戳。
     *
     * @param time 业务时间；{@code null} 取当前时间
     * @return {@code yyyyMMddHHmmssSSS}
     */
    private String formatTime(LocalDateTime time) {
        LocalDateTime actual = time != null ? time : LocalDateTime.now();
        return BOSS_TIME.format(actual);
    }
}
