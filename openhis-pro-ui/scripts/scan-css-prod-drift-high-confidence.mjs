import fs from 'node:fs';
import path from 'node:path';

/**
 * 高置信：与诊断弹窗同类 —— 同一父级内 ElInput 与 ElButton 相邻，
 * 且未见 search-input-wrap / width:0 包裹加固。
 */
function walk(dir, acc = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules') continue;
    if (entry.name === 'warehouse' && dir.replace(/\\/g, '/').includes('/views/')) continue;
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) walk(full, acc);
    else if (entry.name.endsWith('.vue')) acc.push(full);
  }
  return acc;
}

const root = path.resolve('apps/web-ele/src');
const toRel = (f) => path.relative(root, f).replace(/\\/g, '/');

/** 粗提取：含 ElInput 与 ElButton 的较短模板片段 */
function findRiskyBlocks(source) {
  const hits = [];
  // 在 class 含 flex / *-row / toolbar / search 的 div 块里找
  const re =
    /<(?:div|section|header|aside|form)([^>]*class="([^"]*)"[^>]*)>([\s\S]{0,1200}?)<\/(?:div|section|header|aside|form)>/g;
  let m;
  while ((m = re.exec(source))) {
    const className = m[2] || '';
    const body = m[3] || '';
    const isFlexRow =
      /\bflex\b/.test(className) ||
      /(search|toolbar|tool|filter|query|action|form)-?row/.test(className) ||
      /\b(toolbar|filter-bar|query-bar|search-bar|tool-bar)\b/.test(className);
    if (!isFlexRow) continue;
    if (!/ElInput|el-input/.test(body)) continue;
    if (!/ElButton|el-button/.test(body)) continue;

    const hasWrap =
      /search-input-wrap/.test(body) ||
      /class="[^"]*\b(?:flex-1|min-w-0|w-0)\b[^"]*"[\s\S]{0,200}<(?:ElInput|el-input)/.test(
        body,
      ) ||
      /<(?:div|span)[^>]*class="[^"]*(?:flex-1|min-w-0)[^"]*"[\s\S]{0,300}<(?:ElInput|el-input)/.test(
        body,
      );

    const inputHasFlexClass =
      /<(?:ElInput|el-input)[^>]*class="[^"]*\b(?:flex-1|min-w-0|w-0|w-auto)\b/.test(body);

    if (hasWrap || inputHasFlexClass) continue;

    // 排除明显不在同一行的大块（多个 ElInput 表单）
    const inputCount = (body.match(/<ElInput|<el-input/g) || []).length;
    const buttonCount = (body.match(/<ElButton|<el-button/g) || []).length;
    if (inputCount > 3) continue;

    hits.push({
      className: className.slice(0, 80),
      inputCount,
      buttonCount,
      snippet: body
        .replace(/\s+/g, ' ')
        .trim()
        .slice(0, 160),
    });
  }
  return hits;
}

/** 高置信：关键布局只写在 scoped，根节点没有同等 Tailwind flex/grid */
function scopedOnlyCriticalLayout(source) {
  if (!/<style\s+scoped/.test(source)) return false;
  const styleMatch = source.match(/<style\s+scoped[^>]*>([\s\S]*?)<\/style>/);
  if (!styleMatch) return false;
  const style = styleMatch[1];
  const hasCritical =
    /grid-template-columns|display:\s*flex|display:\s*grid/.test(style) &&
    /(layout|toolbar|search-row|aside|sidebar|tri-pane|pane|queue|workstation)/i.test(
      style + source.slice(0, 500),
    );
  if (!hasCritical) return false;
  // 模板根附近已有 flex/grid 原子类则降级
  const template = source.match(/<template>([\s\S]*?)<\/template>/)?.[1] || '';
  const rootHasUtility =
    /class="[^"]*\b(flex|grid|flex-col|grid-cols)\b/.test(template.slice(0, 800));
  return !rootHasUtility;
}

const files = walk(root);
const high = [];
const medium = [];

for (const file of files) {
  const source = fs.readFileSync(file, 'utf8');
  const rel = toRel(file);
  const blocks = findRiskyBlocks(source);
  if (blocks.length) {
    high.push({ rel, blocks: blocks.slice(0, 3), blockCount: blocks.length });
  } else if (scopedOnlyCriticalLayout(source)) {
    medium.push(rel);
  }
}

console.log('=== HIGH: flex行内裸ElInput+ElButton（未见包裹加固）===');
console.log('count', high.length);
for (const item of high) {
  console.log(`\n${item.rel}  (hits≈${item.blockCount})`);
  for (const b of item.blocks) {
    console.log(`  class="${b.className}"  input=${b.inputCount} btn=${b.buttonCount}`);
    console.log(`  … ${b.snippet}`);
  }
}

console.log('\n=== MEDIUM: 关键布局偏依赖 scoped 且根节点缺 flex/grid 原子类 ===');
console.log('count', medium.length);
medium.slice(0, 40).forEach((r) => console.log(' ', r));
if (medium.length > 40) console.log(`  … +${medium.length - 40} more`);
