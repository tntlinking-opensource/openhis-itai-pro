/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.elep;

import com.openhis.health.infrastructure.integration.elep.ExtensionIn.CloneElepPrescriptionExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepMedResultQueryExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepQueryExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepRevokeExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepUploadExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionNoExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionIn.PatientElepHistoryQueryExtensionIn;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepMedExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepPageExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.CloneElepPrescriptionExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepQueryExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepRevokeExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepRxNoQueryExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.ElepUploadExtensionOut;
import com.openhis.health.infrastructure.integration.elep.ExtensionOut.PatientElepPrescriptionSummaryQueryExtensionOut;

import java.util.Optional;

/**
 * 医生站 ↔ 第三方电子处方扩展点（省份/厂商无关）。
 */
public interface ElepPrescriptionExtension {

    /**
     * 电子处方扩展是否已装配。
     *
     * @return false 时医生站不展示上传/续方入口
     */
    default boolean isEnabled() {
        return false;
    }

    /**
     * 是否由本扩展处理该处方组（按处方类型/合同等判定）。
     *
     * @param prescriptionGroupId 处方组主键
     * @return true 时走电子处方出站流程
     */
    default boolean supportsPrescription(Long prescriptionGroupId) {
        return false;
    }

    /**
     * 上传电子处方至第三方/医保平台。
     *
     * @param command 就诊、合同与处方号
     * @return 上传结果含 hi_rxno
     * @throws UnsupportedOperationException 未装配时
     */
    default ElepUploadExtensionOut upload(ElepUploadExtensionIn command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    /**
     * 撤销已上传电子处方。
     *
     * @param command 处方标识与撤销原因
     * @return 撤销结果
     * @throws UnsupportedOperationException 未装配时
     */
    default ElepRevokeExtensionOut revoke(ElepRevokeExtensionIn command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    /**
     * 查询电子处方平台状态。
     *
     * @param command 处方或 hi_rxno
     * @return 状态码与使用状态
     * @throws UnsupportedOperationException 未装配时
     */
    default ElepQueryExtensionOut query(ElepQueryExtensionIn command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    /**
     * 查询取药结果。
     *
     * @param command 合同与 hi_rxno
     * @return 取药结果 JSON
     * @throws UnsupportedOperationException 未装配时
     */
    default ElepMedExtensionOut queryMedResult(ElepMedResultQueryExtensionIn command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }

    /**
     * 按处方组主键查已上传的 hi_rxno 快照。
     *
     * @param prescriptionGroupId 处方组主键
     * @return 快照；未上传或未装配时为空
     */
    default Optional<ElepRxNoQueryExtensionOut> findRxNoByPrescription(ElepRxNoByPrescriptionExtensionIn query) {
        return Optional.empty();
    }

    /** 2204 挂接：按 HIS {@code prescription_no} 查已上传方的 {@code hi_rxno}。 */
    default Optional<ElepRxNoQueryExtensionOut> findRxNoByPrescriptionNo(ElepRxNoByPrescriptionNoExtensionIn query) {
        return Optional.empty();
    }

    /**
     * 分页查询患者历史电子处方。
     *
     * @param query 患者、合同与分页参数
     * @return 历史处方摘要页
     */
    default ElepPageExtensionOut<PatientElepPrescriptionSummaryQueryExtensionOut> listPatientHistory(PatientElepHistoryQueryExtensionIn query) {
        return ElepPageExtensionOut.empty();
    }

    /**
     * 套用/续方：克隆处方到新就诊。
     *
     * @param command 源处方与目标就诊
     * @return 新草稿摘要
     * @throws UnsupportedOperationException 未装配时
     */
    default CloneElepPrescriptionExtensionOut cloneToNewEncounter(CloneElepPrescriptionExtensionIn command) {
        throw new UnsupportedOperationException("电子处方未装配");
    }
}
