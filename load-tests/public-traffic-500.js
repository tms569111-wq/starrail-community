import http from 'k6/http';
import { check, sleep } from 'k6';
import { parseHTML } from 'k6/html';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

export const options = {
    scenarios: {
        public_traffic: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 50 },
                { duration: '30s', target: 200 },
                { duration: '1m', target: 500 },
                { duration: '2m', target: 500 },
                { duration: '1m', target: 0 },
            ],
            gracefulRampDown: '30s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        'http_req_duration{page:home}': ['p(95)<1500'],
        'http_req_duration{page:character}': ['p(95)<1500'],
    },
};

export default function () {
    const home = http.get(`${BASE_URL}/`, { tags: { page: 'home' } });
    const document = parseHTML(home.body);
    const links = document.find('a.tier-character');

    check(home, {
        '홈 200': (response) => response.status === 200,
        '홈 본문 정상': (response) => response.body.includes('붕스청문회'),
        '캐릭터 링크 존재': () => links.size() > 0,
    });

    if (home.status === 200 && links.size() > 0) {
        const path = links.eq(Math.floor(Math.random() * links.size())).attr('href');
        const url = path.startsWith('http') ? path : `${BASE_URL}${path}`;
        const character = http.get(url, { tags: { page: 'character' } });
        check(character, {
            '캐릭터 200': (response) => response.status === 200,
            '캐릭터 본문 정상': (response) => response.body.includes('TIER RESULT'),
        });
    }

    sleep(1 + Math.random() * 2);
}
