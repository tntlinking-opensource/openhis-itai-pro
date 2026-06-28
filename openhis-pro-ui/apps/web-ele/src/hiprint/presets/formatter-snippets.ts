export interface FormatterSnippet {
  name: string;
  /** 适用 option 名，空表示通用 */
  options?: string[];
  code: string;
}

export const FORMATTER_SNIPPETS: FormatterSnippet[] = [
  {
    name: '表格：用法用量行缩进（4 全角字符）',
    options: ['formatter2'],
    code: String.raw`function(value, row, index, options) {
  if (value == null || value === '') return '';
  var text = String(value).replace(/^\s+/, '');
  if (row.rowType === 'usage' || text.indexOf('用法用量') === 0) {
    return '\u3000\u3000\u3000\u3000' + text;
  }
  return String(value);
}`,
  },
  {
    name: '处方 Rp 区斜线（SVG）',
    options: ['formatter'],
    code: `function (data, options, templateData) {
  var stroke = options.borderWidth != null && options.borderWidth !== '' ? options.borderWidth : 1.5;
  var runPct = options.slashRunRatio != null && options.slashRunRatio !== '' ? options.slashRunRatio : 100;
  return '<svg xmlns="http://www.w3.org/2000/svg" width="100%" height="100%" preserveAspectRatio="none">'
    + '<line x1="0" y1="100%" x2="' + runPct + '%" y2="0" stroke="#000" stroke-width="' + stroke + '" vector-effect="non-scaling-stroke"/>'
    + '</svg>';
}`,
  },
  {
    name: '原值转字符串',
    code: `function(title, value, options, templateData) {
  return value == null ? '' : String(value);
}`,
  },
  {
    name: '数组 longText 循环（纯文本）',
    options: ['formatter'],
    code: String.raw`function(title, value, options, templateData) {
  if (!Array.isArray(value)) return '';
  return value.map(function(row) {
    return Object.values(row).filter(Boolean).join('  ');
  }).join('\n');
}`,
  },
  {
    name: '金额千分位两位小数',
    code: String.raw`function(title, value) {
  var n = Number(value);
  if (isNaN(n)) return '';
  return n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}`,
  },
  {
    name: '样式：加粗红色',
    options: ['styler'],
    code: `function(value, options, target, templateData) {
  return { fontWeight: 'bold', color: '#c0392b' };
}`,
  },
];
