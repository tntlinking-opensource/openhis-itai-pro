import type {
  EncounterMedicationDispenseSlipRowDto,
  PendingDispenseInpatientEncounterQueueRowDto,
} from '#/api/inpatient-pharmacy-dispense/inpatient-pharmacy-dispense.types';

import { formatDateTime } from '@vben/utils';

import { decimalAdd } from '#/utils/format/decimal-util';
import { formatMoneyDisplay } from '#/utils/format/money-scale';
import { resolveAdministrativeGenderLabel } from '#/utils/gender/resolve-administrative-gender-label';
import { formatMedicationNameWithMerchandise } from '#/utils/medication/format-medication-name-with-merchandise';
import { unionDiagnosisDisplayNamesOrDash } from '#/utils/diagnosis/union-diagnosis-display-names';

import type { DispensePrescriptionBlock } from '#/views/outpatient/dispense/components/types';
import { formatDispenseRemainingStockText } from '#/views/outpatient/dispense/services/format-dispense-remaining-stock';
import { formatDispenseUsageInstructionText } from '#/views/outpatient/dispense/services/map-api-to-dispense-ui';

import type { InpatientDispenseQueuePatient } from '../components/types';
import { HERBAL_MEDICATION_CATEGORY_CODE } from '#/views/outpatient/doctor-station/advice/services/advice-tab.constants';
import {
  buildTcmHerbGridRowsFromSlipRows,
  buildTcmPrescriptionUsageTextFromSlipHead,
} from '#/utils/tcm-prescription-display';

/** 无护士 recorded_time 时的虚拟日桶标题（仍按 medReq 合并明细） */
const UNRECORDED_DAY_SHEET_LABEL = '未登记执行时间';

/** 接口 number/string 统一为展示用字符串 */
function scalarToTrimmedString(value: null | number | string | undefined): string {
  if (value === null || value === undefined) return '';
  return String(value).trim();
}

/** 是否按中药分组/展示 */
function isHerbalSlipRow(row: EncounterMedicationDispenseSlipRowDto): boolean {
  if (row.tcmFlag === true) return true;
  if (row.tcmFlag === false) return false;
  return String(row.medicationCategoryCode ?? '').trim() === HERBAL_MEDICATION_CATEGORY_CODE;
}

/** 住院队列行 DTO → 左侧列表患者模型 */
export function mapInpatientPendingQueueRowToPatient(
  row: PendingDispenseInpatientEncounterQueueRowDto,
): InpatientDispenseQueuePatient {
  const start = row.startTime ? formatDateTime(row.startTime) : '';
  // 费用性质：透传后端合同名；空则卡片组件回落「自费」
  const feeNature = row.contractDisplay?.trim() || undefined;
  return {
    id: row.encounterId,
    patientId: row.patientId?.trim() || undefined,
    patientName: row.patientName?.trim() || '—',
    // 性别：队列仅有 genderEnum code，映射为展示文案供笺面回显
    genderText: resolveAdministrativeGenderLabel(undefined, row.genderEnum, ''),
    ageLabel: row.ageText?.trim() || '—',
    hospitalizationNo: row.encounterBusNo?.trim() || '—',
    wardName: row.wardName?.trim() || '—',
    bedName: row.bedName?.trim() || '—',
    admissionTimeLabel: start || '—',
    attendingDoctorName: row.attendingDoctorName?.trim() || '—',
    statusText: row.statusEnumText?.trim() || '—',
    feeNatureLabel: feeNature,
  };
}

/** 同一日发药单内根据发放状态推导组状态标签 */
function blockStatusFromRows(
  rows: EncounterMedicationDispenseSlipRowDto[],
): 'blocking' | 'completed' | 'partial' | 'pending' | 'prepared' {
  const set = new Set(rows.map((r) => r.statusEnum));
  if (set.has('PREPARATION')) return 'pending';
  if (set.has('COMPLETED') && (set.has('IN_PROGRESS') || set.has('PREPARED'))) return 'partial';
  if (set.has('IN_PROGRESS') || set.has('PREPARED')) return 'prepared';
  if (set.has('COMPLETED')) return 'completed';
  return 'blocking';
}

/**
 * 护士执行 recorded_time → 自然日键（yyyy-MM-dd）。
 * 空则返回空串，由调用方落入「未登记执行时间」虚拟日桶。
 */
function resolveRecordedDayKey(row: EncounterMedicationDispenseSlipRowDto): string {
  const raw = scalarToTrimmedString(row.recordedTime);
  if (!raw) return '';
  // 接口多为 yyyy-MM-dd HH:mm:ss；取前 10 位作日键
  if (raw.length >= 10) return raw.slice(0, 10);
  return raw;
}

/** 日键 → 发药单标题（虚拟单号，非库表单据号） */
function resolveDaySheetTitle(dayKey: string): string {
  if (!dayKey) return UNRECORDED_DAY_SHEET_LABEL;
  return dayKey;
}

/**
 * 将发放行列表聚合成「按日虚拟发药单」块。
 *
 * <p>分块键：护士执行 {@code recordedTime} 自然日（与汇总领药 planned 轴无关）。
 * 块内明细：同 {@code medReqId} 多次执行合并为一行并累加数量；不同医嘱即使同药也分行。
 * 提交/扫码仍映射底层多条 {@code dispenseId}（见 {@code sourceDispenseIds}）。</p>
 */
export function mapInpatientSlipRowsToPrescriptionBlocks(
  rows: EncounterMedicationDispenseSlipRowDto[],
): DispensePrescriptionBlock[] {
  // 先按日+中西药分桶（与门诊处方块中西隔离同构；接口通常已按 tab 过滤）
  const dayGroups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
  for (const r of rows) {
    const dayKey = resolveRecordedDayKey(r);
    const kind = isHerbalSlipRow(r) ? 't' : 'w';
    const bucketKey = `${dayKey}|${kind}`;
    const list = dayGroups.get(bucketKey);
    if (list) list.push(r);
    else dayGroups.set(bucketKey, [r]);
  }

  const blocks: DispensePrescriptionBlock[] = [];
  for (const [bucketKey, dayRows] of dayGroups) {
    const dayKey = bucketKey.includes('|')
      ? bucketKey.slice(0, bucketKey.lastIndexOf('|'))
      : bucketKey;
    // 同日内按医嘱合并展示行（执行多次 → 数量累加）
    const medReqGroups = new Map<string, EncounterMedicationDispenseSlipRowDto[]>();
    for (const r of dayRows) {
      const medReqId = scalarToTrimmedString(r.medReqId);
      // 无医嘱主键时按发放行兜底，避免误合并不同药
      const mergeKey = medReqId || `dispense|${r.dispenseId}`;
      const list = medReqGroups.get(mergeKey);
      if (list) list.push(r);
      else medReqGroups.set(mergeKey, [r]);
    }

    const mergedItems: DispensePrescriptionBlock['items'] = [];
    const allSlipRowsForStatus: EncounterMedicationDispenseSlipRowDto[] = [];
    let headForBlock: EncounterMedicationDispenseSlipRowDto | undefined;
    let tcmBlock = false;

    for (const [, groupRows] of medReqGroups) {
      // 同医嘱多次执行按 recorded 再按 dispenseId 稳定排序，便于追溯码按序拆分
      groupRows.sort((a, b) => {
        const ta = scalarToTrimmedString(a.recordedTime);
        const tb = scalarToTrimmedString(b.recordedTime);
        if (ta !== tb) return ta.localeCompare(tb);
        return String(a.dispenseId).localeCompare(String(b.dispenseId));
      });
      const head = groupRows[0];
      if (!head) continue;
      if (!headForBlock) headForBlock = head;
      allSlipRowsForStatus.push(...groupRows);
      const tcm = isHerbalSlipRow(head);
      if (tcm) tcmBlock = true;

      const sourceDispenseIds = groupRows.map((line) => String(line.dispenseId));
      let quantitySum = 0;
      let amountSum: null | number = null;
      for (const line of groupRows) {
        const q = parseSlipQuantityValue(line.quantity);
        if (q !== undefined) quantitySum = decimalAdd(quantitySum, q);
        const priceRaw = scalarToTrimmedString(line.totalPrice);
        if (priceRaw !== '') {
          amountSum = amountSum === null ? Number(priceRaw) : decimalAdd(amountSum, priceRaw);
        }
      }
      const unitText = head.unitText?.trim() || '';
      const quantityLabel =
        quantitySum > 0
          ? unitText
            ? `${quantitySum}${unitText}`
            : String(quantitySum)
          : formatDispenseQuantityLabel(head);

      // 追溯码展示：多行已有码用逗号并集；扫码会话键用展示行 id（首条 dispenseId）
      const boundTraces = groupRows
        .map((line) => line.traceNo?.trim())
        .filter((code): code is string => Boolean(code) && code !== '—');
      const traceCode = boundTraces.length > 0 ? boundTraces.join(',') : '—';

      mergedItems.push({
        id: String(head.dispenseId),
        sourceDispenseIds,
        medicationId: String(head.medicationId ?? '').trim() || undefined,
        itemName:
          formatMedicationNameWithMerchandise(head.medicationName, head.merchandiseName) || '—',
        quantity: quantityLabel,
        unitCode: String(head.unitCode ?? '').trim() || undefined,
        unitText: unitText || undefined,
        quantityValue: quantitySum > 0 ? quantitySum : parseSlipQuantityValue(head.quantity),
        packageUnitCode: String(head.packageUnitCode ?? '').trim() || undefined,
        packageUnitRatio: parseOptionalPositiveNumber(head.packageUnitRatio),
        splitUnitCode: String(head.splitUnitCode ?? '').trim() || undefined,
        // 主档贴码快照：待扫个数按合并后 quantityValue 换算
        traceUnitCode: String(head.traceUnitCode ?? '').trim() || undefined,
        traceUnitCodeText: String(head.traceUnitCodeText ?? '').trim() || undefined,
        traceUnitRatio: parseOptionalPositiveNumber(head.traceUnitRatio),
        lotHasTraceMaster: head.lotHasTraceMaster === true,
        // 剩余库存：后端 remainingQuantity（锚定库房×批号）换算展示
        remainingStockText: formatDispenseRemainingStockText(head.remainingQuantity, {
          unitCode: head.unitCode,
          unitText: head.unitText,
          packageUnitCode: head.packageUnitCode,
          packageUnitRatio: head.packageUnitRatio,
          splitUnitCode: head.splitUnitCode,
          doseUnitCode: head.doseUnitCode,
          doseUnitText: head.doseUnitText,
        }),
        spec: head.specificationText?.trim() || '—',
        // 包装↔拆零比例：服务端拼装，规格列后展示（与门诊发药同源）
        packageSplitRatioText: head.packageSplitRatioText?.trim() || undefined,
        traceCode,
        batchNo: head.lotNumber?.trim() || '—',
        amount:
          amountSum !== null && Number.isFinite(amountSum) ? formatMoneyDisplay(amountSum) : '—',
        pharmacy:
          head.dispenseLocationName?.trim() ||
          (head.dispenseLocationId ? `库房 ${head.dispenseLocationId}` : '—'),
        dispenseLocationId: String(head.dispenseLocationId ?? '').trim() || undefined,
        factory: head.manufacturerName?.trim() || '—',
        diagnosis: head.conditionSummaryText?.trim() || '—',
        // 用法+频次+剂量+天数（与门诊发药笺面同源）
        freq: formatDispenseUsageInstructionText(head),
        dosageInstructionText:
          head.dosageInstructionText?.trim() || head.dosageInstructionCode?.trim() || '—',
      });
    }

    if (!headForBlock || mergedItems.length === 0) continue;

    // 西药：同日诊断并集去重，覆盖行级单诊
    if (!tcmBlock && mergedItems.length > 0) {
      const unionDiagnosis = unionDiagnosisDisplayNamesOrDash(
        ...mergedItems.map((item) => item.diagnosis),
      );
      for (const item of mergedItems) {
        item.diagnosis = unionDiagnosis;
      }
    }

    const sheetTitle = resolveDaySheetTitle(dayKey);
    const issued = dayKey
      ? dayKey
      : headForBlock.recordedTime
        ? formatDateTime(headForBlock.recordedTime)
        : '—';

    blocks.push({
      id: `day-${dayKey || 'unrecorded'}-${headForBlock.dispenseId}`,
      rxNo: sheetTitle,
      rxType: tcmBlock ? 'herbal' : 'western',
      deptName: headForBlock.orderingDepartmentName?.trim() || '—',
      doctorName: headForBlock.orderingPractitionerName?.trim() || '—',
      // 调配 / 发药 / 划价（与门诊笺面/打印同源）
      preparerPractitionerName: headForBlock.preparerPractitionerName?.trim() || undefined,
      dispensingPractitionerName: headForBlock.dispensingPractitionerName?.trim() || undefined,
      pricingPractitionerName: headForBlock.pricingPractitionerName?.trim() || undefined,
      feeNatureLabel: headForBlock.feeNatureText?.trim() || undefined,
      visitTypeLabel: headForBlock.encounterMedicationTypeText?.trim() || undefined,
      // 发放单号：码上放心上传键（日单取头行）
      dispenseBusNo: headForBlock.busNo?.trim() || undefined,
      patientPhone: headForBlock.patientPhone?.trim() || undefined,
      issuedAt: issued,
      status: blockStatusFromRows(allSlipRowsForStatus),
      items: mergedItems,
      ...(tcmBlock
        ? {
            tcmHerbGridRows: buildTcmHerbGridRowsFromSlipRows(dayRows),
            tcmUsageText: buildTcmPrescriptionUsageTextFromSlipHead(headForBlock),
          }
        : {}),
    });
  }

  // 有 recorded 的日升序；未登记桶沉底
  blocks.sort((a, b) => {
    const aUnrecorded = a.rxNo === UNRECORDED_DAY_SHEET_LABEL;
    const bUnrecorded = b.rxNo === UNRECORDED_DAY_SHEET_LABEL;
    if (aUnrecorded !== bUnrecorded) return aUnrecorded ? 1 : -1;
    return a.issuedAt.localeCompare(b.issuedAt);
  });
  return blocks;
}

/** 列表「数量」列：quantity + unitText */
function formatDispenseQuantityLabel(line: EncounterMedicationDispenseSlipRowDto): string {
  const q = scalarToTrimmedString(line.quantity);
  const u = line.unitText?.trim() || '';
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

/** 可选正数（包装倍数/主档贴码比）；无效时 undefined */
function parseOptionalPositiveNumber(raw: null | number | string | undefined): number | undefined {
  if (raw === null || raw === undefined || raw === '') return undefined;
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return undefined;
  return n;
}

/** 按中西药 tab 过滤发放行（与接口 tcmOnly/westernOnly 语义一致） */
export function filterInpatientSlipRowsByRxKind(
  rows: EncounterMedicationDispenseSlipRowDto[],
  kind: 'herbal' | 'western',
): EncounterMedicationDispenseSlipRowDto[] {
  return rows.filter((row) => (kind === 'herbal' ? isHerbalSlipRow(row) : !isHerbalSlipRow(row)));
}

/**
 * 根据全量 slip 统计发药单（按日虚拟块）角标并推断默认 tab。
 */
export function computeInpatientRxKindSummaryFromSlips(
  slipList: EncounterMedicationDispenseSlipRowDto[],
): { badgeCounts: { herbal: number; western: number }; defaultKind: 'herbal' | 'western' } {
  const blocks = mapInpatientSlipRowsToPrescriptionBlocks(slipList);
  let western = 0;
  let herbal = 0;
  for (const block of blocks) {
    if (block.rxType === 'herbal') herbal += 1;
    else western += 1;
  }
  const defaultKind: 'herbal' | 'western' = western === 0 && herbal > 0 ? 'herbal' : 'western';
  return {
    defaultKind,
    badgeCounts: { western, herbal },
  };
}
