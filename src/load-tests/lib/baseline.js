import http from 'k6/http';
import { Rate, Trend } from 'k6/metrics';

export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
export const TOKEN_URL = __ENV.TOKEN_URL || 'http://localhost:8084/realms/bricklayers/protocol/openid-connect/token';
export const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8084';
export const DEMO_COMPANY_ID = '5f0c8f3e-6d2a-4b7e-9c41-2a7d3e8b1c05';

export const STAGES = (__ENV.STAGES || '10,25,50,100,200').split(',').map((v) => Number(v.trim())).filter((v) => v > 0);
export const STAGE_SECONDS = Number(__ENV.STAGE_SECONDS || 30);
export const WARMUP_SECONDS = Number(__ENV.WARMUP_SECONDS || 20);
export const WARMUP_VUS = Number(__ENV.WARMUP_VUS || 5);
const GAP_SECONDS = 2;
const SUBMETRICS = {
  http_req_duration: 'max>=0',
  workflow_duration: 'max>=0',
  http_reqs: 'count>=0',
  iterations: 'count>=0',
  business_errors: 'rate>=0',
  stale_reads: 'rate>=0',
};

export const businessErrors = new Rate('business_errors');
export const staleReads = new Rate('stale_reads');
export const workflowDuration = new Trend('workflow_duration', true);

export function buildOptions(exec) {
  const scenarios = {
    warmup: {
      executor: 'constant-vus', exec, vus: WARMUP_VUS, duration: `${WARMUP_SECONDS}s`,
      gracefulStop: '5s', tags: { stage: 'warmup' },
    },
  };
  const thresholds = {};
  let start = WARMUP_SECONDS + GAP_SECONDS;
  for (const vus of STAGES) {
    scenarios[`vus_${vus}`] = {
      executor: 'constant-vus', exec, vus, duration: `${STAGE_SECONDS}s`, startTime: `${start}s`,
      gracefulStop: '10s', tags: { stage: String(vus) },
    };
    for (const [metric, expression] of Object.entries(SUBMETRICS)) {
      thresholds[`${metric}{stage:${vus}}`] = [expression];
    }
    start += STAGE_SECONDS + GAP_SECONDS;
  }
  return {
    scenarios,
    thresholds,
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
    setupTimeout: '120s',
    teardownTimeout: '180s',
    discardResponseBodies: false,
  };
}

export function passwordToken(username, password) {
  const response = http.post(TOKEN_URL, {
    grant_type: 'password', client_id: 'bricklayers-load-test', username, password,
  }, { tags: { name: 'token', stage: 'setup' } });
  if (response.status !== 200) {
    throw new Error(`Token for ${username}: HTTP ${response.status} ${response.body}`);
  }
  return response.json('access_token');
}

export function keycloakAdminToken() {
  const response = http.post(`${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'admin-cli',
    username: __ENV.KEYCLOAK_ADMIN || 'admin', password: __ENV.KEYCLOAK_ADMIN_PASSWORD || 'admin',
  }, { tags: { name: 'keycloak-admin-token', stage: 'setup' } });
  if (response.status !== 200) {
    throw new Error(`Keycloak admin token: HTTP ${response.status} ${response.body}`);
  }
  return response.json('access_token');
}

export function json(token, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, ...extra };
}

export function createLoadUsers(adminToken, prefix, count, withPassword) {
  const kcToken = withPassword ? keycloakAdminToken() : null;
  const users = [];
  for (let i = 0; i < count; i++) {
    const username = `${prefix}-${i}`;
    const created = http.post(`${BASE_URL}/api/v1/admin/users`, JSON.stringify({
      username, email: `${username}@load.test`, firstName: 'Load', lastName: `User${i}`,
      role: 'BUILDER', companyId: DEMO_COMPANY_ID,
    }), json(adminToken, { tags: { name: 'setup-create-user', stage: 'setup' } }));
    if (created.status !== 201) {
      throw new Error(`Cannot create ${username}: HTTP ${created.status} ${created.body}`);
    }
    const user = { id: created.json('id'), username, password: null };
    if (withPassword) {
      const found = http.get(`${KEYCLOAK_URL}/admin/realms/bricklayers/users?exact=true&username=${username}`,
        json(kcToken, { tags: { name: 'setup-find-user', stage: 'setup' } }));
      const keycloakId = found.json('0.id');
      const password = `Load-${username}-1`;
      const reset = http.put(`${KEYCLOAK_URL}/admin/realms/bricklayers/users/${keycloakId}/reset-password`,
        JSON.stringify({ type: 'password', value: password, temporary: false }),
        json(kcToken, { tags: { name: 'setup-password', stage: 'setup' } }));
      if (reset.status !== 204) {
        throw new Error(`Cannot set password for ${username}: HTTP ${reset.status} ${reset.body}`);
      }
      http.put(`${KEYCLOAK_URL}/admin/realms/bricklayers/users/${keycloakId}`,
        JSON.stringify({ emailVerified: true, requiredActions: [] }),
        json(kcToken, { tags: { name: 'setup-profile', stage: 'setup' } }));
      user.password = password;
    }
    users.push(user);
  }
  return users;
}

export function deleteLoadUsers(adminToken, users) {
  let deleted = 0;
  for (const user of users) {
    const response = http.del(`${BASE_URL}/api/v1/admin/users/${user.id}`, null,
      json(adminToken, { tags: { name: 'teardown-delete-user', stage: 'teardown' } }));
    if (response.status === 204 || response.status === 200) deleted++;
  }
  return deleted;
}

export function expect(response, statuses) {
  const ok = statuses.includes(response.status);
  businessErrors.add(!ok);
  return ok;
}

function metricValues(data, name, stage) {
  const metric = data.metrics[`${name}{stage:${stage}}`];
  return metric ? metric.values : {};
}

export function baselineSummary(scenarioName, description, data) {
  const rows = STAGES.map((vus) => {
    const duration = metricValues(data, 'http_req_duration', vus);
    const requests = metricValues(data, 'http_reqs', vus).count || 0;
    const iterations = metricValues(data, 'iterations', vus).count || 0;
    const workflow = metricValues(data, 'workflow_duration', vus);
    return {
      vus,
      requests,
      rps: requests / STAGE_SECONDS,
      iterationsPerSecond: iterations / STAGE_SECONDS,
      avgMs: duration.avg || 0,
      p50Ms: duration.med || 0,
      p95Ms: duration['p(95)'] || 0,
      p99Ms: duration['p(99)'] || 0,
      maxMs: duration.max || 0,
      errorRate: metricValues(data, 'business_errors', vus).rate || 0,
      staleReadRate: metricValues(data, 'stale_reads', vus).rate || 0,
      workflowP95Ms: workflow['p(95)'] || 0,
    };
  });
  const report = {
    scenario: scenarioName,
    description,
    run: __ENV.RUN_NAME || scenarioName,
    environment: __ENV.ENV_LABEL || '',
    stageSeconds: STAGE_SECONDS,
    warmupSeconds: WARMUP_SECONDS,
    rows,
  };
  const fmt = (n, d = 1) => Number(n).toFixed(d);
  const table = [
    `${scenarioName}: ${description}  [${report.environment}]`,
    '| VUs | Throughput (RPS) | Avg ms | p50 ms | p95 ms | p99 ms | Max ms | Error % |',
    '|---:|---:|---:|---:|---:|---:|---:|---:|',
    ...rows.map((r) => `| ${r.vus} | ${fmt(r.rps)} | ${fmt(r.avgMs)} | ${fmt(r.p50Ms)} | ${fmt(r.p95Ms)} | ${fmt(r.p99Ms)} | ${fmt(r.maxMs)} | ${fmt(r.errorRate * 100, 2)} |`),
  ].join('\n');
  const name = report.run.replace(/[^a-zA-Z0-9_-]/g, '_');
  const dir = (__ENV.RESULTS_DIR || '').replace(/[^a-zA-Z0-9_-]/g, '');
  return {
    stdout: `${table}\n`,
    [`/results/${dir ? `${dir}/` : ''}${name}.json`]: JSON.stringify(report, null, 2),
  };
}
