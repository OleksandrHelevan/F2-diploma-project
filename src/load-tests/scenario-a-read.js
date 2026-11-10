import http from 'k6/http';
import {
  BASE_URL, baselineSummary, buildOptions, createLoadUsers, deleteLoadUsers, expect, json, passwordToken,
} from './lib/baseline.js';

const READERS = Number(__ENV.READERS || 5);
const PREFIX = `lt-read-${__ENV.RUN_SUFFIX || Date.now()}`;

export const options = buildOptions('read');

export function setup() {
  const admin = passwordToken(__ENV.ADMIN_USER || 'superadmin', __ENV.ADMIN_PASSWORD || 'superadmin');
  const users = createLoadUsers(admin, PREFIX, READERS, true);
  const tokens = users.map((user) => passwordToken(user.username, user.password));
  return { admin, users, tokens };
}

export function read(data) {
  const token = data.tokens[__VU % data.tokens.length];
  expect(http.get(`${BASE_URL}/api/v1/me`, json(token, { tags: { name: 'GET /api/v1/me' } })), [200]);
  expect(http.get(`${BASE_URL}/api/v1/users`, json(token, { tags: { name: 'GET /api/v1/users' } })), [200]);
  expect(http.get(`${BASE_URL}/api/v1/users?role=FOREMAN`,
    json(token, { tags: { name: 'GET /api/v1/users?role' } })), [200]);
}

export function teardown(data) {
  console.log(`teardown: deleted ${deleteLoadUsers(data.admin, data.users)}/${data.users.length} load users`);
}

export function handleSummary(data) {
  return baselineSummary('scenario-a-read',
    'Read-intensive: own profile (cache) + colleague directory (DB read, replica in Lab 6)', data);
}
