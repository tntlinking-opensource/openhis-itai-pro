/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.bridge.lab.pathb;

import java.util.Optional;

/**
 * 按 LIS/MedSync 申请单号（APPLYNO）反查 Whale {@code ServiceRequest}。
 *
 * <p><b>正向（MedSync 外发）：</b>
 * {@code applyNo = firstNonBlank(serviceRequest.prescriptionNo, String.valueOf(serviceRequest.id))}。</p>
 *
 * <p><b>反向（本接口）：</b>
 * <ol>
 *   <li>先按 {@code prescription_no = applyNo} 查未删 {@code ServiceRequest}；</li>
 *   <li>否则若 applyNo 可解析为 Long，再按 {@code ServiceRequest.id} 查。</li>
 * </ol>
 * 命中后返回 serviceRequestId / encounterId / patientId，供 Path B Snapshot 填患者就诊。</p>
 */
public interface YunlisApplyNoServiceRequestResolver {

    /**
     * 按 APPLYNO/BARCODE 反查 ServiceRequest 上下文。
     *
     * @param applyNo MedSync 外发的申请单号（与 yunlis APPLYNO/BARCODE 同源）
     * @return 命中上下文；未找到则 empty
     */
    Optional<YunlisApplyNoResolveResult> resolve(String applyNo);
}
