import assert from 'node:assert/strict';
import test from 'node:test';
import { buildConsultationPayload, buildHealthRecordPayload, buildMedicationPayload } from './api/contracts.js';

test('健康录入请求使用后端 AddHealthRecordRequest 字段', () => {
  const payload = buildHealthRecordPayload({
    memberId: null, metric: 'heart_rate', value: '72', unit: 'bpm',
    recordedDate: '2026-07-22', recordedTime: '08:00', note: '晨起测量',
  });
  assert.deepEqual(payload, {
    memberId: null, metric: 'heart_rate', value: '72', unit: 'bpm',
    recordedDate: '2026-07-22', recordedTime: '08:00', note: '晨起测量',
  });
});

test('添加药品请求拆分剂量单位并序列化提醒时间', () => {
  assert.deepEqual(buildMedicationPayload({
    name: '示例药品', dosage: '1', unit: '片', instruction: '饭后服用',
    frequency: '每天', inventory: 30, times: ['08:00', '20:00'],
  }), {
    name: '示例药品', dosage: '1', unit: '片', instruction: '饭后服用',
    frequency: '每天', inventory: 30, times: '08:00,20:00',
  });
});

test('问诊会话请求提供非空标题和 JSON 患者资料', () => {
  const payload = buildConsultationPayload({ name: '李明', age: 39, gender: '男', id: 7 }, new Date('2026-07-22T00:00:00Z'));
  assert.ok(payload.title.startsWith('在线问诊 - '));
  assert.equal(payload.memberId, null);
  assert.deepEqual(JSON.parse(payload.patientData), {
    name: '李明', age: 39, gender: '男', relation: '本人', patientId: 7,
  });
});
