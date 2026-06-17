import type { AdviceDrugItem, AdviceProductOption } from './advice.types';

import type { TerminologyOptionDto } from '#/api/doctor-station/advice.types';
import type { EncounterDiagnosisRowDto } from '#/api/doctor-station/diagnosis.types';

import {
  mapEncounterRowToDiagnosisItem,
  pickMainDiagnosisDisplayName,
  resolveConditionDefinitionIdFromEncounterRows,
  resolveEncounterDiagnosisIdFromEncounterRows,
} from '#/components/doctor-station/diagnosis/services/encounter-diagnosis-select';

import { defaultContractSelection, firstTerminologyCode, terminologyLabel } from './advice-format';
import {
  defaultTotalAmountUnitCode,
  doseConvertRatioSplitToDose,
  resolveAdviceLineUnitPrice,
  roundMedicationQty,
  totalAmountSplitToSalesFactor,
  unitCodeByBandKind,
  unitNameByBandKind,
} from './medication-unit-bands';

/** 编辑卡：新建行、选品回填、诊断映射、表单数值 */

export interface WesternProductSelectPatchOptions {
  /** 总量默认计量带：门诊与住院临时为 PACKAGE，住院长期默认 SALES */
  totalUnitDefaultKind?: 'PACKAGE' | 'SALES';
}

export interface CreateAdviceDrugItemContext {
  feeNatureOptions: TerminologyOptionDto[];
  medicationRateOptions: TerminologyOptionDto[];
  medicationRouteOptions: TerminologyOptionDto[];
}

export function createAdviceDrugItem(
  seed: Partial<AdviceDrugItem> | undefined,
  ctx: CreateAdviceDrugItemContext,
): AdviceDrugItem {
  const s = seed as Partial<AdviceDrugItem> | undefined;
  const freqSeed = seed?.frequency;
  const usageSeed = seed?.usage;
  const frequency =
    freqSeed !== undefined && String(freqSeed).trim() !== ''
      ? String(freqSeed)
      : firstTerminologyCode(ctx.medicationRateOptions);
  const usage =
    usageSeed !== undefined && String(usageSeed).trim() !== ''
      ? String(usageSeed)
      : firstTerminologyCode(ctx.medicationRouteOptions);

  function numFromSeed(key: keyof AdviceDrugItem): number | undefined {
    if (s === undefined || !Object.prototype.hasOwnProperty.call(s, key)) {
      return undefined;
    }
    const v = s[key];
    if (v === undefined || v === null || v === '') return undefined;
    const x = Number(v);
    return Number.isFinite(x) ? x : undefined;
  }

  const contractFromSeed = String(seed?.contractId ?? '').trim();
  const feeFromSeed = String(seed?.feeNature ?? '').trim();
  let contractId = contractFromSeed;
  let feeNature = feeFromSeed;
  if (!contractId) {
    const def = defaultContractSelection(ctx.feeNatureOptions);
    contractId = def.contractId;
    feeNature = def.feeNature;
  } else if (!feeNature) {
    const label = terminologyLabel(ctx.feeNatureOptions, contractId);
    feeNature = label === '—' ? contractId : label;
  }

  return {
    orderType: String(seed?.orderType ?? '').trim(),
    selectedId: seed?.selectedId || '',
    diagnosis: seed?.diagnosis || '',
    contractId: contractId || undefined,
    feeNature,
    skinTest: seed?.skinTest || false,
    pharmacy: String(seed?.pharmacy ?? '').trim(),
    singleDose: numFromSeed('singleDose'),
    frequency,
    usage,
    medicationDays: numFromSeed('medicationDays'),
    totalAmount: numFromSeed('totalAmount'),
    serviceQuantity: numFromSeed('serviceQuantity'),
    selectedDrugName: String(s?.selectedDrugName ?? '').trim(),
    selectedDrugSpec: String(s?.selectedDrugSpec ?? '').trim(),
    selectedDrugPrice: s?.selectedDrugPrice ?? '',
    doseMainUnit: String(s?.doseMainUnit ?? '').trim(),
    doseSubUnit: String(s?.doseSubUnit ?? '').trim(),
    doseConvertRatio: numFromSeed('doseConvertRatio'),
    doseBandConvertK: (() => {
      const k = Number(s?.doseBandConvertK);
      return Number.isFinite(k) && k > 0 ? k : undefined;
    })(),
    totalAmountSplitToSalesK: (() => {
      const k = Number(s?.totalAmountSplitToSalesK);
      return Number.isFinite(k) && k > 0 ? k : undefined;
    })(),
    totalAmountUnit: String(s?.totalAmountUnit ?? '').trim(),
    expanded: true,
    tcmSpecialDecoction: s?.tcmSpecialDecoction ?? '',
    productTableName: (s?.productTableName ?? '').toString().trim() || undefined,
    sortNumber: (() => {
      const sn = s?.sortNumber;
      if (sn === undefined || sn === null) return undefined;
      const snNum = Number(sn);
      return Number.isFinite(snNum) && snNum > 0 ? Math.trunc(snNum) : undefined;
    })(),
    groupId: (() => {
      const g = String(s?.groupId ?? '').trim();
      return g === '' ? undefined : g;
    })(),
    performOrgId: (() => {
      const x = String(s?.performOrgId ?? '').trim();
      return x === '' ? undefined : x;
    })(),
    performOrgName: String(s?.performOrgName ?? '').trim() || undefined,
  };
}

export function buildTcmProductSelectPatch(row: AdviceProductOption): Partial<AdviceDrugItem> {
  const specMatch = row.spec.match(/(\d+(\.\d+)?)g/i);
  const doseFromSpec = specMatch ? Number(specMatch[1]) : Number.NaN;
  return {
    singleDose: Number.isFinite(doseFromSpec) ? doseFromSpec : 0,
    doseSubUnit: specMatch ? 'g' : '',
    totalAmount: 0,
    tcmSpecialDecoction: '',
  };
}

export function buildWesternProductSelectPatch(
  prevRow: AdviceDrugItem,
  row: AdviceProductOption,
  opts?: WesternProductSelectPatchOptions,
): Partial<AdviceDrugItem> {
  const patch: Partial<AdviceDrugItem> = {};
  const mc = row.methodCode?.trim();
  const rc = row.rateCode?.trim();
  if (mc) patch.usage = mc;
  if (rc) patch.frequency = rc;
  const bands = row.unitBands;
  patch.unitBands = bands;
  const catalogRaw = row.catalogUnitPrice ?? row.price;
  if (catalogRaw !== undefined && catalogRaw !== null && String(catalogRaw).trim() !== '') {
    const cp = Number(catalogRaw);
    if (Number.isFinite(cp)) patch.catalogUnitPrice = cp;
  }
  const splitName = unitNameByBandKind(bands, 'SPLIT');
  const doseName = unitNameByBandKind(bands, 'DOSE');
  if (splitName) patch.doseMainUnit = splitName;
  if (doseName) patch.doseSubUnit = doseName;
  const ratioK = doseConvertRatioSplitToDose(bands);
  const curSdRaw = Number(prevRow.singleDose);
  const curDqRaw = Number(prevRow.doseConvertRatio);
  const curSd = Number.isFinite(curSdRaw) && curSdRaw > 0 ? curSdRaw : Number.NaN;
  const curDq = Number.isFinite(curDqRaw) && curDqRaw > 0 ? curDqRaw : Number.NaN;
  if (ratioK !== undefined && Number.isFinite(ratioK) && ratioK > 0) {
    patch.doseBandConvertK = ratioK;
    if (Number.isFinite(curSd) && curSd > 0) {
      patch.doseConvertRatio = roundMedicationQty(curSd * ratioK);
    } else if (Number.isFinite(curDq) && curDq > 0) {
      patch.singleDose = roundMedicationQty(curDq / ratioK);
      patch.doseConvertRatio = roundMedicationQty(curDq);
    }
  } else {
    patch.doseBandConvertK = undefined;
  }
  const splitCode = unitCodeByBandKind(bands, 'SPLIT');
  const salesCode = unitCodeByBandKind(bands, 'SALES');
  const salesName = unitNameByBandKind(bands, 'SALES');
  const packageCode = unitCodeByBandKind(bands, 'PACKAGE');
  const packageName = unitNameByBandKind(bands, 'PACKAGE');
  patch.totalUnitSplitCode = splitCode;
  patch.totalUnitSplitName = splitName;
  patch.totalUnitSalesCode = salesCode;
  patch.totalUnitSalesName = salesName;
  patch.totalUnitPackageCode = packageCode;
  patch.totalUnitPackageName = packageName;
  const totalSk = totalAmountSplitToSalesFactor(bands);
  patch.totalAmountSplitToSalesK =
    totalSk !== undefined && Number.isFinite(totalSk) && totalSk > 0 ? totalSk : undefined;
  const defaultKind = opts?.totalUnitDefaultKind ?? 'SALES';
  const totalUnit = defaultTotalAmountUnitCode(bands, defaultKind);
  patch.totalAmountUnit = totalUnit;
  const linePrice = resolveAdviceLineUnitPrice({
    bands,
    catalogUnitPrice: patch.catalogUnitPrice,
    totalAmountUnit: totalUnit,
  });
  if (linePrice !== undefined) {
    patch.selectedDrugPrice = linePrice;
  }
  return patch;
}

export function parseOptionalNumberInput(value: unknown): number | undefined {
  if (value === null || value === undefined || value === '') return undefined;
  const v = Number(value);
  return Number.isFinite(v) ? v : undefined;
}

export function inferDoseBandConvertK(row: AdviceDrugItem): AdviceDrugItem {
  if (
    row.doseBandConvertK !== undefined &&
    Number.isFinite(row.doseBandConvertK) &&
    row.doseBandConvertK > 0
  ) {
    return row;
  }
  const sd = Number(row.singleDose);
  const dq = Number(row.doseConvertRatio);
  if (Number.isFinite(sd) && sd > 0 && Number.isFinite(dq) && dq >= 0) {
    const k = dq / sd;
    if (Number.isFinite(k) && k > 0) {
      return { ...row, doseBandConvertK: k };
    }
  }
  return row;
}

export {
  mapEncounterRowToDiagnosisItem,
  pickMainDiagnosisDisplayName,
  resolveConditionDefinitionIdFromEncounterRows,
  resolveEncounterDiagnosisIdFromEncounterRows,
};

/** 药品行诊断展示名：保留已选，否则回落就诊主诊断。 */
export function resolveDefaultDrugRowDiagnosisName(
  current: string | undefined,
  encounterRows: EncounterDiagnosisRowDto[],
): string {
  const trimmed = String(current ?? '').trim();
  if (trimmed) return trimmed;
  return pickMainDiagnosisDisplayName(encounterRows);
}

/** 组包药品草稿：优先按行诊断名匹配，再回落就诊诊断快照 / 主诊断 / 唯一诊断。 */
export function resolveMedicationEncounterDiagnosisId(
  diagnosisName: string,
  encounterRows: EncounterDiagnosisRowDto[] = [],
): string | undefined {
  const name = String(diagnosisName ?? '').trim();
  if (name) {
    const fromEncounter = resolveEncounterDiagnosisIdFromEncounterRows(name, encounterRows);
    if (fromEncounter) return fromEncounter;
  }

  const mainName = pickMainDiagnosisDisplayName(encounterRows);
  if (mainName) {
    const mainFromEncounter = resolveEncounterDiagnosisIdFromEncounterRows(mainName, encounterRows);
    if (mainFromEncounter) return mainFromEncounter;
  }

  if (encounterRows.length === 1) {
    const only = String(encounterRows[0]?.encounterDiagnosisId ?? '').trim();
    if (only) return only;
  }
  return undefined;
}

/** 从编辑行解析合同主键（落库 contract_id）。 */
export function resolveMedicationContractId(
  item: Pick<AdviceDrugItem, 'contractId'>,
): string | undefined {
  const explicit = String(item.contractId ?? '').trim();
  return explicit || undefined;
}
