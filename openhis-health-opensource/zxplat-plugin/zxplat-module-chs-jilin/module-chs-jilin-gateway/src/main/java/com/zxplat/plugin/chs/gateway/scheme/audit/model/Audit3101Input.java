/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.model;

import com.alibaba.fastjson2.annotation.JSONField;
import com.zxplat.plugin.chs.gateway.scheme.common.model.wrapper.ChsDataInput;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/** 【3101】明细审核事前提醒分析 input */
public class Audit3101Input extends ChsDataInput<Audit3101Input.Audit3101Data> {

    /** 3101 input.data */
    @Data
    @Accessors(chain = true)
    public static class Audit3101Data {

        /** 发送方系统代码 */
        private String syscode;

        /** 规则 ID 列表（可空） */
        @JSONField(name = "rule_ids")
        private List<String> ruleIds;

        /** 任务 ID */
        @JSONField(name = "task_id")
        private String taskId;

        /** 触发场景 */
        @JSONField(name = "trig_scen")
        private String trigScen;

        /** 患者集合（必须数组） */
        @JSONField(name = "patient_dtos")
        private List<Audit3101PatientRow> patientDtos;
    }
}
