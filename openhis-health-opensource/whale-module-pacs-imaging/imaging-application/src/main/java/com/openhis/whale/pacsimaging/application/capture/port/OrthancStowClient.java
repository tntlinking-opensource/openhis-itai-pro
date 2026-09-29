/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.capture.port;

/**
 * Orthanc STOW-RS 出站端口（CAP-09）；应用层禁止直接引用 Orthanc SDK。
 */
public interface OrthancStowClient {

    /**
     * 将 DICOM 字节提交至 Orthanc 归档。
     *
     * @param dicomBytes DICOM 文件字节
     * @return STOW 结果（含 Study/Series UID）
     */
    StowResult store(byte[] dicomBytes);

    /**
     * STOW 出站结果。
     *
     * @param studyInstanceUid Study Instance UID（必填）
     * @param seriesInstanceUid Series Instance UID（可选）
     */
    record StowResult(String studyInstanceUid, String seriesInstanceUid) {
    }
}
