import http from 'k6/http';
import { check, sleep } from 'k6';
import { parseHTML } from 'k6/html';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080')
    .replace(/\/+$/, '');

export const options = {
    stages: [
        { duration: '20s', target: 5 },
        { duration: '1m', target: 10 },
        { duration: '20s', target: 30 },
        { duration: '1m', target: 30 },
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
        'http_req_duration{page:home}': ['p(95)<800'],
        'http_req_duration{page:character}': ['p(95)<800'],
    },
};

export default function () {
    const homeResponse = http.get(`${BASE_URL}/`, {
        tags: { page: 'home' },
    });

    const document = parseHTML(homeResponse.body);
    const characterLinks = document.find('a.tier-character');
    const linkCount = characterLinks.size();

    check(homeResponse, {
        '홈 상태코드 200': (response) => response.status === 200,
        '홈 내용 정상': (response) =>
            response.body.includes('붕스청문회'),
        '홈에 캐릭터 링크 존재': () => linkCount > 0,
    });

    if (homeResponse.status !== 200 || linkCount === 0) {
        sleep(1);
        return;
    }

    const index = Math.floor(Math.random() * linkCount);
    const characterPath = characterLinks.eq(index).attr('href');

    const characterUrl = characterPath.startsWith('http')
        ? characterPath
        : `${BASE_URL}${characterPath}`;

    const characterResponse = http.get(characterUrl, {
        tags: { page: 'character' },
    });

    if (
        characterResponse.status !== 200 &&
        __VU === 1 &&
        __ITER === 0
    ) {
        console.error(
            `캐릭터 요청 실패: ${characterUrl}, ` +
            `상태=${characterResponse.status}, ` +
            `응답=${characterResponse.body.slice(0, 300)}`
        );
    }

    check(characterResponse, {
        '캐릭터 페이지 상태코드 200': (response) =>
            response.status === 200,
        '캐릭터 페이지 내용 정상': (response) =>
            response.body.includes('TIER RESULT'),
    });

    sleep(1);
}