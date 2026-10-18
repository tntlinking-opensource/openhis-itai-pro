/** 插入控件时的常用选项预设（与 Tiptap EMR 弹窗同口径） */
export const WIDGET_OPTION_PRESETS = [
  {
    key: 'yesNo',
    name: '是否',
    items: [
      { code: 'Y', label: '是' },
      { code: 'N', label: '否' },
    ],
  },
  {
    key: 'haveNone',
    name: '有无',
    items: [
      { code: 'HAVE', label: '有' },
      { code: 'NONE', label: '无' },
    ],
  },
  {
    key: 'gender',
    name: '男女',
    items: [
      { code: 'M', label: '男' },
      { code: 'F', label: '女' },
    ],
  },
  {
    key: 'posNeg',
    name: '阴阳',
    items: [
      { code: 'POS', label: '阳性' },
      { code: 'NEG', label: '阴性' },
    ],
  },
] as const;
