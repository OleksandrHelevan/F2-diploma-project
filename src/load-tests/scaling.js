import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const byInstance = new Counter('requests_by_instance');
const errors = new Rate('business_errors');
const identities = (__ENV.INSTANCE_IDS || '').split(',').filter(Boolean);

const thresholds = { http_reqs: ['count>0'] };
for (const instance of identities) {
  thresholds[`requests_by_instance{instance:${instance}}`] = ['count>=0'];
}
if (__ENV.REQUIRE_NO_ERRORS === 'true') thresholds.business_errors = ['rate==0'];

export const options = {
  vus: Number(__ENV.VUS || 20),
  duration: __ENV.DURATION || '30s',
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
  thresholds,
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const PATH = __ENV.TARGET_PATH || '/api/v1/me';

export function setup() {
  const response = http.post(__ENV.TOKEN_URL || 'http://localhost:8084/realms/bricklayers/protocol/openid-connect/token', {
    grant_type: 'password',
    client_id: 'bricklayers-load-test',
    username: __ENV.LOAD_USER || 'worker',
    password: __ENV.LOAD_PASSWORD || 'worker',
  });
  if (response.status !== 200) {
    throw new Error(`Cannot obtain token: HTTP ${response.status} ${response.body}`);
  }
  return { token: response.json('access_token') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };
  if (__ENV.SLOW_INSTANCE) headers['X-Lab-Slow-Instance'] = __ENV.SLOW_INSTANCE;

  const response = http.get(`${BASE_URL}${PATH}`, { headers, timeout: '20s' });
  const identity = response.headers['X-Instance-Id'] || response.headers['X-Instance-ID'] || 'unavailable';
  const valid = check(response, {
    'HTTP 200 with backend identity': (r) => r.status === 200 && identity !== 'unavailable',
  });
  errors.add(!valid);
  byInstance.add(1, { instance: identity });
}

export function handleSummary(data) {
  const latency = data.metrics.http_req_duration?.values || {};
  const distribution = {};
  for (const identity of identities) {
    distribution[identity] = data.metrics[`requests_by_instance{instance:${identity}}`]?.values.count || 0;
  }
  const report = {
    run: __ENV.RUN_NAME || 'manual',
    path: PATH,
    vus: options.vus,
    duration: options.duration,
    slowInstance: __ENV.SLOW_INSTANCE || null,
    requests: data.metrics.http_reqs?.values.count || 0,
    rps: data.metrics.http_reqs?.values.rate || 0,
    avgMs: latency.avg,
    p50Ms: latency['p(50)'],
    p95Ms: latency['p(95)'],
    p99Ms: latency['p(99)'],
    errorRate: data.metrics.business_errors?.values.rate || 0,
    distribution,
  };
  const name = (__ENV.RUN_NAME || 'manual').replace(/[^a-zA-Z0-9_-]/g, '_');
  return {
    stdout: `${JSON.stringify(report, null, 2)}\n`,
    [`/results/${name}.json`]: JSON.stringify({ report, metrics: data.metrics }, null, 2),
  };
}
