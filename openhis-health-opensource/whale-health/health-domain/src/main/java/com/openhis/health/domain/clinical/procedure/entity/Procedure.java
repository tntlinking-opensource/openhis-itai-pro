/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.procedure.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EventStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import com.openhis.whale.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 执行记录聚合根。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_procedure")
public class Procedure extends BaseAuditedEntity {
    @Column(name = "request_id", nullable = false)
    /** 请求主键。 */
    private Long requestId;
    @Column(name = "request_table", nullable = false)
    /** 请求来源表。 */
    private String requestTable;
    @Column(name = "status_enum", nullable = false)
    /** 状态枚举编码。 */
    private String statusEnum;
    @Column(name = "status_reason_text")
    /** 状态原因文本。 */
    private String statusReasonText;
    @Column(name = "category_enum", nullable = false)
    /** 类别枚举编码。 */
    private String categoryEnum;
    @Column(name = "procedure_code")
    /** 操作编码。 */
    private String procedureCode;
    @Column(name = "encounter_id", nullable = false)
    /** 就诊主键。 */
    private Long encounterId;
    @Column(name = "patient_id", nullable = false)
    /** 患者主键。 */
    private Long patientId;
    @Column(name = "occurrence_time")
    /** 发生时间。 */
    private LocalDateTime occurrenceTime;
    @Column(name = "occurrence_period")
    /** 发生period。 */
    private String occurrencePeriod;
    @Column(name = "occurrence_age")
    /** 发生age。 */
    private String occurrenceAge;
    @Column(name = "occurrence_text")
    /** 发生文本。 */
    private String occurrenceText;
    @Column(name = "recorded_time")
    /** 记录时间。 */
    private LocalDateTime recordedTime;
    @Column(name = "recorder_id")
    /** 记录人主键。 */
    private Long recorderId;
    @Column(name = "report_flag")
    /** 报告标志。 */
    private Boolean reportFlag;
    @Column(name = "reported_patient_id")
    /** 被报告患者主键。 */
    private Long reportedPatientId;
    @Column(name = "org_id")
    /** 机构主键。 */
    private Long orgId;
    @Column(name = "location_id")
    /** 位置主键。 */
    private Long locationId;
    @Column(name = "reason_text")
    /** 原因文本。 */
    private String reasonText;
    @Column(name = "body_site_json")
    /** 身体部位JSON。 */
    private String bodySiteJson;
    @Column(name = "body_structure_id")
    /** 身体结构主键。 */
    private Long bodyStructureId;
    @Column(name = "result_text")
    /** 结果文本。 */
    private String resultText;
    @Column(name = "report_json")
    /** 报告JSON。 */
    private String reportJson;
    @Column(name = "complication_json")
    /** 并发症JSON。 */
    private String complicationJson;
    @Column(name = "follow_up_json")
    /** 随访 JSON。 */
    private String followUpJson;
    @Column(name = "group_id")
    /** 分组主键。 */
    private Long groupId;
    @Column(name = "refund_id")
    /** 退费主键。 */
    private Long refundId;
    @Column(name = "occurrence_slot_active", nullable = false)
    /** 计划点占用标记；CANCEL 对冲后释放。 */
    private Boolean occurrenceSlotActive = true;

    /**
     * 手术时间（医师申请可填；执行拷贝后护士可改；禁止与 {@code occurrence_time} 混用）。
     */
    @Column(name = "procedure_time")
    private LocalDateTime procedureTime;

    /**
     * 麻醉开始时间（护士站麻醉执行弹窗登记；医生开立不填；可空）。
     */
    @Column(name = "anesthesia_start_time")
    private LocalDateTime anesthesiaStartTime;

    /**
     * 麻醉结束时间（护士站麻醉执行弹窗登记；医生开立不填；可空）。
     */
    @Column(name = "anesthesia_end_time")
    private LocalDateTime anesthesiaEndTime;

    /**
     * 切口类别（术中可填；可空）。
     */
    @Column(name = "incision_type_code")
    private String incisionTypeCode;

    /**
     * 是否送检（术中可填；可空）。
     */
    @Column(name = "send_lab_flag")
    private Boolean sendLabFlag;

    /**
     * 样本（护士可填；与备注分列；可空）。
     */
    @Column(name = "specimen")
    private String specimen;

    /**
     * 备注（护士可填；与样本分列；可空）。
     */
    @Column(name = "note")
    private String note;

    /**
     * 校验请求、患者与就诊锚点已绑定。
     *
     * @throws BusinessException 锚点缺失
     */
    public void assertRequestBound() {
        if (this.requestId == null || StringUtils.isEmpty(this.requestTable) || this.patientId == null || this.encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "请求、患者、就诊锚点不能为空");
        }
    }

    /**
     * 将状态置为准备中。
     */
    public void initPreparationStatus() {
        this.statusEnum = EventStatus.PREPARATION.getCode();
    }

    /**
     * 自准备态进入执行中。
     *
     * @throws BusinessException 状态不允许迁移
     */
    public void startExecution() {
        this.assertStateTransitionAllowed(EventStatus.PREPARATION.getCode());
        this.statusEnum = EventStatus.IN_PROGRESS.getCode();
    }

    /**
     * 完成执行并置为已完成。
     *
     * @throws BusinessException 不满足完成条件
     */
    public void complete() {
        this.assertCompletable();
        this.statusEnum = EventStatus.COMPLETED.getCode();
    }

    /**
     * 取消执行并置为 {@link EventStatus#CANCEL} 终态。
     *
     * @throws BusinessException 状态或原因不合法
     */
    public void cancel() {
        this.assertCancelable();
        this.statusEnum = EventStatus.CANCEL.getCode();
    }

    /**
     * 登记计划点不执行，自准备态直接进入 {@link EventStatus#STOPPED} 终态。
     *
     * @throws BusinessException 状态不允许迁移
     */
    public void skip() {
        this.assertStateTransitionAllowed(EventStatus.PREPARATION.getCode());
        this.statusEnum = EventStatus.STOPPED.getCode();
    }

    /**
     * 断言当前状态允许迁出到期望状态。
     *
     * @param expectedStatus 期望的当前状态码
     * @throws BusinessException 状态不一致
     */
    public void assertStateTransitionAllowed(String expectedStatus) {
        if (!expectedStatus.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_STATUS, "执行状态不允许当前迁移");
        }
    }

    /**
     * 断言满足完成条件（执行中且已有结果或报告）。
     *
     * @throws BusinessException 规则不满足
     */
    public void assertCompletable() {
        this.assertStateTransitionAllowed(EventStatus.IN_PROGRESS.getCode());
        if (StringUtils.isEmpty(this.resultText) && StringUtils.isEmpty(this.reportJson)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_RULE_CONFLICT, "完成执行需要结果或报告");
        }
    }

    /**
     * 断言当前允许维护执行人（DM-CLN-004 §6.1.3 / SDD {@code assertPerformerAppendable}）。
     *
     * <p>仅 {@code PREPARATION} / {@code IN_PROGRESS} 可挂入或更新 {@code cln_procedure_performer}。</p>
     *
     * @throws BusinessException 状态不允许维护执行人时
     */
    public void assertPerformerAppendable() {
        boolean appendable = EventStatus.PREPARATION.getCode().equals(this.statusEnum)
            || EventStatus.IN_PROGRESS.getCode().equals(this.statusEnum);
        // 终态（COMPLETED/CANCEL/STOPPED 等）禁止再挂执行人
        if (!appendable) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_STATUS, "当前状态不允许维护执行人");
        }
    }

    /**
     * 断言当前允许取消且已填写取消原因。
     *
     * @throws BusinessException 状态或原因不合法
     */
    public void assertCancelable() {
        boolean canCancel = EventStatus.PREPARATION.getCode().equals(this.statusEnum)
            || EventStatus.IN_PROGRESS.getCode().equals(this.statusEnum);
        if (!canCancel) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_STATUS, "当前状态不允许取消");
        }
        if (StringUtils.isEmpty(this.statusReasonText)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "取消原因不能为空");
        }
    }

    /**
     * 修正客户可见执行时刻（仅 {@code recorded_time}；不改 {@code occurrence_time}）。
     *
     * <p>仅净剩已完成行可改；对齐医嘱执行双轨与护士划价改时（IF-IP-PRC-07）。</p>
     *
     * @param newRecordedTime 新的 recorded_time
     * @throws BusinessException 非 COMPLETED 或时刻为空
     */
    public void amendRecordedTime(LocalDateTime newRecordedTime) {
        if (newRecordedTime == null) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_ARGUMENT, "执行展示时间不能为空");
        }
        // 仅已完成事实允许改展示时刻；CANCEL/准备中不可改
        if (!EventStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.PROCEDURE_INVALID_STATUS, "仅已完成执行记录可修改展示时间");
        }
        this.recordedTime = newRecordedTime;
    }
}
