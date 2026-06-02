import type { PatientItem } from './types';

import {
  dispatchNurseStationBedDragStart,
  setNurseStationBedDragPayload,
} from './services/nurse-station-bed-drag-payload';

/** 维护护士站患者卡片拖拽（左侧列表与床位图占床卡共用载荷格式） */
export function useNurseStationBedDrag() {
  let patientDragPreviewEl: HTMLDivElement | null = null;

  /** 移除自定义拖拽预览节点 */
  function removePatientDragPreview() {
    patientDragPreviewEl?.remove();
    patientDragPreviewEl = null;
  }

  /** 构建带文字说明的拖拽幽灵图 */
  function buildPatientDragPreviewEl(row: Pick<PatientItem, 'bedNo' | 'name'>): HTMLDivElement {
    const wrap = document.createElement('div');
    wrap.style.cssText = [
      'position:fixed',
      'left:-9999px',
      'top:0',
      'z-index:99999',
      'padding:12px 16px',
      'min-width:220px',
      'max-width:300px',
      'background:#fff',
      'border:2px solid #409eff',
      'border-radius:10px',
      'box-shadow:0 8px 24px rgba(15,23,42,.18)',
      'font-family:system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif',
      'pointer-events:none',
    ].join(';');
    const title = document.createElement('div');
    title.style.cssText =
      'font-size:15px;font-weight:700;color:#1e293b;margin-bottom:6px;line-height:1.3';
    title.textContent = row.name;
    const from = document.createElement('div');
    from.style.cssText = 'font-size:13px;color:#64748b;margin-bottom:8px;line-height:1.35';
    from.textContent = row.bedNo ? `当前床位：${row.bedNo}` : '当前：待分床（无床位）';
    const hint = document.createElement('div');
    hint.style.cssText = 'font-size:12px;color:#409eff;line-height:1.45';
    hint.textContent = '→ 拖至目标床位；待分床需填体征，换床/互换确认即生效';
    wrap.append(title, from, hint);
    return wrap;
  }

  /** 开始拖拽：写入载荷、预览图并广播给床位图 */
  function onPatientDragStart(e: DragEvent, row: PatientItem) {
    const dt = e.dataTransfer;
    if (!dt) {
      return;
    }
    removePatientDragPreview();
    setNurseStationBedDragPayload(dt, {
      patientId: row.id,
      fromBedNo: row.bedNo || '',
    });
    dt.setData('text/plain', `${row.name} · 分床拖拽`);
    dt.effectAllowed = 'move';
    const preview = buildPatientDragPreviewEl(row);
    document.body.append(preview);
    patientDragPreviewEl = preview;
    void preview.offsetWidth;
    dt.setDragImage(preview, 28, 32);
    dispatchNurseStationBedDragStart({
      fromBedLabel: row.bedNo || '待分床',
      hasBed: !!row.bedLocationId,
      patientName: row.name,
    });
  }

  /** 拖拽结束：移除预览 */
  function onPatientDragEnd() {
    removePatientDragPreview();
  }

  const patientListPanelDragContext = {
    onPatientDragEnd,
    onPatientDragStart,
  };

  return {
    onPatientDragEnd,
    onPatientDragStart,
    patientListPanelDragContext,
  };
}
