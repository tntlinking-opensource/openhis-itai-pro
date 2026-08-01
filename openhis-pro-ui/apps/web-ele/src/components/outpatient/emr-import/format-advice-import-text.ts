import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';

import { formatMedicationDoseSummaryLine } from '#/utils/doctor-station/medication-dose-display';
import { resolveAdviceQuantityUnitLabel } from '#/utils/doctor-station/advice-quantity-unit-display';
import { preserveApiIdForRequest } from '#/utils/format/preserve-api-id';
import { buildServiceApplyFormExtensionText } from '#/utils/service-apply-display';

/** 医嘱带入行 */
export interface AdviceImportItem {
  key: string;
  /** 列表展示主文案 */
  title: string;
  /** 副文案（剂量/用法等） */
  subtitle: string;
  statusEnum: string;
  statusText: string;
  /** 是否默认勾选 */
  defaultChecked: boolean;
  /** 插入用正文行（可多行，嘱托另起一行） */
  lineText: string;
  groupId?: string;
  sortNumber: number;
}

function trimText(v: unknown): string {
  if (v === null || v === undefined) return '';
  return String(v).trim();
}

function upperStatus(row: DoctorStationAdviceRowDto): string {
  return trimText(row.statusEnum).toUpperCase();
}

function isStoppedStatus(status: string): boolean {
  return status === 'STOPPED' || status === 'CANCELLED';
}

function isDraftStatus(status: string): boolean {
  return status === 'DRAFT';
}

function inferResourceType(dto: DoctorStationAdviceRowDto): string {
  const raw = trimText(dto.resourceType).toUpperCase();
  if (raw) return raw;
  const cat = trimText(dto.catalogCategoryCode);
  if (cat === '11') return 'DEVICE';
  if (cat === '21' || cat.startsWith('21') || cat === '52' || cat === '26') return 'SERVICE';
  if (cat === '2' || cat === '3' || cat === '4') return 'MEDICATION';
  return '';
}

function adviceRowKey(row: DoctorStationAdviceRowDto, index: number): string {
  return trimText(row.listRowKey) || preserveApiIdForRequest(row.requestId) || `adv-${index}`;
}

function buildMedicationDetail(dto: DoctorStationAdviceRowDto): string {
  const dose = formatMedicationDoseSummaryLine({
    dosageDisplay: dto.dosageDisplay,
    dose: dto.dose,
    doseUnitCode: dto.doseUnitCode,
    doseUnitName: dto.doseUnitName,
    splitDose: dto.splitDose,
    splitDoseUnitCode: dto.splitDoseUnitCode,
    splitDoseUnitName: dto.splitDoseUnitName,
    quantity: dto.quantity,
    unitCode: dto.unitCode,
    unitName: dto.unitName,
    resolveTotalUnitLabel: resolveAdviceQuantityUnitLabel,
  }).replace(/。$/u, '');
  const method = trimText(dto.methodName);
  const rate = trimText(dto.rateName);
  const durRaw = dto.dispensePerDuration;
  let dayPart = '';
  if (durRaw !== null && durRaw !== undefined && String(durRaw).trim() !== '') {
    const d = Number(durRaw);
    if (Number.isFinite(d) && d > 0) dayPart = `×${Math.trunc(d)}天`;
  }
  const parts = [dose, method, rate, dayPart].filter(Boolean);
  return parts.join(' ');
}

function buildServiceDetail(dto: DoctorStationAdviceRowDto): string {
  const typeLabel =
    trimText(dto.activityTypeName) ||
    trimText(dto.catalogCategoryName) ||
    (trimText(dto.serviceCategoryEnum) === '52' ? '嘱托' : '');
  const ext = buildServiceApplyFormExtensionText(
    {
      remark: dto.remark,
      serviceRequestDetails: dto.serviceRequestDetails,
    },
    [],
  );
  const parts = [typeLabel ? `（${typeLabel}）` : '', ext].filter(Boolean);
  return parts.join(' ');
}

function buildDeviceDetail(dto: DoctorStationAdviceRowDto): string {
  const qty = trimText(dto.quantity);
  if (!qty) return '';
  const unit = resolveAdviceQuantityUnitLabel(dto.unitName, dto.unitCode);
  return `×${qty}${unit}`;
}

function buildStatusPrefix(status: string): string {
  if (isDraftStatus(status)) return '[草稿] ';
  if (status === 'STOPPED') return '[已停] ';
  if (status === 'CANCELLED') return '[已取消] ';
  return '';
}

function buildAdviceBodyLine(dto: DoctorStationAdviceRowDto): string {
  const entrust = trimText(dto.entrustContent);
  if (entrust && (trimText(dto.serviceCategoryEnum) === '52' || !trimText(dto.adviceDisplayName))) {
    return `嘱托：${entrust}`;
  }

  const name = trimText(dto.adviceDisplayName) || trimText(dto.contentSummary) || '未命名医嘱';
  const resourceType = inferResourceType(dto);
  let detail = '';
  if (resourceType === 'MEDICATION') {
    detail = buildMedicationDetail(dto);
  } else if (resourceType === 'DEVICE') {
    detail = buildDeviceDetail(dto);
  } else if (resourceType === 'SERVICE') {
    detail = buildServiceDetail(dto);
  } else {
    detail = trimText(dto.contentSummary);
    if (detail === name) detail = '';
  }

  const prefix = buildStatusPrefix(upperStatus(dto));
  const main = detail ? `${prefix}${name} ${detail}` : `${prefix}${name}`;
  if (entrust && !main.includes(`嘱托：${entrust}`)) {
    return `${main}\n嘱托：${entrust}`;
  }
  return main;
}

function statusDisplayText(dto: DoctorStationAdviceRowDto): string {
  return trimText(dto.statusEnumText) || trimText(dto.statusEnum) || '—';
}

/** 将医嘱 list 行转为带入勾选项 */
export function buildAdviceImportItems(rows: DoctorStationAdviceRowDto[]): AdviceImportItem[] {
  const items = rows.map((row, index) => {
    const status = upperStatus(row);
    const title = trimText(row.adviceDisplayName) || trimText(row.contentSummary) || '未命名医嘱';
    const lineText = buildAdviceBodyLine(row);
    const subtitle = lineText.startsWith(title)
      ? lineText.slice(title.length).trim()
      : lineText.replace(title, '').trim();
    return {
      key: adviceRowKey(row, index),
      title,
      subtitle,
      statusEnum: status,
      statusText: statusDisplayText(row),
      defaultChecked: !isStoppedStatus(status),
      lineText,
      groupId: trimText(row.groupId) || trimText(row.packageId) || undefined,
      sortNumber: Number(row.sortNumber ?? index) || index,
    };
  });

  return items.toSorted((a, b) => {
    if (a.sortNumber !== b.sortNumber) return a.sortNumber - b.sortNumber;
    return a.key.localeCompare(b.key);
  });
}

/**
 * BR-IMPORT-ADV-01：勾选医嘱 → 多行纯文本。
 * 同 groupId 连续行用「同组：A；B」合并为一行编号。
 */
export function formatAdviceImportText(items: AdviceImportItem[]): string {
  if (items.length === 0) return '';

  const lines: string[] = [];
  let i = 0;
  let seq = 1;
  while (i < items.length) {
    const current = items[i]!;
    const gid = current.groupId;
    if (gid) {
      const group: AdviceImportItem[] = [current];
      let j = i + 1;
      while (j < items.length && items[j]!.groupId === gid) {
        group.push(items[j]!);
        j += 1;
      }
      if (group.length > 1) {
        const joined = group.map((g) => g.lineText.replace(/\n/g, '；')).join('；');
        lines.push(`${seq}. 同组：${joined}`);
        seq += 1;
        i = j;
        continue;
      }
    }
    const bodyLines = current.lineText.split('\n');
    lines.push(`${seq}. ${bodyLines[0] ?? ''}`);
    for (let k = 1; k < bodyLines.length; k += 1) {
      lines.push(`   ${bodyLines[k]}`);
    }
    seq += 1;
    i += 1;
  }

  return lines.join('\n');
}
