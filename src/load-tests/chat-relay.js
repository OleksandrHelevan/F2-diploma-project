import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const MESSAGES = Number(__ENV.MESSAGES || 6);
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WS_URL = BASE_URL.replace(/^http/, 'ws') + '/ws';
const received = new Counter('relay_messages_received');

export const options = {
  scenarios: {
    listener: { executor: 'per-vu-iterations', vus: 1, iterations: 1, exec: 'listen', maxDuration: '40s' },
    sender: { executor: 'per-vu-iterations', vus: 1, iterations: 1, exec: 'send', startTime: '3s', maxDuration: '30s' },
  },
  thresholds: {
    relay_messages_received: [`count==${MESSAGES}`],
    checks: ['rate==1'],
  },
};

function token(username, password) {
  const response = http.post(__ENV.TOKEN_URL || 'http://localhost:8084/realms/bricklayers/protocol/openid-connect/token', {
    grant_type: 'password', client_id: 'bricklayers-load-test', username, password,
  });
  if (response.status !== 200) throw new Error(`Token for ${username}: HTTP ${response.status}`);
  return response.json('access_token');
}

export function setup() {
  const foreman = token('foreman', 'foreman');
  const worker = token('worker', 'worker');
  const auth = (t) => ({ headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' } });

  const builders = http.get(`${BASE_URL}/api/v1/users?role=BUILDER`, auth(foreman));
  const target = builders.json().find((user) => user.username === 'worker');
  if (!target) throw new Error('worker is not synchronised into user-service yet');
  const chat = http.post(`${BASE_URL}/api/v1/chats/direct`,
    JSON.stringify({ type: 'FOREMAN_BUILDER', targetUserId: target.id }), auth(foreman));
  if (chat.status !== 201) throw new Error(`Cannot open chat: HTTP ${chat.status} ${chat.body}`);
  return { foreman, worker, chatId: chat.json('id'), marker: `relay-${Date.now()}` };
}

function frame(command, headers, body = '') {
  const lines = Object.entries(headers).map(([name, value]) => `${name}:${value}`);
  return `${command}\n${lines.join('\n')}\n\n${body}\0`;
}

export function listen(data) {
  const seen = new Set();
  const response = ws.connect(WS_URL, {}, (socket) => {
    socket.on('open', () => {
      socket.send(frame('CONNECT', { 'accept-version': '1.2', host: 'localhost', 'heart-beat': '0,0', Authorization: `Bearer ${data.worker}` }));
    });
    socket.on('message', (raw) => {
      for (const part of raw.split('\0').filter((p) => p.trim())) {
        const [head, ...rest] = part.replace(/^\n+/, '').split('\n\n');
        const command = head.split('\n')[0];
        if (command === 'CONNECTED') {
          socket.send(frame('SUBSCRIBE', { id: 'sub-0', destination: `/topic/chats/${data.chatId}` }));
        } else if (command === 'MESSAGE') {
          const event = JSON.parse(rest.join('\n\n'));
          const content = event.payload && event.payload.content;
          if (event.type === 'NEW_MESSAGE' && content && content.startsWith(data.marker) && !seen.has(content)) {
            seen.add(content);
            received.add(1);
            if (seen.size === MESSAGES) socket.close();
          }
        } else if (command === 'ERROR') {
          console.error(`STOMP error: ${part}`);
          socket.close();
        }
      }
    });
    socket.setTimeout(() => socket.close(), 30000);
  });
  const instance = response.headers['X-Instance-Id'] || response.headers['X-Instance-ID'];
  console.log(`listener WebSocket held by chat replica ${instance}, received ${seen.size}/${MESSAGES} messages`);
  check(response, { 'WebSocket upgraded (101)': (r) => r.status === 101 });
  check(seen, { 'listener received every message': (s) => s.size === MESSAGES });
}

export function send(data) {
  const instances = {};
  for (let i = 1; i <= MESSAGES; i++) {
    const response = http.post(`${BASE_URL}/api/v1/chats/${data.chatId}/messages`,
      JSON.stringify({ content: `${data.marker} #${i}`, attachments: [] }),
      { headers: { Authorization: `Bearer ${data.foreman}`, 'Content-Type': 'application/json' } });
    check(response, { 'message stored (201)': (r) => r.status === 201 });
    const instance = response.headers['X-Instance-Id'] || response.headers['X-Instance-ID'];
    instances[instance] = (instances[instance] || 0) + 1;
    sleep(0.5);
  }
  console.log(`messages published by chat replicas: ${JSON.stringify(instances)}`);
  check(instances, { 'messages were published by at least two replicas': (m) => Object.keys(m).length >= 2 });
}
