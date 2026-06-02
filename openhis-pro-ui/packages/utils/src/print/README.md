<script setup lang="ts">
import { usePdfPrint } from '@vben/hooks';

const { printFromUrl, printFromBlob, printFromResponse, isPrinting } = usePdfPrint();

// 方式1：从后端 URL 直接打印
const handlePrint = () => {
  printFromUrl('/api/print/pdf/123', {
    autoPrint: true, // 自动打印
  });
};

// 方式2：从后端返回的 Blob 打印
const handlePrintBlob = async () => {
  const response = await fetch('/api/print/pdf/123');
  const blob = await response.blob();
  printFromBlob(blob, { autoPrint: true });
};

// 方式3：从 Response 直接打印
const handlePrintResponse = async () => {
  const response = await fetch('/api/print/pdf/123');
  await printFromResponse(response, { autoPrint: true });
};
</script>
<script setup lang="ts">
import { useHiprint } from '@vben/hooks';

const { loadFromJson, print } = useHiprint();

// 从后端获取模板 JSON 并打印
const handlePrint = async () => {
  const response = await fetch('/api/print/template/123');
  const templateJson = await response.json();
  
  loadFromJson(templateJson);
  print({ title: '打印标题', data: '打印数据' });
};
</script>
