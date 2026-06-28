/** hiprint 设计面板（库无完整 TS 定义） */
export interface HiprintEditingPanel {
  scale?: number;
  getSelectEls?: () => unknown[];
  setElsAlign?: (type: string) => void;
  undo?: () => void;
  redo?: () => void;
}

/** hiprint PrintTemplate 实例（库无完整 TS 定义） */
export interface HiprintTemplateInstance {
  design: (selector: string) => void;
  getJson: () => Record<string, unknown>;
  update: (json: Record<string, unknown>) => void;
  setPaper: (width: number, height: number) => void;
  zoom: (scale: number, persist?: boolean) => void;
  clear: () => void;
  print: (
    data: Record<string, unknown>,
    options?: Record<string, unknown>,
    ext?: Record<string, unknown>,
  ) => void;
  getHtml: (data: Record<string, unknown>) => string;
  /** 汇总各元素 options.testData（设计器属性面板测试值） */
  getTestData?: () => Record<string, unknown>;
  editingPanel?: HiprintEditingPanel;
  history?: boolean;
  historyList?: unknown[];
  redo?: () => void;
  undo?: () => void;
}

export interface FunctionEditorContext {
  optionName: string;
  label: string;
  value: string;
  apply: (code: string) => void;
}

export interface PaperPreset {
  label: string;
  width: number;
  height: number;
}

export interface HiprintDesignerExpose {
  getTemplateJson: () => Record<string, unknown>;
  getSamplePrintData: () => Record<string, unknown>;
}
