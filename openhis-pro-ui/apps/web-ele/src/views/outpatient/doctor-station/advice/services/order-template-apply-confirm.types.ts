import type {
  AdviceDraftBindDialogRow,
  AdviceDraftBindDialogSourceGroup,
} from './advice-draft-bind.types';

import type { DoctorStationAdviceDraftLineRequest } from '#/api/doctor-station/advice.types';

/** 组套套用确认弹窗：组套行类型 */
export type OrderTemplateApplyConfirmLineKind = 'DEVICE' | 'MEDICATION' | 'SERVICE';

/** 组套套用确认弹窗：组套行（可编辑数量 / 删除） */
export interface OrderTemplateApplyConfirmTemplateRow {
  key: string;
  /** 对应 preparedDraftLines 下标，用于删行后重映射带出行锚点 */
  lineIndex: number;
  lineKind: OrderTemplateApplyConfirmLineKind;
  itemName: string;
  kindLabel: string;
  quantity: number;
  unitName: string;
  dosageText?: string;
}

/** 组套套用确认弹窗打开参数 */
export interface OrderTemplateApplyConfirmDialogPayload {
  templateName: string;
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
  bindRows: AdviceDraftBindDialogRow[];
  bindSourceGroups: AdviceDraftBindDialogSourceGroup[];
}

/** 组套套用确认弹窗确认结果 */
export interface OrderTemplateApplyConfirmDialogResult {
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
  bindRows: AdviceDraftBindDialogRow[];
  skipSourceKeys: string[];
}

/** prepareOrderTemplateApplyConfirm 产出：供弹窗展示与确认后套用 */
export interface OrderTemplateApplyConfirmPreview {
  templateName: string;
  templateRows: OrderTemplateApplyConfirmTemplateRow[];
  bindRows: AdviceDraftBindDialogRow[];
  bindSourceGroups: AdviceDraftBindDialogSourceGroup[];
  draftLines: DoctorStationAdviceDraftLineRequest[];
}
