/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.devicerequest.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建耗材草稿命令。
 */
@Getter
@Setter
public class CreateDeviceOrderDraftCommand extends BatchCommand<CreateDeviceOrderDraftCommand.CreateDeviceOrderDraftItemCommand> {

    /** 操作人主键。 */
    private Long operatorId;

/**
 * 创建设备医嘱草稿项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class CreateDeviceOrderDraftItemCommand {
        /** 设备def主键。 */
        private Long deviceDefId;
        /** 数量。 */
        private BigDecimal quantity;
        /** unit编码。 */
        private String unitCode;
        /** 就诊主键。 */
        private Long encounterId;
        /** 患者主键。 */
        private Long patientId;
        /**
         * 默认器材房（{@code adm_location.id}；医生站发材库位，与 {@link #performLocation} 同值写入）。
         */
        private Long locationId;
        /**
         * 发放耗材房（{@code adm_location.id}；权威发材库位，签发 FIFO 按此房扣库）。
         */
        private Long performLocation;

        /** 生成来源持久化码（医生站与药品对称过滤）。 */
        private String generateSourceEnum;
        /** 医生站列表展示排序号（可选）。 */
        private Integer sortNumber;
        /**
         * 绑定就诊诊断主键有序串（门诊医生站绑定诊断；持久化 {@code wkf_device_request.encounter_diagnosis_ids}；有序，首位为主诊断）。
         */
        private String encounterDiagnosisIds;
        /**
         * 来源锚点表名（用法绑定派生耗材锚定 {@code med_medication_request} 等；与 {@link #basedOnId} 成对）。
         */
        private String basedOnTable;
        /**
         * 来源锚点主键（与 {@link #basedOnTable} 成对；成组可任取组内一条药嘱 id）。
         */
        private Long basedOnId;
        /**
         * 组套标识（成组绑定可与主药嘱 {@code package_id} 对齐；可空）。
         */
        private Long packageId;
        /**
         * 费用性质合同业务号（合同 {@code bus_no}；落 {@code wkf_device_request.contract_code}，供列表反查 contractId）。
         */
        private String contractCode;
    }
}
