<script setup lang="ts">
import { ref, watch } from 'vue';

import { useHiprint, usePdfPrint } from '@vben/hooks';

import { EditTable } from '@vben-core/table-layout-ui';

import { ElButton, ElForm, ElFormItem, ElMessage, ElTabPane, ElTabs } from 'element-plus';

import { getProductListApi } from '#/api/core/common';

import ProductSelectTable from '../components/select-product.vue';
// PDF 打印
const { printFromUrl, printFromBlob, printFromBase64, printFromResponse, download, isPrinting } =
  usePdfPrint();

const pdfUrl = ref('https://example.com/api/print/pdf/123');

// 从 URL 打印 PDF
const handlePrintPdfFromUrl = () => {
  printFromUrl(pdfUrl.value, {
    title: '打印文档',
    autoPrint: true,
  });
};

// 从 Blob 打印 PDF
const handlePrintPdfFromBlob = async () => {
  try {
    const response = await fetch(pdfUrl.value);
    const blob = await response.blob();
    printFromBlob(blob, { autoPrint: true });
  } catch {
    ElMessage.error('获取 PDF 失败');
  }
};

// 从 Base64 打印 PDF
const handlePrintPdfFromBase64 = () => {
  // 示例 Base64（实际使用时从后端获取）
  const base64 = 'data:application/pdf;base64,JVBERi0xLjQK...';
  printFromBase64(base64, { autoPrint: true });
};

// 从 Response 打印 PDF
const handlePrintPdfFromResponse = async () => {
  try {
    const response = await fetch(pdfUrl.value);
    await printFromResponse(response, { autoPrint: true });
  } catch {
    ElMessage.error('打印失败');
  }
};

// 下载 PDF
const handleDownloadPdf = () => {
  download(pdfUrl.value, 'document.pdf');
};

// JSON 模板打印
const { loadFromJson, print, preview, getJson } = useHiprint();
const templateJson = ref<null | string>(null);

// SelectTable 示例相关
const selectValue = ref<any>(null);
const selectValueMultiple = ref<(number | string)[] | number | string>('');

const tableColumns = [
  { prop: 'productName', label: '产品名称', width: 200 },
  { prop: 'unitCodeDictText', label: '单位', width: 100 },
  { prop: 'manufacturer', label: '生产厂家', width: 200 },
  { prop: 'price', label: '价格' },
];

// 处理远程加载方法（用于多选示例）
const handleRemoteMethod = async (params: {
  [key: string]: any;
  pageNo: number;
  pageSize: number;
  keyword?: string;
}) => {
  const { pageNo, pageSize, keyword, ...restParams } = params;
  const requestParams: any = {
    pageNo,
    pageSize,
    ...restParams,
  };
  if (keyword) {
    requestParams.keyword = keyword;
  }
  const res = await getProductListApi(requestParams);

  return res;
};

// 加载模板
const handleLoadTemplate = () => {
  // 示例模板 JSON（实际使用时从后端或配置文件获取）
  const template = {
    panels: [
      {
        index: 0,
        height: 297,
        width: 210,
        paperHeader: 10,
        paperFooter: 10,
      },
    ],
  };
  loadFromJson(template);
  ElMessage.success('模板加载成功');
};

// 预览
const handlePreview = () => {
  preview({
    title: '预览标题',
    content: '预览内容',
  });
};

// 打印
const handlePrint = () => {
  print({
    title: '打印标题',
    content: '打印内容',
  });
};

// 获取模板 JSON
const handleGetJson = () => {
  const json = getJson();
  if (json) {
    templateJson.value = json;
    ElMessage.success('模板 JSON 已获取');
  } else {
    ElMessage.warning('请先加载模板');
  }
};

watch(selectValue, (newVal) => {
  // 控制台调试：这里保留为 warning 级别，符合 eslint 规则
  console.warn('Select value changed:', newVal);
});
</script>

<template>
  <div class="p-4">
    <h2 class="mb-4">打印示例</h2>

    <ElTabs>
      <ElTabPane label="PDF 打印" name="pdf">
        <div class="mb-4 flex flex-col gap-2">
          <ElButton type="primary" :loading="isPrinting" @click="handlePrintPdfFromUrl">
            从 URL 打印 PDF
          </ElButton>
          <ElButton :loading="isPrinting" @click="handlePrintPdfFromBlob">
            从 Blob 打印 PDF
          </ElButton>
          <ElButton :loading="isPrinting" @click="handlePrintPdfFromBase64">
            从 Base64 打印 PDF
          </ElButton>
          <ElButton :loading="isPrinting" @click="handlePrintPdfFromResponse">
            从 Response 打印 PDF
          </ElButton>
          <ElButton @click="handleDownloadPdf">下载 PDF</ElButton>
        </div>
        <div class="text-sm text-gray-600">
          <p>
            PDF URL: <code>{{ pdfUrl }}</code>
          </p>
        </div>
      </ElTabPane>

      <ElTabPane label="JSON 模板打印" name="json">
        <div class="mb-4 flex gap-2">
          <ElButton @click="handleLoadTemplate">加载模板</ElButton>
          <ElButton @click="handlePreview">预览</ElButton>
          <ElButton type="primary" @click="handlePrint">打印</ElButton>
          <ElButton @click="handleGetJson">获取模板 JSON</ElButton>
        </div>

        <div v-if="templateJson" class="mb-4">
          <h3>模板 JSON：</h3>
          <pre class="max-h-40 overflow-auto rounded bg-gray-100 p-2 text-xs">{{
            templateJson
          }}</pre>
        </div>
      </ElTabPane>
    </ElTabs>

    <div class="mt-8">
      <h2 class="mb-4">SelectTable 组件示例</h2>

      <div class="mb-6">
        <h3 class="mb-2 text-lg font-semibold">远程分页加载（表格形式）</h3>
        <div class="mb-2 text-sm text-gray-600">选择产品（支持搜索和分页）：</div>
        <SelectTable
          class="w-[400px]"
          v-model="selectValueMultiple"
          :remote-method="handleRemoteMethod"
          :columns="tableColumns"
          placeholder="请输入产品名称搜索"
          :page-size="20"
          value-key="id"
          label-key="productName"
        />
        <div class="mt-2 text-sm text-gray-600">
          当前选中值：
          <code>{{ selectValueMultiple }}</code>
        </div>
      </div>

      <ElForm>
        <ElFormItem label="产品" class="w-[400px]">
          <ProductSelectTable v-model="selectValue" />
        </ElFormItem>
      </ElForm>
    </div>
    <EditTable />
  </div>
</template>
