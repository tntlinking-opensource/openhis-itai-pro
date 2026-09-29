/**
 * EMR 模版批量迁移 CLI。
 *
 * 用法（在 apps/web-ele 目录）：
 *   npx tsx src/emrEditor/migration/migrate-emr-template.cli.ts template.json
 *   npx tsx src/emrEditor/migration/migrate-emr-template.cli.ts template.json --out template.migrated.json
 *   npx tsx src/emrEditor/migration/migrate-emr-template.cli.ts ./templates/*.json --in-place
 */
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { migrateEmrTemplate } from './migrate-emr-template';

function parseArgs(argv: string[]) {
  const files: string[] = [];
  let out: string | null = null;
  let inPlace = false;

  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]!;
    if (arg === '--out' && argv[i + 1]) {
      out = argv[++i]!;
      continue;
    }
    if (arg === '--in-place') {
      inPlace = true;
      continue;
    }
    if (!arg.startsWith('-')) {
      files.push(arg);
    }
  }

  return { files, out, inPlace };
}

function migrateFile(filePath: string, out: string | null, inPlace: boolean) {
  const abs = resolve(filePath);
  const raw = JSON.parse(readFileSync(abs, 'utf8')) as unknown;
  const { doc, report } = migrateEmrTemplate(raw);

  const target = inPlace ? abs : out ? resolve(out) : abs.replace(/\.json$/i, '.migrated.json');
  writeFileSync(target, `${JSON.stringify(doc, null, 2)}\n`, 'utf8');

  console.log(`\n✓ ${filePath} → ${target}`);
  console.log(`  slotId 补全: ${report.slotIdsAssigned}`);
  console.log(`  尾部空段删除: ${report.emptyParagraphsRemoved}`);
  console.log(`  可填域节点: ${report.fillableNodeCount}`);
  if (report.warnings.length) {
    console.warn(`  警告: ${report.warnings.join('; ')}`);
  } else {
    console.log('  警告: 无');
  }
}

function main() {
  const { files, out, inPlace } = parseArgs(process.argv.slice(2));
  if (files.length === 0) {
    console.error('请指定至少一个模版 JSON 文件路径');
    process.exit(1);
  }
  if (out && (files.length > 1 || inPlace)) {
    console.error('--out 仅支持单文件；批量请用 --in-place');
    process.exit(1);
  }

  for (const file of files) {
    migrateFile(file, out, inPlace);
  }
}

main();
