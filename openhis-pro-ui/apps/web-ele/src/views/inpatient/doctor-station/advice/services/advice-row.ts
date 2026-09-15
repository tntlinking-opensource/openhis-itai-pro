import type { DoctorStationAdviceResourceKeyDto } from '#/api/doctor-station/advice.types';

import type { AdviceDrugItem, AdviceRowItem, InpatientAdviceTherapyType } from './advice.types';
import {
  ADVICE_ROW_PRODUCT_TABLE_FALLBACK,
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATEGORY_CODE,
} from './advice-tab.constants';

/** 列表右上角筛选码 → 开立默认治疗类型；「全部」或未知回落长期。 */
export function resolveDefaultInpatientAdviceTherapyType(
  listFilter: '' | InpatientAdviceTherapyType | string | null | undefined,
): InpatientAdviceTherapyType {
  const code = String(listFilter ?? '')
    .trim()
    .toUpperCase();
  if (code === 'TEMPORARY') return 'TEMPORARY';
  if (code === 'DISCHARGE_MEDICATION') return 'DISCHARGE_MEDICATION';
  return 'LONG_TERM';
}

/** 嘱托不可出院带药：筛选为出院带药时回落长期。 */
export function resolveDefaultInpatientEntrustTherapyType(
  listFilter: '' | InpatientAdviceTherapyType | string | null | undefined,
): InpatientAdviceTherapyType {
  const code = resolveDefaultInpatientAdviceTherapyType(listFilter);
  if (isInpatientDischargeMedicationTherapy(code)) return 'LONG_TERM';
  return code;
}

/** 住院西药长期医嘱：页面上不录总量，保存时 quantity/unitCode 取单次用量左侧（SPLIT）。 */
export function isInpatientLongTermMedicationTherapy(
  therapy?: InpatientAdviceTherapyType | string | null,
): boolean {
  return (
    String(therapy ?? '')
      .trim()
      .toUpperCase() === 'LONG_TERM'
  );
}

/** 出院带药 therapy（仅药品；禁止诊疗/嘱托，BR-IP-ORD-029）。 */
export function isInpatientDischargeMedicationTherapy(
  therapy?: InpatientAdviceTherapyType | string | null,
): boolean {
  return (
    String(therapy ?? '')
      .trim()
      .toUpperCase() === 'DISCHARGE_MEDICATION'
  );
}

/** 编辑卡行是否嘱托（类目 52 或项目名「嘱托」）。 */
export function isEntrustAdviceDrugItem(
  item: Pick<AdviceDrugItem, 'catalogCategoryCode' | 'selectedDrugName'>,
): boolean {
  if (String(item.catalogCategoryCode ?? '').trim() === INPATIENT_ENTRUST_CATEGORY_CODE) {
    return true;
  }
  return String(item.selectedDrugName ?? '').trim() === INPATIENT_ENTRUST_ACTIVITY_NAME;
}

/** 复合 selectedId：`{productTableName}:{definitionId}` */

export function parseProductTableNameFromSelectedId(selectedId: string): string | undefined {
  const s = String(selectedId || '').trim();
  const idx = s.indexOf(':');
  if (idx <= 0) return undefined;
  const tbl = s.slice(0, idx).trim();
  return tbl || undefined;
}

export function parseColonDefinitionId(selectedId: string): string | undefined {
  const s = String(selectedId || '').trim();
  const idx = s.indexOf(':');
  if (idx < 0 || idx >= s.length - 1) return undefined;
  const id = s.slice(idx + 1).trim();
  return id || undefined;
}

export type AdviceLineEditorKind = 'DEVICE' | 'MEDICATION' | 'SERVICE';

export function inferAdviceLineEditorKind(
  item: Pick<AdviceDrugItem, 'productTableName' | 'selectedId'>,
): AdviceLineEditorKind {
  const direct = String(item.productTableName ?? '')
    .trim()
    .toLowerCase();
  if (direct === 'adm_device_definition') return 'DEVICE';
  if (direct === 'wkf_activity_definition') return 'SERVICE';
  if (direct === 'med_medication_definition' || direct.includes('medication_definition')) {
    return 'MEDICATION';
  }
  const fromId = String(parseProductTableNameFromSelectedId(item.selectedId) ?? '')
    .trim()
    .toLowerCase();
  if (fromId === 'adm_device_definition') return 'DEVICE';
  if (fromId === 'wkf_activity_definition') return 'SERVICE';
  return 'MEDICATION';
}

function validateWesternMedicationLineItem(
  item: AdviceDrugItem,
  therapy?: InpatientAdviceTherapyType | string | null,
): boolean {
  const therapyCode =
    therapy !== undefined && therapy !== null && String(therapy).trim() !== ''
      ? therapy
      : item.therapyEnum;
  const longTerm = isInpatientLongTermMedicationTherapy(therapyCode);
  return Boolean(
    item.selectedId &&
      item.contractId &&
      item.usage &&
      item.frequency &&
      Number(item.singleDose) > 0 &&
      (longTerm ||
        (Number(item.totalAmount) > 0 && String(item.totalAmountUnit ?? '').trim() !== '')),
  );
}

export function validateWesternEditLineItem(
  item: AdviceDrugItem,
  therapy?: InpatientAdviceTherapyType | string | null,
): boolean {
  if (!String(item.selectedId ?? '').trim()) return false;
  const kind = inferAdviceLineEditorKind(item);
  if (kind === 'DEVICE') {
    return Number(item.totalAmount) > 0 && String(item.totalAmountUnit ?? '').trim() !== '';
  }
  if (kind === 'SERVICE') {
    const therapyCode =
      therapy !== undefined && therapy !== null && String(therapy).trim() !== ''
        ? therapy
        : item.therapyEnum;
    // 出院带药不得开诊疗/嘱托（后端也会拒，前端先挡）
    if (isInpatientDischargeMedicationTherapy(therapyCode)) {
      return false;
    }
    // 嘱托行：界面不录数量/执行科室，仅校验频次与正文
    if (isEntrustAdviceDrugItem(item)) {
      return (
        String(item.frequency ?? '').trim() !== '' &&
        String(item.entrustContent ?? '').trim() !== ''
      );
    }
    // 临时诊疗：后端清空 rate_code，频次非必填；长期须填频次
    const longTerm = isInpatientLongTermMedicationTherapy(therapyCode);
    return (
      Number(item.serviceQuantity) > 0 &&
      String(item.performOrgId ?? '').trim() !== '' &&
      (!longTerm || String(item.frequency ?? '').trim() !== '') &&
      (longTerm || Number(item.totalAmount) > 0)
    );
  }
  return validateWesternMedicationLineItem(item, therapy);
}

export function productTableNameForAdviceRow(row: AdviceRowItem): string | undefined {
  const fromList = String(row.productTableName ?? '').trim();
  if (fromList) return fromList;
  const rt = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  const fb = ADVICE_ROW_PRODUCT_TABLE_FALLBACK[rt];
  return fb?.trim() || undefined;
}

export function resourceKeyFromRow(row: AdviceRowItem): DoctorStationAdviceResourceKeyDto | null {
  const requestId = String(row.requestId ?? '').trim();
  const productTableName = productTableNameForAdviceRow(row);
  if (!requestId || !productTableName) return null;
  return { requestId, productTableName };
}

export function rowLooksDraftForSign(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  const code = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (code === 'DRAFT') return true;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  return /\bDRAFT\b/.test(blob);
}

export function rowLooksWithdrawable(
  row: AdviceRowItem,
  isDraftRow: (r: AdviceRowItem) => boolean,
) {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  if (isDraftRow(row)) return false;
  const text = `${row.status} ${row.statusEnum || ''}`;
  if (/已收费|SETTLED|已结算|计费完成/i.test(text)) return false;
  return true;
}

export function rowLooksDeletable(row: AdviceRowItem, isDraftRow: (r: AdviceRowItem) => boolean) {
  if (row.isEditor) return false;
  if (!isDraftRow(row)) return false;
  return Boolean(resourceKeyFromRow(row));
}

/** 暂存批次行长临字段（CREATE/UPDATE/DELETE 与后端 Mapper 归一一致） */
export function therapyDraftFieldsFromAdviceRow(
  row: Pick<AdviceRowItem, 'therapyTypeCode'>,
): { therapyTypeCode: string; therapyEnum: string } | undefined {
  const therapyEnum = String(row.therapyTypeCode ?? '').trim();
  if (!therapyEnum) return undefined;
  return { therapyTypeCode: therapyEnum, therapyEnum };
}

export function rowLooksDraft(row: AdviceRowItem): boolean {
  const text = `${row.status} ${row.statusEnum || ''}`;
  return /待签发|草稿|DRAFT|暂存|新开/i.test(text);
}

/** 住院长期医嘱停嘱：须已开嘱校对（VERIFIED），therapy 为 LONG_TERM，且未停嘱 */
export function rowLooksStopLongTermEligible(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  if (rowLooksDraft(row)) return false;
  const therapy = String(row.therapyTypeCode ?? '')
    .trim()
    .toUpperCase();
  if (therapy && therapy !== 'LONG_TERM') return false;
  const statusCode = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  // 未开嘱校对禁止停嘱（业务上应撤回）；仅 VERIFIED 可停
  if (statusCode !== 'VERIFIED') return false;
  return true;
}

/** 是否已停嘱（含展示文案） */
export function rowLooksStopped(row: AdviceRowItem): boolean {
  const statusCode = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (statusCode === 'STOPPED') return true;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  return /\bSTOPPED\b|已停嘱/.test(blob);
}

/** 是否已完成停嘱校对（有停嘱校对人则医生不可直接取消停嘱） */
export function rowLooksStopChecked(row: AdviceRowItem): boolean {
  return Boolean(String(row.stopPerformerCheckId ?? '').trim());
}

/** 住院长期医嘱取消停嘱：已停嘱、长期，且护士尚未完成停嘱校对 */
export function rowLooksCancelStopLongTermEligible(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  const therapy = String(row.therapyTypeCode ?? '')
    .trim()
    .toUpperCase();
  if (therapy && therapy !== 'LONG_TERM') return false;
  // 已停嘱校对时须护士先取消停嘱校对
  if (rowLooksStopChecked(row)) return false;
  return rowLooksStopped(row);
}

/** 住院长期皮试药追加皮试检查：已勾皮试、长期、未停嘱（IF-IP-DOR-27） */
export function rowLooksAppendSkinTestEligible(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  if (row.skinTestFlag !== true) return false;
  const therapy = String(row.therapyTypeCode ?? '')
    .trim()
    .toUpperCase();
  if (therapy && therapy !== 'LONG_TERM') return false;
  const statusCode = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (statusCode === 'STOPPED') return false;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  if (/\bSTOPPED\b|已停嘱/.test(blob)) return false;
  return true;
}

/**
 * 住院长期药品改量：已校对或已停嘱的药品行（IF-IP-DOR-28）。
 * 诊疗/草稿/临嘱/出院带药不可改量。
 */
export function rowLooksChangeDoseEligible(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  if (rowLooksDraft(row)) return false;
  const resource = String(row.resourceType ?? '')
    .trim()
    .toUpperCase();
  if (resource && resource !== 'MEDICATION' && resource !== 'MED') {
    // 仅药品
    const pt = String(row.productTableName ?? '')
      .trim()
      .toLowerCase();
    if (!pt.includes('medication')) return false;
  }
  const therapy = String(row.therapyTypeCode ?? '')
    .trim()
    .toUpperCase();
  if (therapy && therapy !== 'LONG_TERM') return false;
  const statusCode = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (statusCode === 'VERIFIED' || statusCode === 'STOPPED') return true;
  if (rowLooksStopped(row)) return true;
  return false;
}
