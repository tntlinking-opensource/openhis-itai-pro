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

/**
 * EMR 与 HIS 的集成适配层（health-application → whale-emr）。
 *
 * <p>职责：实现 {@link com.openhis.whale.emr.domain.metadata.port.EmrMetadataContextPort}，
 * 在文书打开/保存元数据解析时，将患者主数据、就诊主数据及诊断读模型快照写入
 * {@code Map&lt;String, String&gt;} 扁平上下文，供 {@code MetadataValueResolver} 按
 * {@code source_path} 取值。</p>
 *
 * <p>与功能清单 12.1 对齐：宿主传入 {@code patientId}/{@code encounterId}，本包不负责
 * 患者/就诊列表；无效 ID 静默跳过，由元数据字段 {@code default_value} 兜底。</p>
 *
 * <p>契约：读模型 {@code diagnosis_json} 仅在应用层内存解析，产出 {@code diagnosis.*}
 * 展示键；禁止向 HTTP Response 透出原始 JSON（规范 B.6.2）。</p>
 *
 * @see com.openhis.health.application.emr.metadata.HealthEmrMetadataContextAdapter
 * @see com.openhis.health.application.emr.metadata.EmrDiagnosisMetadataContributor
 */
package com.openhis.health.application.emr.metadata;
