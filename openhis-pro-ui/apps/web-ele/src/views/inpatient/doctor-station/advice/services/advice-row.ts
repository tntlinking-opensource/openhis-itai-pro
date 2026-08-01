import type { DoctorStationAdviceResourceKeyDto } from '#/api/doctor-station/advice.types';

import type { AdviceDrugItem, AdviceRowItem, InpatientAdviceTherapyType } from './advice.types';
import {
  ADVICE_ROW_PRODUCT_TABLE_FALLBACK,
  INPATIENT_ENTRUST_ACTIVITY_NAME,
  INPATIENT_ENTRUST_CATEGORY_CODE,
} from './advice-tab.constants';

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

/** 住院长期医嘱停嘱：已签发且 therapy 为 LONG_TERM，且未停嘱 */
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
  if (statusCode === 'STOPPED') return false;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  if (/\bSTOPPED\b|已停嘱/.test(blob)) return false;
  return true;
}

/** 住院长期医嘱取消停嘱：已停嘱且 therapy 为 LONG_TERM */
export function rowLooksCancelStopLongTermEligible(row: AdviceRowItem): boolean {
  if (row.isEditor) return false;
  if (!resourceKeyFromRow(row)) return false;
  const therapy = String(row.therapyTypeCode ?? '')
    .trim()
    .toUpperCase();
  if (therapy && therapy !== 'LONG_TERM') return false;
  const statusCode = String(row.statusEnum ?? '')
    .trim()
    .toUpperCase();
  if (statusCode === 'STOPPED') return true;
  const blob = `${row.status ?? ''} ${row.statusEnum ?? ''}`.toUpperCase();
  return /\bSTOPPED\b|已停嘱/.test(blob);
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
