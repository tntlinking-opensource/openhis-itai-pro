/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.gateway.scheme.audit.assembler;

import com.zxplat.plugin.chs.domain.audit.command.AuditAnalyze3101Command;
import com.zxplat.plugin.chs.domain.audit.command.AuditDiagnoseCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditEncounterCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditOperationCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditOrderCommand;
import com.zxplat.plugin.chs.domain.audit.command.AuditPatientCommand;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101DiagnoseRow;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101EncounterRow;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101Input;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101OperationRow;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101OrderRow;
import com.zxplat.plugin.chs.gateway.scheme.audit.model.Audit3101PatientRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * 【3101】Command → 强类型 Input。
 *
 * <p>集合字段一律发 JSON 数组；手术节点名优先 {@code fsi_operation_dtos}；his 扩展发空 Map。</p>
 */
public final class Audit3101InputAssembler {

    private Audit3101InputAssembler() {
    }

    /**
     * 组装 3101 平台 input。
     *
     * @param command 分析命令
     * @return 强类型 Input
     */
    public static Audit3101Input assemble(AuditAnalyze3101Command command) {
        Audit3101Input.Audit3101Data data = new Audit3101Input.Audit3101Data();
        data.setSyscode(command.getSyscode());
        data.setTaskId(command.getTaskId());
        data.setTrigScen(command.getTrigScen());
        data.setRuleIds(command.getRuleIds());
        data.setPatientDtos(mapPatients(command.getPatients()));
        Audit3101Input input = new Audit3101Input();
        input.setData(data);
        return input;
    }

    /**
     * Command 患者树 → 平台 patient_dtos（his 扩展固定空 Map）。
     *
     * @param patients 领域患者列表
     * @return 平台患者行
     */
    private static List<Audit3101PatientRow> mapPatients(List<AuditPatientCommand> patients) {
        List<Audit3101PatientRow> rows = new ArrayList<>();
        if (patients == null) {
            return rows;
        }
        for (AuditPatientCommand patient : patients) {
            if (patient == null) {
                continue;
            }
            Audit3101PatientRow row = new Audit3101PatientRow();
            row.setPatnId(patient.getPatnId());
            row.setPatnName(patient.getPatnName());
            row.setGend(patient.getGend());
            row.setBrdy(patient.getBrdy());
            row.setPoolarea(patient.getPoolarea());
            row.setCurrMdtrtId(patient.getCurrMdtrtId());
            row.setFsiEncounterDtos(mapEncounters(patient.getEncounters()));
            // 联调要求 fsi_his_data_dto 恒为对象；W1/W2 固定空 Map
            row.setFsiHisDataDto(new HashMap<>());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Command 就诊树 → 平台 fsi_encounter_dtos（含诊断/医嘱/手术子节点）。
     *
     * @param encounters 领域就诊列表
     * @return 平台就诊行
     */
    private static List<Audit3101EncounterRow> mapEncounters(List<AuditEncounterCommand> encounters) {
        List<Audit3101EncounterRow> rows = new ArrayList<>();
        if (encounters == null) {
            return rows;
        }
        for (AuditEncounterCommand encounter : encounters) {
            if (encounter == null) {
                continue;
            }
            Audit3101EncounterRow row = new Audit3101EncounterRow();
            row.setMdtrtId(encounter.getMdtrtId());
            row.setMedinsId(encounter.getMedinsId());
            row.setMedinsName(encounter.getMedinsName());
            row.setMedinsAdmdvs(encounter.getMedinsAdmdvs());
            row.setMedinsType(encounter.getMedinsType());
            row.setMedinsLv(encounter.getMedinsLv());
            row.setWardareaCodg(encounter.getWardareaCodg());
            row.setWardno(encounter.getWardno());
            row.setBedno(encounter.getBedno());
            row.setAdmDate(encounter.getAdmDate());
            row.setDscgDate(encounter.getDscgDate());
            row.setDscgMainDiseCodg(encounter.getDscgMainDiseCodg());
            row.setDscgMainDiseName(encounter.getDscgMainDiseName());
            row.setFsiDiagnoseDtos(mapDiagnoses(encounter.getDiagnoses()));
            row.setDrCodg(encounter.getDrCodg());
            row.setAdmDeptCodg(encounter.getAdmDeptCodg());
            row.setAdmDeptName(encounter.getAdmDeptName());
            row.setDscgDeptCodg(encounter.getDscgDeptCodg());
            row.setDscgDeptName(encounter.getDscgDeptName());
            row.setMedMdtrtType(encounter.getMedMdtrtType());
            row.setMedType(encounter.getMedType());
            row.setFsiOrderDtos(mapOrders(encounter.getOrders()));
            row.setFsiOperationDtos(mapOperations(encounter.getOperations()));
            row.setMatnStas(encounter.getMatnStas());
            row.setMedfeeSumamt(encounter.getMedfeeSumamt());
            row.setOwnpayAmt(encounter.getOwnpayAmt());
            row.setSelfpayAmt(encounter.getSelfpayAmt());
            row.setAcctPayamt(encounter.getAcctPayamt());
            row.setMaAmt(encounter.getMaAmt());
            row.setHifpPayamt(encounter.getHifpPayamt());
            row.setSetlTotlnum(encounter.getSetlTotlnum());
            row.setInsutype(encounter.getInsutype());
            row.setReimFlag(encounter.getReimFlag());
            row.setOutSetlFlag(encounter.getOutSetlFlag());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Command 诊断 → 平台 fsi_diagnose_dtos。
     *
     * @param diagnoses 领域诊断
     * @return 平台诊断行
     */
    private static List<Audit3101DiagnoseRow> mapDiagnoses(List<AuditDiagnoseCommand> diagnoses) {
        List<Audit3101DiagnoseRow> rows = new ArrayList<>();
        if (diagnoses == null) {
            return rows;
        }
        for (AuditDiagnoseCommand diagnose : diagnoses) {
            if (diagnose == null) {
                continue;
            }
            Audit3101DiagnoseRow row = new Audit3101DiagnoseRow();
            row.setDiseId(diagnose.getDiseId());
            row.setInoutDiseType(diagnose.getInoutDiseType());
            row.setMaindiseFlag(diagnose.getMaindiseFlag());
            row.setDiasSrtNo(diagnose.getDiasSrtNo());
            row.setDiseCodg(diagnose.getDiseCodg());
            row.setDiseName(diagnose.getDiseName());
            row.setDiseDate(diagnose.getDiseDate());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Command 医嘱 → 平台 fsi_order_dtos。
     *
     * @param orders 领域医嘱
     * @return 平台医嘱行
     */
    private static List<Audit3101OrderRow> mapOrders(List<AuditOrderCommand> orders) {
        List<Audit3101OrderRow> rows = new ArrayList<>();
        if (orders == null) {
            return rows;
        }
        for (AuditOrderCommand order : orders) {
            if (order == null) {
                continue;
            }
            Audit3101OrderRow row = new Audit3101OrderRow();
            row.setRxId(order.getRxId());
            row.setRxno(order.getRxno());
            row.setGrpno(order.getGrpno());
            row.setLongDrordFlag(order.getLongDrordFlag());
            row.setHilistType(order.getHilistType());
            row.setChrgType(order.getChrgType());
            row.setDrordBhvr(order.getDrordBhvr());
            row.setHilistCode(order.getHilistCode());
            row.setHilistName(order.getHilistName());
            row.setHilistDosform(order.getHilistDosform());
            row.setHilistLv(order.getHilistLv());
            // Pre：目录价/单价/金额统一 2 位（长春 3101 数值长度校验）
            row.setHilistPric(scalePlatformMoney(order.getHilistPric()));
            row.setHosplistCode(order.getHosplistCode());
            row.setHosplistName(order.getHosplistName());
            row.setHosplistDosform(order.getHosplistDosform());
            row.setCnt(order.getCnt());
            row.setPric(scalePlatformMoney(order.getPric()));
            row.setSumamt(scalePlatformMoney(order.getSumamt()));
            row.setOwnpayAmt(order.getOwnpayAmt());
            row.setSelfpayAmt(order.getSelfpayAmt());
            row.setSpec(order.getSpec());
            row.setSpecUnt(order.getSpecUnt());
            row.setDrordBegnDate(order.getDrordBegnDate());
            row.setDrordStopDate(order.getDrordStopDate());
            row.setDrordDeptCodg(order.getDrordDeptCodg());
            row.setDrordDeptName(order.getDrordDeptName());
            row.setDrordDrCodg(order.getDrordDrCodg());
            row.setDrordDrName(order.getDrordDrName());
            row.setDrordDrProfttl(order.getDrordDrProfttl());
            row.setCurrDrordFlag(order.getCurrDrordFlag());
            rows.add(row);
        }
        return rows;
    }

    /**
     * Command 手术 → 平台 fsi_operation_dtos；空则空列表（仍发数组）。
     *
     * @param operations 领域手术
     * @return 平台手术行
     */
    private static List<Audit3101OperationRow> mapOperations(List<AuditOperationCommand> operations) {
        if (operations == null || operations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Audit3101OperationRow> rows = new ArrayList<>();
        for (AuditOperationCommand operation : operations) {
            if (operation == null) {
                continue;
            }
            Audit3101OperationRow row = new Audit3101OperationRow();
            row.setSetlListOprnId(operation.getSetlListOprnId());
            row.setOprnCode(operation.getOprnCode());
            row.setOprnName(operation.getOprnName());
            row.setMainOprnFlag(operation.getMainOprnFlag());
            row.setOprnDate(operation.getOprnDate());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 平台金额/单价：2 位 HALF_UP。
     *
     * @param value 原始金额
     * @return 两位小数；null 原样
     */
    private static java.math.BigDecimal scalePlatformMoney(java.math.BigDecimal value) {
        if (value == null) {
            return null;
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
