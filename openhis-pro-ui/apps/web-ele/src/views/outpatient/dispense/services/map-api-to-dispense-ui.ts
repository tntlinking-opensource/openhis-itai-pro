import type { DispensePrescriptionBlock, DispenseQueuePatient } from '../components/types';

import type {
  EncounterMedicationDispenseSlipRowDto,
  MedicationDispenseInventoryDetailDto,
  PendingDispenseEncounterQueueRowDto,
} from '#/api/outpatient-pharmacy-dispense/outpatient-pharmacy-dispense.types';

import { formatDateTime } from '@vben/utils';

import { unionDiagnosisDisplayNamesOrDash } from '#/utils/diagnosis/union-diagnosis-display-names';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { formatMedicationNameWithMerchandise } from '#/utils/medication/format-medication-name-with-merchandise';
import {
  buildTcmHerbGridRowsFromSlipRows,
  buildTcmPrescriptionUsageTextFromSlipHead,
} from '#/utils/tcm-prescription-display';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';

import { encodeInventoryPick } from './inventory-pick-codec';

/** 接口 number/string 统一为展示用字符串 */
function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/**
 * 代煎数量展示：优先方头付数（与笺面「N付」一致），再回落收费数量；
 * 仅数量、不拼单位码（单位码如「6」曾被拼成 X26）。
 */
function formatDecoctionQuantityLabel(
  head: EncounterMedicationDispenseSlipRowDto,
): string | undefined {
  // 付数为业务真源；收费数量在同方残留费用行加总时可能翻倍，不宜优先
  const doseQty = scalarToTrimmedString(head.chineseHerbsDoseQuantity);
  if (doseQty) return doseQty;
  const qty = scalarToTrimmedString(head.decoctionChargeQuantity);
  return qty || undefined;
}

/** 从方头 slip 解析代煎费展示字段（同处方号各行一致，取首行即可） */
function resolveDecoctionChargeDisplay(head: EncounterMedicationDispenseSlipRowDto): {
  decoctionActivityName?: string;
  decoctionChargeAmountLabel?: string;
  decoctionChargeQuantityLabel?: string;
  sufferingFlag?: boolean;
} {
  const sufferingFlag = head.sufferingFlag === true;
  const amountRaw = scalarToTrimmedString(head.decoctionChargeAmount);
  // 未代煎且无金额时不写块级字段
  if (!sufferingFlag && !amountRaw) {
    return {};
  }
  return {
    sufferingFlag: sufferingFlag || undefined,
    decoctionActivityName:
      head.decoctionActivityName?.trim() || (sufferingFlag || amountRaw ? '代煎中药' : undefined),
    decoctionChargeAmountLabel: amountRaw
      ? formatMoneyDisplay(head.decoctionChargeAmount)
      : undefined,
    decoctionChargeQuantityLabel: formatDecoctionQuantityLabel(head),
  };
}

/** 是否按中药分组/展示（仅认 tcmFlag / medicationCategoryCode） */
export function isHerbalSlipRow(row: EncounterMedicationDispenseSlipRowDto): boolean {
  if (row.tcmFlag === true) return true;
  if (row.tcmFlag === false) return false;
  return String(row.medicationCategoryCode ?? '').trim() === HERBAL_MEDICATION_CATEGORY_CODE;
}

/** 队列行 DTO → 左侧列表患者模型（性别文案只用后端 genderEnumText） */
export function mapPendingQueueRowToPatient(
  row: PendingDispenseEncounterQueueRowDto,
): DispenseQueuePatient {
  const start = row.startTime ? formatDateTime(row.startTime) : '';
  return {
    id: row.encounterId,
    patientId: row.patientId?.trim() || undefined,
    patientName: row.patientName?.trim() || '—',
    genderLabel: row.genderEnumText?.trim() || '',
    ageLabel: row.ageText?.trim() || '—',
    organizationName: row.organizationName?.trim() || undefined,
    visitNo: row.encounterBusNo?.trim() || '—',
    visitTimeLabel: start || '—',
    patientPhone: row.patientPhone?.trim() || undefined,
    feeNatureLabel: row.contractDisplay?.trim() || undefined,
    rxCount: 0,
  };
}

/** 同一处方组内根据发放状态推导组状态标签 */
function blockStatusFromRows(
  rows: EncounterMedicationDispenseSlipRowDto[],
): 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared' {
  const set = new Set(rows.map((r) => r.statusEnum));
  if (set.has('PREPARATION')) return 'pending';
  // 部分已发 + 部分已配未发（PART_COMPLETED 等混合态）
  if (set.has('COMPLETED') && (set.has('IN_PROGRESS') || set.has('PREPARED'))) return 'partial';
  // 保存配药后后端为 IN_PROGRESS（待发药），与 PREPARED（已配药）同属可核发态
  if (set.has('IN_PROGRESS') || set.has('PREPARED')) return 'prepared';
  if (set.has('COMPLETED')) return 'completed';
  return 'blocking';
}

/** 处方区块标题：仅 med_medication_request.prescription_no，禁止回落发放 busNo */
function resolvePrescriptionBlockNo(row: EncounterMedicationDispenseSlipRowDto): string {
  const prescriptionNo = row.prescriptionNo?.trim();
  if (prescriptionNo) {
    return prescriptionNo;
  }
  return '—';
}

/** 将发放行列表聚合成处方块（按 prescription_no 分块；无处方号时按发放行兜底） */
export function mapSlipRowsToPrescriptionBlocks(
  rows: EncounterMedicationDispenseSlipRowDto[],
): DispensePrescriptionBlock[] {
  const groups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
  for (const r of rows) {
    const tcm = isHerbalSlipRow(r);
    const prescriptionNo = r.prescriptionNo?.trim();
    const key = prescriptionNo
      ? `${prescriptionNo}|${tcm ? 't' : 'w'}`
      : `dispense|${r.dispenseId}`;
    const list = groups.get(key);
    if (list) list.push(r);
    else groups.set(key, [r]);
  }

  const blocks: DispensePrescriptionBlock[] = [];
  for (const [, groupRows] of groups) {
    groupRows.sort((a, b) => (a.requestOpenedTime ?? '').localeCompare(b.requestOpenedTime ?? ''));
    const head = groupRows[0];
    if (!head) continue;
    const tcm = isHerbalSlipRow(head);
    const rxNo = resolvePrescriptionBlockNo(head);
    const issued =
      head.requestOpenedTime !== null &&
      head.requestOpenedTime !== undefined &&
      head.requestOpenedTime !== ''
        ? formatDateTime(head.requestOpenedTime)
        : '—';
    const items = groupRows.map((line) => {
      const invList = line.inventoryDetailList ?? [];
      const inventoryPickOptions =
        invList.length > 0
          ? invList.map((inv) => ({
              value: encodeInventoryPick({
                pharmacyOrganizationId: String(inv.pharmacyOrganizationId),
                lotNumber: inv.lotNumber,
              }),
              label: formatInventoryOptionLabel(inv),
            }))
          : undefined;
      return {
        id: line.dispenseId,
        medicationId: String(line.medicationId ?? '').trim() || undefined,
        itemName:
          formatMedicationNameWithMerchandise(line.medicationName, line.merchandiseName) || '—',
        quantity: formatDispenseQuantityLabel(line),
        unitCode: String(line.unitCode ?? '').trim() || undefined,
        quantityValue: parseSlipQuantityValue(line.quantity),
        packageUnitCode: String(line.packageUnitCode ?? '').trim() || undefined,
        packageUnitRatio: parseOptionalPositiveNumber(line.packageUnitRatio),
        splitUnitCode: String(line.splitUnitCode ?? '').trim() || undefined,
        // 主档贴码快照：待扫个数换算真源
        traceUnitCode: String(line.traceUnitCode ?? '').trim() || undefined,
        traceUnitRatio: parseOptionalPositiveNumber(line.traceUnitRatio),
        lotHasTraceMaster: line.lotHasTraceMaster === true,
        spec: line.specificationText?.trim() || '—',
        traceCode: line.traceNo?.trim() || '—',
        batchNo: line.lotNumber?.trim() || '—',
        amount:
          line.totalPrice !== null &&
          line.totalPrice !== undefined &&
          scalarToTrimmedString(line.totalPrice) !== ''
            ? formatMoneyDisplay(line.totalPrice)
            : '—',
        pharmacy:
          line.dispenseLocationName?.trim() ||
          (line.dispenseLocationId ? `库房 ${line.dispenseLocationId}` : '—'),
        dispenseLocationId: String(line.dispenseLocationId ?? '').trim() || undefined,
        inventoryPickOptions,
        factory: line.manufacturerName?.trim() || '—',
        diagnosis: line.conditionSummaryText?.trim() || '—',
        freq: line.frequencyText?.trim() || line.frequencyCode?.trim() || '—',
        dosageInstructionText:
          line.dosageInstructionText?.trim() || line.dosageInstructionCode?.trim() || '—',
      };
    });
    // 西药：同方各明细诊断强制写成并集去重文案（与后端方头口径一致）
    if (!tcm && items.length > 0) {
      const unionDiagnosis = unionDiagnosisDisplayNamesOrDash(
        ...items.map((item) => item.diagnosis),
      );
      for (const item of items) {
        item.diagnosis = unionDiagnosis;
      }
    }
    blocks.push({
      id: `block-${head.dispenseId}-${rxNo}`,
      rxNo,
      rxType: tcm ? 'herbal' : 'western',
      deptName: head.orderingDepartmentName?.trim() || '—',
      // 开单医生 / 医师
      doctorName: head.orderingPractitionerName?.trim() || '—',
      // 调配 / 发药 / 划价（与打印 PrintData 同源）
      preparerPractitionerName: head.preparerPractitionerName?.trim() || undefined,
      dispensingPractitionerName: head.dispensingPractitionerName?.trim() || undefined,
      pricingPractitionerName: head.pricingPractitionerName?.trim() || undefined,
      // 费用性质：接口 feeNatureText；空时由 buildPrescriptionSheetData 用患者信息补偿
      feeNatureLabel: head.feeNatureText?.trim() || undefined,
      // 患者手机号（打印 enrich 回填）
      patientPhone: head.patientPhone?.trim() || undefined,
      issuedAt: issued,
      status: blockStatusFromRows(groupRows),
      items,
      ...(tcm
        ? {
            tcmHerbGridRows: buildTcmHerbGridRowsFromSlipRows(groupRows),
            tcmUsageText: buildTcmPrescriptionUsageTextFromSlipHead(head),
            ...resolveDecoctionChargeDisplay(head),
          }
        : {}),
    });
  }
  blocks.sort((a, b) => a.issuedAt.localeCompare(b.issuedAt));
  return blocks;
}

/** 列表「数量」列：绑定 slip.quantity，后接单位文案（unitText 缺省时回退 doseUnitText） */
function formatDispenseQuantityLabel(line: EncounterMedicationDispenseSlipRowDto): string {
  const q = scalarToTrimmedString(line.quantity);
  const u = line.unitText?.trim() || line.doseUnitText?.trim() || '';
  if (!q && !u) return '—';
  if (!u) return q || '—';
  if (!q) return u;
  return `${q}${u}`;
}

/** slip.quantity → 数值；无效时 undefined */
function parseSlipQuantityValue(raw: null | number | string | undefined): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return n;
}

/** 可选正数（包装倍数等）；无效时 undefined */
function parseOptionalPositiveNumber(raw: null | number | string | undefined): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return n;
}

function formatInventoryOptionLabel(inv: MedicationDispenseInventoryDetailDto): string {
  const name = inv.pharmacyDisplayName?.trim() || `库房 ${inv.pharmacyOrganizationId}`;
  const lot = inv.lotNumber?.trim();
  return lot ? `${name} / 批号 ${lot}` : name;
}
