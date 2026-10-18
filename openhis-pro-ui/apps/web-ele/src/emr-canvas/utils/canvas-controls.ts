import {
  ControlType,
  ElementType,
  FlexDirection,
  type IElement,
} from '@hufe921/canvas-editor';

type ControlConfig = NonNullable<IElement['control']>;

function controlId(): string {
  return `ctl_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
}

/** 文本/数字/日期：图一淡蓝底 + 方括号占位。打印时由高亮补丁去掉此色，用户高亮保留 */
export const FILL_CONTROL_CHROME = {
  prefix: '[',
  postfix: ']',
  highlight: '#eff6ff',
} as const;

/**
 * 单选/复选/下拉必须有前缀和后缀元素，画布才知道控件从哪到哪。
 * 空字符串不会生成边界：删除时左边界落到文首，光标也出不了控件。
 * 用词连接符，宽度为 0，不是换行符，纸面上不出现括号或空格。
 */
export const CHOICE_CONTROL_BOUNDARY = '\u2060';

function isFillControlType(type: ControlType | string | undefined) {
  return type === ControlType.TEXT || type === ControlType.NUMBER || type === ControlType.DATE;
}

function isChoiceControlType(type: ControlType | string | undefined) {
  return (
    type === ControlType.CHECKBOX || type === ControlType.RADIO || type === ControlType.SELECT
  );
}

/** 只给填写类控件套蓝底括号；单选/多选/下拉保持纯选项，无括号无蓝底 */
export function applyControlChrome(control: ControlConfig) {
  if (isFillControlType(control.type)) {
    control.prefix = FILL_CONTROL_CHROME.prefix;
    control.postfix = FILL_CONTROL_CHROME.postfix;
    control.highlight = FILL_CONTROL_CHROME.highlight;
    return;
  }
  if (isChoiceControlType(control.type)) {
    control.prefix = CHOICE_CONTROL_BOUNDARY;
    control.postfix = CHOICE_CONTROL_BOUNDARY;
    delete control.highlight;
  }
}

export function applyControlChromeInElements(elements: IElement[] | undefined) {
  if (!elements?.length) return;
  for (const el of elements) {
    if (el.control) applyControlChrome(el.control);
    const rows = el.trList;
    if (!rows?.length) continue;
    for (const row of rows) {
      for (const cell of row.tdList ?? []) {
        applyControlChromeInElements(cell.value);
      }
    }
  }
}

export type ControlOption = { code: string; label: string };

function toValueSets(options?: string[] | ControlOption[]) {
  const source = options?.length ? options : ['选项1', '选项2'];
  return source
    .map((item, index) => {
      if (typeof item === 'string') {
        const value = item.trim();
        return value ? { value, code: String(index + 1) } : null;
      }
      const value = item.label.trim();
      const code = item.code.trim() || String(index + 1);
      return value ? { value, code } : null;
    })
    .filter((item): item is { value: string; code: string } => Boolean(item));
}

/** 选项末尾留一个空格，两个方框之间才有地方落光标、回车和继续输入 */
function withTrailingSpace(sets: { value: string; code: string }[]) {
  return sets.map((item) => {
    if (item.value.endsWith(' ')) return item;
    return { ...item, value: `${item.value} ` };
  });
}

/**
 * 单选在画布里只能画圆；需要方块时转成 checkbox 并限制最多选 1 项。
 */
function resolveType(type: ControlType): ControlType {
  if (type === ControlType.RADIO) return ControlType.CHECKBOX;
  return type;
}

export function buildControlElement(input: {
  type: ControlType;
  placeholder?: string;
  options?: string[] | ControlOption[];
}): IElement {
  const type = resolveType(input.type);
  const isChoice = type === ControlType.CHECKBOX || type === ControlType.SELECT;
  const valueSets = toValueSets(input.options);
  const placeholder =
    input.placeholder?.trim() ||
    (type === ControlType.DATE ? '请选择日期' : isChoice ? '请选择' : '请在这里填写');
  const base: ControlConfig = {
    type,
    value: null,
    placeholder,
    conceptId: controlId(),
    underline: false,
    border: false,
  };
  applyControlChrome(base);

  if (type === ControlType.SELECT) {
    base.code = null;
    base.valueSets = valueSets;
  }
  if (type === ControlType.CHECKBOX) {
    base.code = null;
    base.flexDirection = FlexDirection.ROW;
    const options = valueSets.length ? valueSets : toValueSets(['选项']);
    base.valueSets = withTrailingSpace(options);
    if (input.type === ControlType.RADIO) {
      base.max = 1;
    }
  }
  if (type === ControlType.DATE) {
    base.dateFormat = 'yyyy-MM-dd';
  }

  return {
    type: ElementType.CONTROL,
    value: '',
    control: base,
  };
}
