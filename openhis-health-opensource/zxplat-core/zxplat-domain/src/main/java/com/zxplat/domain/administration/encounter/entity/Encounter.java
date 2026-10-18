/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.entity;

import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import com.zxplat.framework.core.application.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 就诊聚合根实体。
 */
@Getter
@Setter
@Entity
@Table(name = "adm_encounter")
public class Encounter extends BaseAuditedEntity {

    /** patient 标识。 */
    @Column(name = "patient_id", nullable = false)
    private Long patientId;

    /** group 标识。 */
    @Column(name = "group_id")
    private Long groupId;

    /** bus 编号。 */
    @Column(name = "bus_no", nullable = false)
    private String busNo;

    /** status 枚举值。 */
    @Column(name = "status_enum", nullable = false)
    private String statusEnum;

    /** class 枚举值。 */
    @Column(name = "class_enum", nullable = false)
    private String classEnum;

    /** ybClass 枚举值。 */
    @Column(name = "yb_class_enum")
    private String ybClassEnum;

    /** ybClassText。 */
    @Column(name = "yb_class_text")
    private String ybClassText;

    /** class JSON 快照。 */
    @Column(name = "class_json")
    private String classJson;

    /** priority 枚举值。 */
    @Column(name = "priority_enum")
    private String priorityEnum;

    /** type 枚举值。 */
    @Column(name = "type_enum")
    private String typeEnum;

    /** serviceType 标识。 */
    @Column(name = "service_type_id")
    private Long serviceTypeId;

    /** subjectStatus 枚举值。 */
    @Column(name = "subject_status_enum")
    private String subjectStatusEnum;

    /** start 时间。 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** end 时间。 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** organization 标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** displayOrder。 */
    @Column(name = "display_order")
    private Integer displayOrder;

    /** first 枚举值。 */
    @Column(name = "first_enum")
    private String firstEnum;

    /** admitSource 代码。 */
    @Column(name = "admit_source_code")
    private String admitSourceCode;

    /** inWay 代码。 */
    @Column(name = "in_way_code")
    private String inWayCode;

    /** reception 时间。 */
    @Column(name = "reception_time")
    private LocalDateTime receptionTime;

    /** registrar 标识。 */
    @Column(name = "registrar_id")
    private Long registrarId;

    /** ambEncounter 标识。 */
    @Column(name = "amb_encounter_id")
    private Long ambEncounterId;

    /** outWay 代码。 */
    @Column(name = "out_way_code")
    private String outWayCode;

    /**
     * 入院办理意向（{@link com.zxplat.domain.share.enums.ChargeIntent#getCode()}）。
     *
     * <p>医生转住院时提示款台准备医保办/自费办；非真实医保入院或费用性质，登记合同仍以款台为准。</p>
     */
    @Column(name = "charge_intent")
    private String chargeIntent;

    /** 乐观锁版本号（与 adm_encounter.version 对齐；与 GAP-ATD-004/005 悲观行锁叠加）。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 断言当前就诊不处于终止态，适用于所有状态迁移方法的前置保护（BR-ENC-003）。
     */
    public void assertNotTerminal() {
        if (this.statusEnum == null) {
            return;
        }
        EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, this.statusEnum);
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "就诊已处于终止态（" + current.name() + "），不允许再次状态迁移");
        }
    }

    /**
     * 断言当前就诊已结算完成，允许执行清床离院（DM-ADM-002 §2.3.1：COMPLETED → DISCHARGED）。
     */
    public void assertClearBedAllowed() {
        if (!EncounterStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅结算完成（COMPLETED）状态可执行清床");
        }
    }

    /**
     * 校验门诊开立必填字段。
     */
    public void assertOutpatientRegistrationRequiredFields() {
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "机构科室标识不能为空");
        }
        if (this.serviceTypeId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "号源类型标识不能为空");
        }
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊业务号不能为空");
        }
    }

    /**
     * 初始化门诊已计划状态。
     */
    public void initOutpatientPlanned() {
        this.classEnum = EncounterClass.AMB.getCode();
        this.statusEnum = EncounterStatus.PLANNED.getCode();
    }

    /**
     * 初始化体检在检状态（EncounterClass.PE + IN_PROGRESS，BR-REG-04）。
     */
    public void initPhysicalExamInProgress() {
        this.classEnum = EncounterClass.PE.getCode();
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
    }

    /**
     * 体检总检完成后结束就诊：{@code PE + IN_PROGRESS} → {@code COMPLETED}（BR-MAS-05）。
     *
     * <p>枚举无 FINISHED；通用 finished 语义落地为 {@link EncounterStatus#COMPLETED}。</p>
     */
    public void finishPhysicalExam() {
        this.assertClass(EncounterClass.PE.getCode());
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(this.statusEnum)) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅在检中的体检就诊可结束");
        }
        this.statusEnum = EncounterStatus.COMPLETED.getCode();
        this.endTime = LocalDateTime.now();
    }

    /**
     * 校验体检登记必填字段（不要求号源类型）。
     */
    public void assertPhysicalExamRegistrationRequiredFields() {
        if (this.patientId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者标识不能为空");
        }
        if (this.organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "体检中心机构标识不能为空");
        }
        if (StringUtils.isEmpty(this.busNo)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊业务号不能为空");
        }
    }

    /**
     * 初始化住院已计划状态。
     */
    public void initInpatientPlanned() {
        this.classEnum = EncounterClass.IMP.getCode();
        this.statusEnum = EncounterStatus.PLANNED.getCode();
    }

    /**
     * 初始化住院待入科状态（无申请直登 INSERT 时使用）。
     */
    public void initInpatientRegistered() {
        this.classEnum = EncounterClass.IMP.getCode();
        this.statusEnum = EncounterStatus.REGISTERED.getCode();
    }

    /**
     * 断言当前状态允许接诊。
     *
     * <p>门诊（AMB）：须已完成挂号缴费（REGISTERED）或已分诊（TRIAGED），禁止对「待挂号」态（PLANNED）接诊。</p>
     */
    public void assertReceptionAllowed() {
        this.assertClass(EncounterClass.AMB.getCode());
        if (!EncounterStatus.REGISTERED.getCode().equals(this.statusEnum)
            && !EncounterStatus.TRIAGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许接诊");
        }
    }

    /**
     * 断言当前状态允许暂离。
     */
    public void assertCanMarkOnHold() {
        this.assertClass(EncounterClass.AMB.getCode(), EncounterClass.IMP.getCode());
        this.assertStatus(EncounterStatus.IN_PROGRESS.getCode());
    }

    /**
     * 断言当前状态允许完诊/出院。
     */
    public void assertCompletable() {
        this.assertClass(EncounterClass.AMB.getCode(), EncounterClass.IMP.getCode());
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(this.statusEnum)
            && !EncounterStatus.ON_HOLD.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许完诊/出院");
        }
    }

    /**
     * 断言当前就诊状态允许执行住院登记（有申请路径：仅 PLANNED）。
     *
     * @throws BusinessException 当前状态不允许住院登记
     */
    public void assertInpatientRegistrationAllowed() {
        if (!EncounterStatus.PLANNED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许住院登记");
        }
    }

    /**
     * 断言当前住院就诊允许分床（DM-ADM-002 §6.1.17 待入科）。
     *
     * <p>要求 {@code IMP} 且 {@code status_enum=REGISTERED}。</p>
     *
     * @throws BusinessException 类别非住院或状态非待入科时
     */
    public void assertInpatientBedAssignmentAllowed() {
        this.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.REGISTERED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许分床");
        }
    }

    /**
     * 断言当前住院就诊允许转床或互换床（DM-ADM-002 §6.1.18 / §6.1.18a）。
     *
     * <p>要求 {@code IMP} 且 {@code status_enum=IN_PROGRESS}（已入科在院）。</p>
     *
     * @throws BusinessException 类别非住院或状态非在院执行态时
     */
    public void assertInpatientBedTransferAllowed() {
        this.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许转床");
        }
    }

    /**
     * 断言当前住院就诊允许取消登记（DM §6.1.23 {@code cancelInpatientAdmission}）。
     *
     * <p>允许 {@code PLANNED}（医生申请后未登记）、{@code REGISTERED}（已登记未分床）。</p>
     *
     * @param hasAssignedBed 是否存在活动 {@code BED} 形态位置
     */
    public void assertInpatientAdmissionCancelable(boolean hasAssignedBed) {
        this.assertClass(EncounterClass.IMP.getCode());
        if (hasAssignedBed) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "已分床不可取消住院登记");
        }
        if (EncounterStatus.PLANNED.getCode().equals(this.statusEnum)
            || EncounterStatus.REGISTERED.getCode().equals(this.statusEnum)) {
            return;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前住院状态不允许取消");
    }

    /**
     * 断言当前状态允许取消接诊（回退待诊，供窗口退号前由医生同意）。
     *
     * <p>门诊：仅 {@code IN_PROGRESS}/{@code ON_HOLD}/{@code DISCHARGED} 可取消接诊；
     * 与账单无关，语义为「医生同意退号」。本诊病历/诊断/医嘱须已删净由应用层 IF-DOR-06 Pre 校验，
     * 本方法不跨聚合断言。</p>
     */
    public void assertCancelReceptionAllowed() {
        this.assertClass(EncounterClass.AMB.getCode());
        // 仅已接诊后的状态允许回退：在诊、暂离、完诊。
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(this.statusEnum)
            && !EncounterStatus.ON_HOLD.getCode().equals(this.statusEnum)
            && !EncounterStatus.DISCHARGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许取消接诊");
        }
    }

    /**
     * 取消门诊接诊：回到已挂号待诊，清空接诊/完诊时刻。
     *
     * <p>允许自在诊/暂离/完诊回退；完诊为业务终态，本方法豁免终止态拦截（对齐重新接诊路径）。
     * 接诊医生参与者收口由领域服务在聚合外子表完成。</p>
     */
    public void cancelOutpatientReception() {
        this.assertCancelReceptionAllowed();
        // 统一回到已挂号，窗口方可按 BR-REG-008 继续退号。
        this.statusEnum = EncounterStatus.REGISTERED.getCode();
        this.receptionTime = null;
        this.endTime = null;
    }

    /**
     * 断言当前状态允许取消。
     *
     * <p>门诊允许退号的前置状态：仅 REGISTERED（已挂号未接诊）、TRIAGED（已分诊未接诊）。
     * 已接诊（IN_PROGRESS/ON_HOLD）或完诊（DISCHARGED）须先经医生「取消接诊」回退待诊，
     * 再由窗口退号——防止患者问诊后直接找款员退号而医生不知情。</p>
     *
     * <p>PLANNED（待挂号/预结算未缴费）不走退号域动作，由应用侧引导完成缴费或清理草稿。</p>
     *
     * <p>住院（IMP）取消登记须走 {@link #assertInpatientAdmissionCancelable(boolean)}，不在此方法内处理。</p>
     */
    public void assertCancelable() {
        if (EncounterClass.IMP.getCode().equals(this.classEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "住院取消登记请使用专用域动作");
        }
        // 门诊：仅未接诊的已挂号/已分诊可退号；已接诊/完诊须先取消接诊。
        if (!EncounterStatus.REGISTERED.getCode().equals(this.statusEnum)
            && !EncounterStatus.TRIAGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前门诊状态不允许取消");
        }
    }

    /**
     * 迁移到已挂号状态（门诊缴费确认后调用）。
     *
     * <p>前置状态：PLANNED（预挂号已计划）；缴费成功后由应用层通过领域服务驱动此迁移。</p>
     */
    public void transitToRegistered() {
        this.assertNotTerminal();
        this.assertStatus(EncounterStatus.PLANNED.getCode());
        this.statusEnum = EncounterStatus.REGISTERED.getCode();
    }

    /**
     * 住院登记完成：待登记 → 待入科。
     *
     * @param registerAt 登记时间（正式入院时间锚点）
     */
    public void transitToInpatientRegistered(LocalDateTime registerAt) {
        this.assertNotTerminal();
        this.assertClass(EncounterClass.IMP.getCode());
        this.assertStatus(EncounterStatus.PLANNED.getCode());
        this.statusEnum = EncounterStatus.REGISTERED.getCode();
        this.startTime = registerAt;
    }

    /**
     * 迁移到在诊状态，并设置开始时间。
     *
     * @param receptionAt 接诊时间
     */
    public void transitToInProgress(LocalDateTime receptionAt) {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
        this.startTime = receptionAt;
        this.receptionTime = receptionAt;
    }

    /**
     * 门诊暂离后恢复接诊：{@code ON_HOLD} → {@code IN_PROGRESS}。
     */
    public void resumeOutpatientFromOnHold() {
        this.assertClass(EncounterClass.AMB.getCode());
        if (!EncounterStatus.ON_HOLD.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅暂离状态允许恢复接诊");
        }
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
    }

    /**
     * 门诊诊毕后重新接诊（豁免 BR-ENC-003）：{@code DISCHARGED} → {@code IN_PROGRESS}。
     *
     * @param receptionAt 本次重新接诊时间
     */
    public void resumeOutpatientReceptionFromDischarged(LocalDateTime receptionAt) {
        this.assertClass(EncounterClass.AMB.getCode());
        if (!EncounterStatus.DISCHARGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅诊毕状态允许重新接诊");
        }
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
        this.receptionTime = receptionAt;
        this.endTime = null;
    }

    /**
     * 迁移到暂离状态（住院：出院医嘱开立等）。
     */
    public void transitToOnHold() {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.ON_HOLD.getCode();
    }

    /**
     * 撤销转科/出院过渡态：{@code ON_HOLD} → {@code IN_PROGRESS}（GAP-ATD-008）。
     *
     * <p>业务口径：护士<strong>取消校对</strong>出院/转科医嘱（办理出院前）= 还原校对副作用，就诊回在科。
     * 已办理出院的 {@code PENDING} 不在本方法范围，须走 {@link #cancelNurseDischarge(boolean)} / 反结另轨。</p>
     *
     * @param clearDischargeFields 为 true 时清空 {@code outWayCode}（待出院轨撤销）；转科轨传 false
     */
    public void revertHoldToInProgress(boolean clearDischargeFields) {
        this.assertClass(EncounterClass.IMP.getCode());
        // 前置校验：仅待转科/待出院可回在科，防止误改待结算/已结算
        if (!EncounterStatus.ON_HOLD.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅转科/出院过渡态允许撤销回在科");
        }
        // 状态迁移：ON_HOLD → IN_PROGRESS（在科）
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
        // 出院轨：清离院方式，避免读模型仍分流「待出院」
        if (clearDischargeFields) {
            this.outWayCode = null;
        }
    }

    /**
     * 取消护士办理出院：{@code PENDING} → 待出院或已入科（AS-IP-NUR-002 IF-IP-NUR-ATD-08）。
     *
     * <p>对称 {@link #transitToPending()}：清空 {@code end_time}；有有效已校对出院嘱时回 {@code ON_HOLD}
     * 并保留 {@code outWayCode}；无有效出院嘱时回 {@code IN_PROGRESS} 并清空离院方式，避免空待出院假态。</p>
     *
     * @param restoreToInProgress 为 true 时回已入科并清 {@code outWayCode}；为 false 时回待出院
     */
    public void cancelNurseDischarge(boolean restoreToInProgress) {
        this.assertClass(EncounterClass.IMP.getCode());
        // 前置校验：仅待结算可取消办理出院；已结算须走反结
        if (!EncounterStatus.PENDING.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅待结算状态允许取消办理出院");
        }
        // 无有效出院嘱：回已入科，避免 ON_HOLD 无嘱仍展示待出院
        if (restoreToInProgress) {
            this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
            this.endTime = null;
            this.outWayCode = null;
            return;
        }
        // 仍有有效出院嘱：回待出院，保留离院方式供再办出院
        this.statusEnum = EncounterStatus.ON_HOLD.getCode();
        this.endTime = null;
    }

    /**
     * 迁移到待出院结算状态（护士出院执行后）。
     */
    public void transitToPending() {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.PENDING.getCode();
    }

    /**
     * 迁移到结算完成状态（财务终态，临床可仍在院）。
     */
    public void transitToCompleted() {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.COMPLETED.getCode();
    }

    /**
     * 出院召回（未清床轨）：已结算 → 已入科，清空出院时间与离院方式（DM-ADM-002 §6.1.25）。
     *
     * <p>豁免 BR-ENC-003 终止态拦截：仅允许自 {@code COMPLETED} 回退，不清活动床/参与者轨迹。</p>
     */
    public void recallFromCompletedToInProgress() {
        this.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.COMPLETED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅已结算状态允许未清床轨出院召回");
        }
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
        this.endTime = null;
        this.outWayCode = null;
    }

    /**
     * 出院召回（已清床轨）：已离院 → 已入科，清空出院时间与离院方式（DM-ADM-002 §6.1.25）。
     *
     * <p>豁免 BR-ENC-003：{@code DISCHARGED} 为业务终态，仅本方法允许拉回 {@code IN_PROGRESS}；
     * 活动病区由领域服务 INSERT 新行，不新增活动床、不占物理床。</p>
     */
    public void recallFromDischargedToInProgress() {
        this.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.DISCHARGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅已出院状态允许已清床轨出院召回");
        }
        this.statusEnum = EncounterStatus.IN_PROGRESS.getCode();
        this.endTime = null;
        this.outWayCode = null;
    }

    /**
     * 迁移到诊毕/出院状态（清床或无床全结终态）。
     *
     * <p>业务出院时间 {@code end_time} 须在护士办理出院时已写入（医生出院医嘱计划时间）。
     * 清床/无床结算仅推进状态；<strong>已有 {@code end_time} 不得被清床/结算时刻覆盖</strong>
     *（对齐 DM-ADM-002 §6.1.21「若尚未写入则与清床时刻对齐」）。</p>
     *
     * @param completedAt 清床或结算时刻；仅当 {@code end_time} 为空时回落写入
     */
    public void transitToDischarged(LocalDateTime completedAt) {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.DISCHARGED.getCode();
        // 已由办理出院写入计划出院时间：清床/结算不得改写业务出院时间
        if (this.endTime == null) {
            this.endTime = completedAt;
        }
        this.assertTimeRangeLegal();
    }

    /**
     * 迁移到取消状态。
     *
     * <p>完诊（DISCHARGED）为终止态但仍允许窗口退号，故仅此终止态可迁入 CANCELLED。</p>
     */
    public void transitToCancelled() {
        if (this.statusEnum != null) {
            EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, this.statusEnum);
            if (current != null && current.isTerminal() && !EncounterStatus.DISCHARGED.equals(current)) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "就诊已处于终止态（" + current.name() + "），不允许再次状态迁移");
            }
        }
        this.statusEnum = EncounterStatus.CANCELLED.getCode();
    }

    /**
     * 迁移到错误录入终止态。
     *
     * <p>门诊待挂号（PLANNED）窗口「取消」专用：与退号 {@link #transitToCancelled()} 区分，
     * 表示未收费挂单作废而非已挂号后退号。</p>
     */
    public void transitToEnteredInError() {
        if (this.statusEnum != null) {
            EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, this.statusEnum);
            if (current != null && current.isTerminal()) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                    "就诊已处于终止态（" + current.name() + "），不允许再次状态迁移");
            }
        }
        this.statusEnum = EncounterStatus.ENTERED_IN_ERROR.getCode();
    }

    /**
     * 校验就诊时间区间合法性。
     */
    public void assertTimeRangeLegal() {
        if (this.startTime == null || this.endTime == null) {
            return;
        }
        if (this.endTime.isBefore(this.startTime)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT, "结束时间不能早于开始时间");
        }
    }

    /**
     * 断言当前类别在允许集合内。
     *
     * @param allowedClasses 允许的类别编码
     */
    public void assertClass(String... allowedClasses) {
        if (this.classEnum == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "就诊类别不能为空");
        }
        for (String allowedClass : allowedClasses) {
            if (allowedClass.equals(this.classEnum)) {
                return;
            }
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前就诊类别不允许该操作");
    }

    /**
     * 断言当前状态等于指定编码。
     *
     * @param status 目标状态编码
     */
    public void assertStatus(String status) {
        if (!status.equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "当前状态不允许该操作");
        }
    }
}
