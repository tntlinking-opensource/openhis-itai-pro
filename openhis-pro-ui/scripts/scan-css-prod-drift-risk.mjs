import fs from 'node:fs';
import path from 'node:path';

function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'DEPRECATED.md') continue;
    // 废弃 warehouse views 不纳入改造清单
    if (entry.name === 'warehouse' && dir.replace(/\\/g, '/').includes('/views/')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.name.endsWith('.vue')) acc.push(full);
  }
  return acc;
}

const root = path.resolve('apps/web-ele/src');
const files = walk(root);
const toRel = (f) => path.relative(root, f).replace(/\\/g, '/');

const searchRow = [];
const flexInputBtn = [];
const scopedFlexLayout = [];
const deepElInputWidth = [];

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  const rel = toRel(file);

  if (source.includes('search-row')) searchRow.push(rel);

  const hasInputThenButton = /ElInput[\s\S]{0,600}ElButton/.test(source);
  const hasFlexContext =
    /\bflex\b/.test(source) ||
    /search-row|toolbar-row|form-row|filter-bar|action-bar|query-bar|tool-bar/.test(source);
  if (hasInputThenButton && hasFlexContext) flexInputBtn.push(rel);

  const hasScoped = /<style\s+scoped/.test(source);
  if (
    hasScoped &&
    /display:\s*flex/.test(source) &&
    /search-row|toolbar-row|form-row|filter|header-row|action-row|query-row/.test(source)
  ) {
    scopedFlexLayout.push(rel);
  }

  if (/:deep\(\s*\.el-input|\.el-input\s*\{[^}]*width:\s*100%/.test(source)) {
    deepElInputWidth.push(rel);
  }
}

console.log('## search-row');
console.log(searchRow.join('\n') || '(none)');
console.log('\n## flex+ElInput+ElButton');
console.log(flexInputBtn.join('\n') || '(none)');
console.log('\n## scoped flex layout classes');
console.log(scopedFlexLayout.join('\n') || '(none)');
console.log('\n## counts');
console.log(
  JSON.stringify(
    {
      vueFiles: files.length,
      searchRow: searchRow.length,
      flexInputBtn: flexInputBtn.length,
      scopedFlexLayout: scopedFlexLayout.length,
      deepElInputWidth: deepElInputWidth.length,
    },
    null,
    2,
  ),
);
