import http from 'k6/http';
import {
  BASE_URL, DEMO_COMPANY_ID, baselineSummary, buildOptions, createLoadUsers, deleteLoadUsers, expect, json,
  passwordToken, staleReads, workflowDuration,
} from './lib/baseline.js';

const TARGETS = Number(__ENV.TARGETS || 20);
const PREFIX = `lt-flow-${__ENV.RUN_SUFFIX || Date.now()}`;

export const options = buildOptions('workflow');

export function setup() {
  const admin = passwordToken(__ENV.ADMIN_USER || 'superadmin', __ENV.ADMIN_PASSWORD || 'superadmin');
  return { admin, users: createLoadUsers(admin, PREFIX, TARGETS, false) };
}

export function workflow(data) {
  const started = Date.now();
  const target = data.users[(__VU * 3 + __ITER) % data.users.length];
  const marker = `F${__VU}x${__ITER}`;

  const list = http.get(`${BASE_URL}/api/v1/admin/users?page=0&size=100`,
    json(data.admin, { tags: { name: 'GET /api/v1/admin/users' } }));
  if (!expect(list, [200])) return;

  const overview = http.get(`${BASE_URL}/api/v1/admin/users/${target.id}/overview`,
    json(data.admin, { tags: { name: 'GET /api/v1/admin/users/{id}/overview' } }));
  if (!expect(overview, [200])) return;
  const role = overview.json('keycloak.realmRoles') && overview.json('keycloak.realmRoles').includes('FOREMAN')
    ? 'BUILDER' : 'FOREMAN';

  const update = http.put(`${BASE_URL}/api/v1/admin/users/${target.id}`, JSON.stringify({
    firstName: 'Flow', lastName: marker, email: `${target.username}@load.test`, role, companyId: DEMO_COMPANY_ID,
  }), json(data.admin, { tags: { name: 'PUT /api/v1/admin/users/{id}' } }));
  if (!expect(update, [200])) return;

  const after = http.get(`${BASE_URL}/api/v1/admin/users?page=0&size=100`,
    json(data.admin, { tags: { name: 'GET /api/v1/admin/users (after write)' } }));
  if (expect(after, [200])) {
    const row = (after.json('content') || []).find((user) => user.id === target.id);
    if (row) staleReads.add(row.lastName !== marker);
  }
  workflowDuration.add(Date.now() - started);
}

export function teardown(data) {
  console.log(`teardown: deleted ${deleteLoadUsers(data.admin, data.users)}/${data.users.length} load users`);
}

export function handleSummary(data) {
  return baselineSummary('scenario-c-workflow',
    'Complex workflow: list -> account overview (Keycloak + audit, parallel) -> update -> read own write', data);
}
