/**
 * Dialog 尺寸类型
 */
export type DialogSize = 'large' | 'medium' | 'small';

/**
 * Dialog 组件的 Props 类型
 */
export interface DialogProps {
  /** 是否将对话框追加到 body 上 */
  appendToBody?: boolean;
  /** 对话框打开前的回调 */
  beforeClose?: (done: () => void) => void;
  /** 是否可以通过点击遮罩层关闭对话框 */
  closeOnClickModal?: boolean;
  /** 是否可以通过按下 ESC 关闭对话框 */
  closeOnPressEscape?: boolean;
  /** 自定义类名 */
  customClass?: string;
  /** 是否在关闭时销毁子元素 */
  destroyOnClose?: boolean;
  /** 是否可拖拽 */
  draggable?: boolean;
  /** 是否全屏 */
  fullscreen?: boolean;
  /** 自定义高度 */
  height?: number | string;
  /** 是否显示加载状态 */
  loading?: boolean;
  /** 是否在对话框出现时将 body 滚动锁定 */
  lockScroll?: boolean;
  /** 是否显示对话框 */
  modelValue?: boolean;
  /** 是否显示关闭按钮 */
  showClose?: boolean;
  /** 对话框尺寸 */
  size?: DialogSize;
  /** 对话框标题 */
  title?: string;
  /** 自定义宽度 */
  width?: number | string;
}
