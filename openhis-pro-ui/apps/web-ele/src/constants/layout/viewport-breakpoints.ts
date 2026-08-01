/** 查询区控件使用固定宽度的视口宽度下限（px，含该值）；低于此值使用栅格铺满。 */
export const FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX = 1680;

/** CSS `@media (max-width: …)` 用上限（px），与 {@link FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX} 对齐。 */
export const COMPACT_VIEWPORT_MAX_CSS_PX = FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX - 1;

/** 窄屏根 class，由 {@link useLayoutCompactViewport} 挂载到 `html`。 */
export const COMPACT_VIEWPORT_ROOT_CLASS = 'layout-viewport-compact';

/** 报表/列表查询区普通控件固定宽度（px）。 */
export const FIXED_QUERY_CONTROL_WIDTH_PX = 200;

/** 报表/列表查询区日期区间控件固定宽度（px）。 */
export const FIXED_QUERY_DATERANGE_CONTROL_WIDTH_PX = 360;

/** 全局顶栏 + 路由页签高度（px）：≥ {@link FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX} 用 default，否则 compact。 */
export const LAYOUT_SHELL_HEIGHT_PX = {
  header: { default: 50, compact: 44 },
  tabbar: { default: 38, compact: 32 },
} as const;

/** 全局表单/按钮 compact 档（px）：≥ {@link FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX} 用 default，否则 compact。 */
export const APP_CONTROL_SIZE_PX = {
  formHeight: { default: 30, compact: 28 },
  formRadius: { default: 6, compact: 5 },
  btnHeight: { default: 32, compact: 28 },
  fontSize: { default: 14, compact: 13 },
  btnPaddingX: { default: 16, compact: 12 },
  btnPaddingY: { default: 8, compact: 6 },
  formItemGap: { default: 18, compact: 14 },
} as const;

/** 弹层 / 下拉 compact 档（px）：Dialog、Drawer、MessageBox、Select 等，见 layout-viewport-compact.css §P1。 */
export const OVERLAY_SIZE_PX = {
  dialogTitleFont: { default: 18, compact: 16 },
  dialogHeaderPad: { default: 16, compact: 12 },
  dialogBodyPad: { default: 20, compact: 14 },
  drawerTitleFont: { default: 16, compact: 15 },
  drawerPad: { default: 20, compact: 14 },
  dropdownItemHeight: { default: 34, compact: 30 },
  popoverPad: { default: 12, compact: 8 },
} as const;

/** 表单补全 compact 档（px）：Textarea、Checkbox、Tree 等，见 layout-viewport-compact.css §P1。 */
export const FORM_EXTRA_SIZE_PX = {
  textareaFont: { default: 14, compact: 13 },
  checkboxLabelFont: { default: 14, compact: 13 },
  treeNodeHeight: { default: 32, compact: 28 },
} as const;

/** 表格 compact 档（px）：ElTable / VxeGrid，见 layout-viewport-compact.css §P0-Table。 */
export const TABLE_SIZE_PX = {
  headerHeight: { default: 40, compact: 32 },
  rowMinHeight: { default: 40, compact: 34 },
  cellPaddingX: { default: 12, compact: 10 },
  cellPaddingY: { default: 8, compact: 6 },
  fontSize: { default: 14, compact: 13 },
} as const;

/** 页签 compact 档（px）：ElTabs、区块工具条、医生站业务 Tab。 */
export const TAB_SIZE_PX = {
  elTabsItemHeight: { default: 40, compact: 32 },
  elTabsItemPaddingX: { default: 20, compact: 12 },
  sectionToolbarHeight: { default: 42, compact: 36 },
  doctorBusinessTabHeight: { default: 28, compact: 26 },
} as const;

/** 分页 compact 档（px）。 */
export const PAGINATION_SIZE_PX = {
  btnHeight: { default: 32, compact: 28 },
  fontSize: { default: 14, compact: 13 },
} as const;

/** 医生站侧栏宽度（px）：≥ {@link FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX} 用 default，否则 compact。 */
export const DOCTOR_STATION_SIDEBAR_WIDTH_PX = {
  patientQueue: { default: 260, compact: 228 },
  orderTemplate: { default: 240, compact: 208 },
  emrHistoryTemplate: { default: 280, compact: 248 },
  diagnosisQuickTree: { default: 260, compact: 228 },
  tcmTemplate: { default: 240, compact: 208 },
} as const;

/**
 * 库房单据工作站左侧队列宽度（px）。
 * WarehouseDocWorkstation 未传 asideWidth 时按视口取用。
 */
export const INVENTORY_DOC_ASIDE_WIDTH_PX = {
  default: 280,
  compact: 248,
} as const;

export type ViewportSizePreset<T> = { default: T; compact: T };

/** 视口是否处于 compact 档（宽度 < 1680）。 */
export function isCompactViewportWidth(width: number): boolean {
  return width < FIXED_QUERY_CONTROL_VIEWPORT_MIN_PX;
}

/** 按视口宽度在 default / compact 间取值。 */
export function pickViewportPreset<T>(width: number, preset: ViewportSizePreset<T>): T {
  return isCompactViewportWidth(width) ? preset.compact : preset.default;
}
