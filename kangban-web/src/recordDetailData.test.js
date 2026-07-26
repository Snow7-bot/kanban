import assert from 'node:assert/strict';
import test from 'node:test';
import { mapRecordDetail, safeParseJson } from './pages/recordDetailData.js';

test('病历详情映射后端字段与结构化 JSON', () => {
  const detail = mapRecordDetail({
    recordName: '年度体检报告', recordType: 'PDF', recordDate: '2026-07-22', fileUrl: 'https://example.test/report.pdf',
    hospital: '测试医院', department: '内科', doctor: '测试医生', confidence: 95,
    diagnosisData: JSON.stringify({ diagnosis: '血压偏高', findings: '建议复查', chiefComplaint: '头晕' }),
    medicationsData: JSON.stringify([{ name: '示例药品', spec: '5mg', usage: '每日一次' }]),
    advicesData: JSON.stringify({ advices: ['规律监测', '按时复诊'] }),
  });
  assert.equal(detail.recordDate, '2026-07-22');
  assert.equal(detail.fileUrl, 'https://example.test/report.pdf');
  assert.deepEqual(detail.diagnoses, [{ name: '血压偏高', detail: '建议复查' }]);
  assert.deepEqual(detail.medications, [{ name: '示例药品', spec: '5mg', usage: '每日一次' }]);
  assert.deepEqual(detail.advices, ['规律监测', '按时复诊']);
});

test('无效结构化 JSON 不回退到演示数据', () => {
  assert.equal(safeParseJson('{invalid'), null);
  const detail = mapRecordDetail({ diagnosisData: '{invalid', medicationsData: '', advicesData: null });
  assert.deepEqual(detail.diagnoses, []);
  assert.deepEqual(detail.medications, []);
  assert.deepEqual(detail.advices, []);
});

test('病历详情识别 OCR 任务返回的中文字段', () => {
  const detail = mapRecordDetail({
    diagnosisData: JSON.stringify({ '诊断结论': '未见明显异常', '检查所见': '各项指标正常', '建议': ['定期复查'] }),
  });
  assert.deepEqual(detail.diagnoses, [{ name: '未见明显异常', detail: '各项指标正常' }]);
  assert.deepEqual(detail.advices, ['定期复查']);
});
