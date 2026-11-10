import http from 'k6/http';
import {
  BASE_URL, DEMO_COMPANY_ID, baselineSummary, buildOptions, createLoadUsers, deleteLoadUsers, expect, json, passwordToken,
} from './lib/baseline.js';

const TARGETS = Number(__ENV.TARGETS || 20);
const PREFIX = `lt-write-${__ENV.RUN_SUFFIX || Date.now()}`;

export const options = buildOptions('write');

export function setup() {
  const admin = passwordToken(__ENV.ADMIN_USER || 'superadmin', __ENV.ADMIN_PASSWORD || 'superadmin');
  return { admin, users: createLoadUsers(admin, PREFIX, TARGETS, false) };
}

export function write(data) {
  const target = data.users[(__VU * 7 + __ITER) % data.users.length];
  const response = http.put(`${BASE_URL}/api/v1/admin/users/${target.id}`, JSON.stringify({
    firstName: 'Load',
    lastName: `V${__VU}I${__ITER}`,
    email: `${target.username}@load.test`,
    role: __ITER % 2 === 0 ? 'BUILDER' : 'FOREMAN',
    companyId: DEMO_COMPANY_ID,
  }), json(data.admin, { tags: { name: 'PUT /api/v1/admin/users/{id}' } }));
  expect(response, [200]);
}

export function teardown(data) {
  console.log(`teardown: deleted ${deleteLoadUsers(data.admin, data.users)}/${data.users.length} load users`);
}

export function handleSummary(data) {
  return baselineSummary('scenario-b-write',
    'Write-intensive: profile updates (PostgreSQL row update + audit + Keycloak Admin API + e-mail hand-off)', data);
}
