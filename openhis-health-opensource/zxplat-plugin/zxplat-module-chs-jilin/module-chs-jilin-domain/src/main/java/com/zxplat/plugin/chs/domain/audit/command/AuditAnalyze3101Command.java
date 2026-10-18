/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.audit.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 【3101】明细审核事前提醒分析命令。
 *
 * <p>可由试调页手工组装，或由 {@code AuditFeeSnapshot} 投影生成。</p>
 */
@Data
@Accessors(chain = true)
public class AuditAnalyze3101Command {

    /** 契约主键（与 contractBusNo 二选一） */
    private Long contractId;
    /** 契约 bus_no */
    private String contractBusNo;
    /** 操作员 ID（可空，由 bridge 从当前登录补全） */
    private Long operatorId;
    /** 参保地医保区划（报文头；可空则走签到默认） */
    private String insuplcAdmdvs;

    /**
     * 发送方系统代码（必填）。
     * <p>优先取本字段；空则回落 {@code zxplat.chs.audit.syscode}；仍空则 Gate 失败。</p>
     */
    private String syscode;
    /** 任务 ID（可选 UUID） */
    private String taskId;
    /** 触发场景 1–10（优先）；与 ruleIds 至少其一 */
    private String trigScen;
    /** 规则 ID 列表（可选；有 trigScen 时可空） */
    private List<String> ruleIds;

    /** 患者树（→ patient_dtos，必须为列表） */
    private List<AuditPatientCommand> patients = new ArrayList<>();
}
