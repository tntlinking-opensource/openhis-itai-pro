const DEFAULT_BLANK_TEMPLATE: Record<string, unknown> = {
  schemaVersion: 1,
  engine: 'Print',
  panels: [
    {
      index: 0,
      paperType: 'A4',
      width: 210,
      height: 296.6,
      /** 0 = 页眉线贴纸上边（hiprint 默认会 hide，由 design-canvas 纠正展示） */
      paperHeader: 0,
      /** 省略 paperFooter，由 hiprint 按纸张高度默认贴底 */
      printElements: [],
    },
  ],
};

/** 空白面板补全页眉/页脚默认值（仅无元素时） */
function applyBlankPanelMarginDefaults(panel: Record<string, unknown>): void {
  if (!isBlankPanel(panel)) {
    return;
  }
  panel.paperHeader = 0;
  if (typeof panel.paperFooter !== 'number') {
    delete panel.paperFooter;
  }
}

function isBlankPanel(panel: Record<string, unknown>): boolean {
  const elements = panel.printElements;
  return !Array.isArray(elements) || elements.length === 0;
}

/** 是否空白模板（无元素，应使用 A4 默认纸张） */
export function isBlankPrintTemplate(raw?: null | Record<string, unknown>): boolean {
  const normalized = normalizePrintJson(raw);
  const panel = (normalized.panels as Record<string, unknown>[] | undefined)?.[0];
  if (!panel) {
    return true;
  }
  const elements = panel.printElements;
  return !Array.isArray(elements) || elements.length === 0;
}

/** 规范化加载/保存用的 PrintJson 根对象 */
export function normalizePrintJson(raw?: null | Record<string, unknown>): Record<string, unknown> {
  if (!raw || typeof raw !== 'object') {
    return structuredClone(DEFAULT_BLANK_TEMPLATE);
  }
  const panels = raw.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    return {
      ...raw,
      schemaVersion: typeof raw.schemaVersion === 'number' ? raw.schemaVersion : 1,
      engine: 'Print',
      panels: DEFAULT_BLANK_TEMPLATE.panels,
    };
  }
  const normalizedPanels = panels.map((panel) => {
    if (!panel || typeof panel !== 'object') {
      return panel;
    }
    const copy = { ...(panel as Record<string, unknown>) };
    applyBlankPanelMarginDefaults(copy);
    return copy;
  });
  return {
    ...raw,
    schemaVersion: typeof raw.schemaVersion === 'number' ? raw.schemaVersion : 1,
    engine: 'Print',
    panels: normalizedPanels,
  };
}

/** 保存前补全 engine / schemaVersion */
export function toSavePayload(raw: Record<string, unknown>): Record<string, unknown> {
  return normalizePrintJson(raw);
}

/** 前端校验 PrintJson 结构（与后端 PrintJsonValidator 对齐） */
export function validatePrintJson(raw: Record<string, unknown>): void {
  if (!raw || typeof raw !== 'object') {
    throw new Error('PrintJson 须为对象');
  }
  const panels = raw.panels;
  if (!Array.isArray(panels) || panels.length === 0) {
    throw new Error('panels 须为非空数组');
  }
  for (const panel of panels) {
    validatePanel(panel);
  }
}

function validatePanel(panel: unknown): void {
  if (!panel || typeof panel !== 'object') {
    throw new Error('panel 须为对象');
  }
  const p = panel as Record<string, unknown>;
  if (typeof p.width !== 'number' || typeof p.height !== 'number') {
    throw new TypeError('panel 宽高须为数字');
  }
  const elements = p.printElements;
  if (!Array.isArray(elements)) {
    throw new TypeError('printElements 须为数组');
  }
  for (const el of elements) {
    validatePrintElement(el);
  }
}

function validatePrintElement(element: unknown): void {
  if (!element || typeof element !== 'object') {
    throw new Error('元素须为对象');
  }
  const el = element as Record<string, unknown>;
  const pet = el.printElementType;
  if (!pet || typeof pet !== 'object') {
    throw new Error('缺少 printElementType');
  }
  const type = (pet as Record<string, unknown>).type;
  if (typeof type !== 'string' || !type.trim()) {
    throw new Error('元素 type 不能为空');
  }
}

export { DEFAULT_BLANK_TEMPLATE };

/** 设计器首次打开使用的 A4 空白模板 */
export function createInitialDesignerTemplate(): Record<string, unknown> {
  return structuredClone(DEFAULT_BLANK_TEMPLATE);
}
