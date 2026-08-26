import http from 'k6/http';
import { check, fail } from 'k6';
import { parseHTML } from 'k6/html';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');
const UID = __ENV.UID || '';
const RAW_COOKIE = __ENV.SESSION_COOKIE || '';
const COOKIE = RAW_COOKIE.includes('=') ? RAW_COOKIE : `JSESSIONID=${RAW_COOKIE}`;

export const options = {
    scenarios: {
        uid_guard: {
            executor: 'shared-iterations',
            vus: 200,
            iterations: 200,
            maxDuration: '30s',
        },
    },
    thresholds: {
        checks: ['rate>0.99'],
        http_req_failed: ['rate<0.01'],
        'http_req_duration{operation:uid-submit}': ['p(95)<10000'],
    },
};

export function setup() {
    if (!/^\d{9}$/.test(UID)) fail('UID 환경변수에 테스트할 숫자 9자리를 넣으세요.');
    if (!RAW_COOKIE) fail('SESSION_COOKIE 환경변수에 테스트 계정의 JSESSIONID를 넣으세요.');

    const response = http.get(`${BASE_URL}/me/profile`, {
        headers: { Cookie: COOKIE },
        redirects: 0,
        tags: { operation: 'csrf-setup' },
    });
    if (response.status !== 200) {
        fail(`로그인 세션 확인 실패: 상태=${response.status}`);
    }

    const token = parseHTML(response.body)
        .find('input[name="_csrf"]')
        .eq(0)
        .attr('value');
    if (!token) fail('프로필 화면에서 CSRF 토큰을 찾지 못했습니다.');
    return { token };
}

export default function (data) {
    const response = http.post(
        `${BASE_URL}/me/profile/challenge`,
        { uid: UID, _csrf: data.token },
        {
            headers: { Cookie: COOKIE },
            redirects: 0,
            tags: { operation: 'uid-submit' },
        }
    );

    check(response, {
        'UID 요청은 안전하게 리다이렉트': (result) => result.status === 302,
    });
}
