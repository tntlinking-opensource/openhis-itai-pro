import type { DoctorStationPatient } from '../doctorPatientStore';

import type { DoctorStationAdviceRowDto } from '#/api/doctor-station/advice.types';
import type { TcmHerbGridDisplayRow } from '#/utils/tcm-prescription-display';
import type { PrescriptionSheetData } from '#/views/outpatient/dispense/components/prescription-sheet.types';
import type {
  DispensePrescriptionBlock,
  DispensePrescriptionLine,
  DispenseQueuePatient,
} from '#/views/outpatient/dispense/components/types';

import { formatDateTime } from '@vben/utils';

import { buildPrescriptionSheetData } from '#/views/outpatient/dispense/components/build-prescription-sheet-data';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import { unionDiagnosisDisplayNamesOrDash } from '#/utils/diagnosis/union-diagnosis-display-names';
import { resolvePrintHospitalName } from '#/utils/print/resolve-print-hospital-name';

/** 医嘱行上可选中药字段（列表契约可能回填） */
type AdviceRowTcmExtras = {
  tcmFlag?: boolean | null;
  categoryCode?: string;
  dosageInstructionText?: string;
  dosageInstructionName?: string;
  /** 中药付数（方头；勿与 quantity 混淆） */
  chineseHerbsDoseQuantity?: null | number | string;
};

type AdviceRowForSheet = DoctorStationAdviceRowDto & AdviceRowTcmExtras;

/** 是否中药医嘱行 */
function isTcmAdviceRow(row: AdviceRowForSheet): boolean {
  if (row.tcmFlag === true) return true;
  if (row.tcmFlag === false) return false;
  const categoryCode = String(row.catalogCategoryCode ?? row.categoryCode ?? '').trim();
  return categoryCode === HERBAL_MEDICATION_CATEGORY_CODE;
}

function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) {
    return '';
  }
  return String(value).trim();
}

/** 单味饮片展示：药名 + 单味剂量 */
function formatTcmHerbDisplayFromAdviceRow(row: AdviceRowForSheet): string {
  const name = String(row.adviceDisplayName ?? row.contentSummary ?? '').trim();
  const dose = scalarToTrimmedString(row.dose);
  const doseUnit = String(row.doseUnitName ?? row.doseUnitCode ?? '').trim();
  if (name && dose) {
    return doseUnit ? `${name} ${dose}${doseUnit}` : `${name} ${dose}`;
  }
  return name;
}

/** 单味特殊煎法展示名 */
function resolveDecoctionNote(row: AdviceRowForSheet): string {
  const direct = String(row.dosageInstructionText ?? row.dosageInstructionName ?? '').trim();
  if (direct) return direct;
  const attrs = row.requestExtensions?.attributes;
  if (attrs && typeof attrs === 'object') {
    const fromAttr = String(
      (attrs as Record<string, unknown>).dosageInstructionText ??
        (attrs as Record<string, unknown>).dosageInstructionName ??
        '',
    ).trim();
    if (fromAttr) return fromAttr;
  }
  return '';
}

/** 医嘱行 → 四列饮片网格（与发药页中药处方笺一致） */
function buildTcmHerbGridRowsFromAdviceRows(
  rows: readonly AdviceRowForSheet[],
): TcmHerbGridDisplayRow[] {
  const cells = rows.map((row) => {
    const note = resolveDecoctionNote(row);
    return {
      herbDisplay: formatTcmHerbDisplayFromAdviceRow(row),
      decoctionNote: note ? `(${note})` : '',
    };
  });
  const cols = 4;
  const gridRows: TcmHerbGridDisplayRow[] = [];
  for (let i = 0; i < cells.length; i += cols) {
    const chunk = cells.slice(i, i + cols);
    gridRows.push({
      col1: chunk[0]?.herbDisplay ?? '',
      note1: chunk[0]?.decoctionNote ?? '',
      col2: chunk[1]?.herbDisplay ?? '',
      note2: chunk[1]?.decoctionNote ?? '',
      col3: chunk[2]?.herbDisplay ?? '',
      note3: chunk[2]?.decoctionNote ?? '',
      col4: chunk[3]?.herbDisplay ?? '',
      note4: chunk[3]?.decoctionNote ?? '',
    });
  }
  return gridRows;
}

/**
 * 付数展示：只用方头 chineseHerbsDoseQuantity。
 * 禁止用 quantity——那是单剂×付数的请求总量（如 100g），会错显成「100付」。
 */
function formatTcmDoseCountFromAdviceHead(head: AdviceRowForSheet): string {
  const doseCount = scalarToTrimmedString(head.chineseHerbsDoseQuantity);
  if (!doseCount) return '';
  return `${doseCount}付`;
}

/** 付数 + 频次 + 用法（单行，与发药页一致） */
function buildTcmUsageTextFromAdviceHead(head: AdviceRowForSheet): string {
  const parts: string[] = [];
  const doseCount = formatTcmDoseCountFromAdviceHead(head);
  if (doseCount) parts.push(doseCount);
  const freq = String(head.rateName ?? head.rateCode ?? '').trim();
  if (freq) parts.push(freq);
  const route = String(head.methodName ?? head.methodCode ?? '').trim();
  if (route) parts.push(route);
  return parts.join('  ');
}

/** 医嘱行数量展示 */
function formatAdviceQuantityLabel(row: DoctorStationAdviceRowDto): string {
  const q = scalarToTrimmedString(row.quantity);
  const u = String(row.unitName ?? row.unitCode ?? '').trim();
  if (!q && !u) {
    return '—';
  }
  if (!u) {
    return q || '—';
  }
  if (!q) {
    return u;
  }
  return `${q}${u}`;
}

/** 医嘱行用法用量展示（医生站笺面：口服 每日3次 每次2粒） */
function formatAdviceFreqLabel(row: DoctorStationAdviceRowDto): string {
  const parts: string[] = [];
  const method = String(row.methodName ?? row.methodCode ?? '').trim();
  if (method) {
    parts.push(method);
  }
  const rate = String(row.rateName ?? row.rateCode ?? '').trim();
  if (rate) {
    parts.push(rate);
  }
  const dose = scalarToTrimmedString(row.dose);
  const doseUnit = String(row.doseUnitName ?? row.doseUnitCode ?? '').trim();
  if (dose) {
    parts.push(doseUnit ? `每次${dose}${doseUnit}` : `每次${dose}`);
  }
  const days = scalarToTrimmedString(row.dispensePerDuration);
  if (days) {
    parts.push(`${days}天`);
  }
  return parts.join(' ') || '—';
}

/** 医嘱行 → 处方明细行 */
function mapAdviceRowToPrescriptionLine(row: AdviceRowForSheet): DispensePrescriptionLine {
  const amountRaw = row.lineDisplayTotalPrice ?? row.lineTotalAmount;
  const amount =
    amountRaw !== null && amountRaw !== undefined && scalarToTrimmedString(amountRaw) !== ''
      ? String(amountRaw)
      : '—';
  const decoction = resolveDecoctionNote(row);
  return {
    id: String(row.requestId ?? row.listRowKey ?? '').trim() || `row-${Date.now()}`,
    itemName: String(row.adviceDisplayName ?? row.contentSummary ?? '').trim() || '—',
    quantity: formatAdviceQuantityLabel(row),
    spec: String(row.specificationText ?? '').trim() || '—',
    traceCode: '—',
    batchNo: '—',
    amount,
    pharmacy: '—',
    factory: '—',
    diagnosis:
      String(
        row.conditionDefinitionName ?? row.diagnosisSummary ?? row.boundConditionName ?? '',
      ).trim() || '—',
    freq: formatAdviceFreqLabel(row),
    dosageInstructionText: decoction || undefined,
  };
}

/** 医嘱药品行 → 处方块（按 prescriptionNo 分块） */
export function mapAdviceRowsToPrescriptionBlocks(
  rows: readonly DoctorStationAdviceRowDto[],
): DispensePrescriptionBlock[] {
  const groups = new Map<string, AdviceRowForSheet[]>();
  for (const raw of rows) {
    const row = raw as AdviceRowForSheet;
    const tcm = isTcmAdviceRow(row);
    const prescriptionNo = String(row.prescriptionNo ?? '').trim();
    const requestId = String(row.requestId ?? '').trim();
    const key = prescriptionNo
      ? `${prescriptionNo}|${tcm ? 't' : 'w'}`
      : `request|${requestId || row.listRowKey || groups.size}`;
    const list = groups.get(key);
    if (list) {
      list.push(row);
    } else {
      groups.set(key, [row]);
    }
  }

  const blocks: DispensePrescriptionBlock[] = [];
  for (const [, groupRows] of groups) {
    groupRows.sort((a, b) =>
      String(a.reqOpenedTime ?? '').localeCompare(String(b.reqOpenedTime ?? '')),
    );
    const head = groupRows[0];
    if (!head) {
      continue;
    }
    const tcm = isTcmAdviceRow(head);
    const rxNo = String(head.prescriptionNo ?? '').trim() || '—';
    const issuedRaw = String(head.reqOpenedTime ?? '').trim();
    const issued = issuedRaw ? formatDateTime(issuedRaw) || issuedRaw : '—';
    const items = groupRows.map((line) => mapAdviceRowToPrescriptionLine(line));
    // 西药：同方诊断并集去重（医生站预览/打印与药房一致）
    if (!tcm && items.length > 0) {
      const unionDiagnosis = unionDiagnosisDisplayNamesOrDash(
        ...items.map((item) => item.diagnosis),
      );
      for (const item of items) {
        item.diagnosis = unionDiagnosis;
      }
    }
    blocks.push({
      id: `block-${String(head.requestId ?? head.listRowKey ?? rxNo)}-${rxNo}`,
      rxNo,
      rxType: tcm ? 'herbal' : 'western',
      deptName: '—',
      // 开单医生 / 医师：authoredByName ← med_medication_request.requester_id
      doctorName: String(head.authoredByName ?? '').trim() || '—',
      // 费用性质：后端 feeNatureText（ContractCategory 文案）
      feeNatureLabel: String(head.feeNatureText ?? '').trim() || undefined,
      issuedAt: issued,
      status: 'completed',
      items,
      ...(tcm
        ? {
            tcmHerbGridRows: buildTcmHerbGridRowsFromAdviceRows(groupRows),
            tcmUsageText: buildTcmUsageTextFromAdviceHead(head),
          }
        : {}),
    });
  }
  blocks.sort((a, b) => a.issuedAt.localeCompare(b.issuedAt));
  return blocks;
}

/** 医生站患者卡片 → 处方单展示用队列患者模型（与门诊药房一致） */
export function mapDoctorStationPatientToDispenseQueuePatient(
  patient: DoctorStationPatient,
): DispenseQueuePatient {
  const ageRaw = (patient.ageText ?? '').trim();
  const ageLabel = ageRaw.includes('岁') ? ageRaw : ageRaw ? `${ageRaw}岁` : '—';
  const start = patient.startTime ? formatDateTime(patient.startTime) : '';
  return {
    id: patient.encounterId,
    patientName: (patient.patientName ?? '').trim() || '—',
    genderLabel: (patient.genderEnumText ?? '').trim() || '',
    ageLabel,
    organizationName: (patient.organizationName ?? '').trim() || undefined,
    visitNo: (patient.encounterBusNo ?? '').trim() || (patient.encounterId ?? '').trim() || '—',
    visitTimeLabel: start || '—',
    patientPhone: (patient.phone ?? '').trim() || undefined,
    // 费用性质：与患者卡片同源（contractDisplay），供笺面/打印回显
    feeNatureLabel: (patient.contractDisplay ?? '').trim() || undefined,
    rxCount: 0,
  };
}

/** 历史 Tab 门诊号：优先就诊 id 展示 */
function resolveHistoryOutpatientNo(
  patientVisitNo: string,
  headRow: DoctorStationAdviceRowDto | undefined,
): string {
  const enc = String(headRow?.encounterId ?? '').trim();
  if (enc) {
    return enc;
  }
  return patientVisitNo.trim() || '—';
}

/** 历史 Tab 下按就诊 id 覆盖门诊号展示 */
function resolveOutpatientNoForBlock(
  patient: DispenseQueuePatient,
  headRow: DoctorStationAdviceRowDto | undefined,
  useHistoryEncounterNo: boolean,
): string {
  if (useHistoryEncounterNo) {
    return resolveHistoryOutpatientNo(patient.visitNo, headRow);
  }
  return patient.visitNo.trim() || '—';
}

/** 处方块 + 患者上下文 → 纸质处方单展示数据（与发药工作站笺面同组件；医生站不展示发药状态章） */
export function buildDoctorStationPrescriptionSheetData(
  patient: DispenseQueuePatient,
  block: DispensePrescriptionBlock,
  headRow: DoctorStationAdviceRowDto | undefined,
  options?: {
    useHistoryEncounterNo?: boolean;
    /** 中药方头诊断（病证合并）；未传时中药诊断显示 — */
    tcmDiagnosisText?: string;
  },
): PrescriptionSheetData {
  const patientForSheet: DispenseQueuePatient = {
    ...patient,
    visitNo: resolveOutpatientNoForBlock(patient, headRow, options?.useHistoryEncounterNo === true),
  };
  const deptFromBlock = (block.deptName ?? '').trim();
  const deptFallback = (patient.organizationName ?? '').trim();
  const isHerbal = block.rxType === 'herbal';
  // 费用性质优先患者信息（与卡片 contractDisplay 一致），不依赖医嘱行 feeNatureText
  const patientFeeNature = (patient.feeNatureLabel ?? '').trim();
  // 开单医生/医师用 block.doctorName；调配/发药/划价（收费人）未回补前为空显示 —
  const data = buildPrescriptionSheetData(patientForSheet, block, {
    hospitalName: resolvePrintHospitalName() || undefined,
    // 医生站页眉用「处方单」，不用发药工作站的「发药单」
    documentTitle: isHerbal ? '中药处方单' : '处方单',
    tcmDiagnosisText: options?.tcmDiagnosisText,
    feeNatureLabel: patientFeeNature || undefined,
  });
  return {
    ...data,
    // 医生站查看不走发药态，去掉笺面状态章
    status: undefined,
    statusLabel: undefined,
    deptName: deptFromBlock && deptFromBlock !== '—' ? deptFromBlock : deptFallback || '—',
  };
}

/** 按处方块首行 requestId 定位组首行医嘱 */
export function findHeadAdviceRowForBlock(
  block: DispensePrescriptionBlock,
  rows: readonly DoctorStationAdviceRowDto[],
): DoctorStationAdviceRowDto | undefined {
  const headRequestId = String(block.items[0]?.id ?? '').trim();
  if (headRequestId) {
    return rows.find((row) => String(row.requestId ?? '').trim() === headRequestId);
  }
  return rows[0];
}
