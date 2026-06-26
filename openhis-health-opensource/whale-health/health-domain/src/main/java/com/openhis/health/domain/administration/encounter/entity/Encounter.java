/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.administration.encounter.entity;

import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

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
        if (StringUtils.isBlank(this.busNo)) {
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
     * 断言当前状态允许取消。
     *
     * <p>门诊允许退号的前置状态：REGISTERED（已挂号未接诊）、
     * TRIAGED（已分诊但医生未接诊）、IN_PROGRESS（接诊中）、DISCHARGED（完诊）。
     * PLANNED（待挂号/预结算未缴费）不走退号域动作，由应用侧引导完成缴费或清理草稿。</p>
     *
     * <p>住院（IMP）取消登记须走 {@link #assertInpatientAdmissionCancelable(boolean)}，不在此方法内处理。</p>
     */
    public void assertCancelable() {
        if (EncounterClass.IMP.getCode().equals(this.classEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "住院取消登记请使用专用域动作");
        }
        // 门诊：待挂号（PLANNED）禁止退号；已挂号至完诊允许退号。
        if (!EncounterStatus.REGISTERED.getCode().equals(this.statusEnum)
            && !EncounterStatus.TRIAGED.getCode().equals(this.statusEnum)
            && !EncounterStatus.IN_PROGRESS.getCode().equals(this.statusEnum)
            && !EncounterStatus.DISCHARGED.getCode().equals(this.statusEnum)) {
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
     * 迁移到暂离状态（住院：出院医嘱开立等）。
     */
    public void transitToOnHold() {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.ON_HOLD.getCode();
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
     * 出院召回（已清床轨）：已出院 → 待入科，清空出院时间与离院方式（DM-ADM-002 §6.1.25）。
     *
     * <p>豁免 BR-ENC-003：{@code DISCHARGED} 为业务终态，仅本方法允许拉回 {@code REGISTERED}；
     * 活动病区由领域服务 INSERT，床/参与者须护士二次分床补齐。</p>
     */
    public void recallFromDischargedToRegistered() {
        this.assertClass(EncounterClass.IMP.getCode());
        if (!EncounterStatus.DISCHARGED.getCode().equals(this.statusEnum)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS,
                "仅已出院状态允许已清床轨出院召回");
        }
        this.statusEnum = EncounterStatus.REGISTERED.getCode();
        this.endTime = null;
        this.outWayCode = null;
    }

    /**
     * 迁移到诊毕/出院状态。
     *
     * @param completedAt 完成时间
     */
    public void transitToDischarged(LocalDateTime completedAt) {
        this.assertNotTerminal();
        this.statusEnum = EncounterStatus.DISCHARGED.getCode();
        this.endTime = completedAt;
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

