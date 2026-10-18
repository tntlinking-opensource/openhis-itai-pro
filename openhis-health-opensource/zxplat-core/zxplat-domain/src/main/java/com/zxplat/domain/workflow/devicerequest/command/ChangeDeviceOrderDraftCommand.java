/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 修改耗材草稿命令。
 */
@Getter
@Setter
public class ChangeDeviceOrderDraftCommand extends BatchCommand<ChangeDeviceOrderDraftCommand.ChangeDeviceOrderDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 变更设备医嘱草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ChangeDeviceOrderDraftItemCommand {
        /** 主键。 */
        private Long id;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /**
         * 默认器材房（{@code adm_location.id}；非空时与 {@link #performLocation} 一并更新）。
         */
        private Long locationId;
        /**
         * 发放耗材房（{@code adm_location.id}；非空时更新；医生站发材库位）。
         */
        private Long performLocation;
        /** 批号号。 */
        private String lotNumber;
        /** 追溯号。 */
        private String traceNo;
        /** remark。 */
        private String remark;
        /** use时间。 */
        private LocalDateTime useTime;
        /** use开始时间。 */
        private LocalDateTime useStartTime;
        /** use结束时间。 */
        private LocalDateTime useEndTime;
        /** usetiming。 */
        private String useTiming;
        /** 医生站列表展示排序号；非空时更新。 */
        private Integer sortNumber;
        /**
         * 绑定就诊诊断主键有序串（草稿修改；非空且与存量不同时更新 {@code wkf_device_request.encounter_diagnosis_ids}）。
         */
        private String encounterDiagnosisIds;
        /**
         * 费用性质合同业务号（合同 {@code bus_no}；非空时更新 {@code wkf_device_request.contract_code}）。
         */
        private String contractCode;
    }
}
