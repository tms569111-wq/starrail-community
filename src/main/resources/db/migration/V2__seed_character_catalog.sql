-- Character metadata and resource paths are derived from Mar-7th/StarRailRes.
-- See THIRD_PARTY_NOTICES.md before redistribution.

INSERT INTO member_account (id, nickname, status, created_at, updated_at)
VALUES
    (1, '로컬 테스트 유저', 'ACTIVE', NOW(6), NOW(6)),
    (2, '은하열차 승객', 'ACTIVE', NOW(6), NOW(6)),
    (3, '기억의 개척자', 'ACTIVE', NOW(6), NOW(6));

INSERT INTO game_character
(canonical_external_id, slug, name, rarity, path_code, path_name, element_code, element_name,
 icon_url, portrait_url, status, display_order, created_at, updated_at)
VALUES
    ('8009', 'trailblazer-elation', '개척자·환락', 5, 'Elation', '환락', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/8009.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/8009.png', 'ACTIVE', 1, NOW(6), NOW(6)),
    ('8007', 'trailblazer-remembrance', '개척자·기억', 5, 'Memory', '기억', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/8007.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/8007.png', 'ACTIVE', 2, NOW(6), NOW(6)),
    ('8005', 'trailblazer-harmony', '개척자·화합', 5, 'Shaman', '화합', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/8005.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/8005.png', 'ACTIVE', 3, NOW(6), NOW(6)),
    ('8003', 'trailblazer-preservation', '개척자·보존', 5, 'Knight', '보존', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/8003.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/8003.png', 'ACTIVE', 4, NOW(6), NOW(6)),
    ('8001', 'trailblazer-destruction', '개척자·파멸', 5, 'Warrior', '파멸', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/8001.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/8001.png', 'ACTIVE', 5, NOW(6), NOW(6)),
    ('1510', 'himekonova', '히메코•노바', 5, 'Mage', '지식', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1510.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1510.png', 'ACTIVE', 6, NOW(6), NOW(6)),
    ('1509', 'gilgamesh', '길가메시', 5, 'Warrior', '파멸', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1509.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1509.png', 'ACTIVE', 7, NOW(6), NOW(6)),
    ('1508', 'tohsakarin', '토오사카 린', 5, 'Mage', '지식', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1508.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1508.png', 'ACTIVE', 8, NOW(6), NOW(6)),
    ('1507', 'mortenaxblade', '천야•블레이드', 5, 'Warlock', '공허', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1507.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1507.png', 'ACTIVE', 9, NOW(6), NOW(6)),
    ('1506', 'silverwolflv999', '은랑 LV.999', 5, 'Elation', '환락', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1506.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1506.png', 'ACTIVE', 10, NOW(6), NOW(6)),
    ('1505', 'evanescia', '에바네시아', 5, 'Elation', '환락', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1505.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1505.png', 'ACTIVE', 11, NOW(6), NOW(6)),
    ('1504', 'ashveil', '애쉬베일', 5, 'Rogue', '수렵', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1504.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1504.png', 'ACTIVE', 12, NOW(6), NOW(6)),
    ('1502', 'yaoguang', '효광', 5, 'Elation', '환락', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1502.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1502.png', 'ACTIVE', 13, NOW(6), NOW(6)),
    ('1501', 'sparxie', '스파키', 5, 'Elation', '환락', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1501.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1501.png', 'ACTIVE', 14, NOW(6), NOW(6)),
    ('1415', 'cyrene', '키레네', 5, 'Memory', '기억', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1415.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1415.png', 'ACTIVE', 15, NOW(6), NOW(6)),
    ('1414', 'danhengpt', '단항•등황', 5, 'Knight', '보존', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1414.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1414.png', 'ACTIVE', 16, NOW(6), NOW(6)),
    ('1413', 'evernight', '에버나이트', 5, 'Memory', '기억', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1413.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1413.png', 'ACTIVE', 17, NOW(6), NOW(6)),
    ('1412', 'cerydra', '케리드라', 5, 'Shaman', '화합', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1412.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1412.png', 'ACTIVE', 18, NOW(6), NOW(6)),
    ('1410', 'hysilens', '히실렌스', 5, 'Warlock', '공허', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1410.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1410.png', 'ACTIVE', 19, NOW(6), NOW(6)),
    ('1409', 'hyacine', '히아킨', 5, 'Memory', '기억', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1409.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1409.png', 'ACTIVE', 20, NOW(6), NOW(6)),
    ('1408', 'phainon', '파이논', 5, 'Warrior', '파멸', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1408.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1408.png', 'ACTIVE', 21, NOW(6), NOW(6)),
    ('1407', 'castorice', '카스토리스', 5, 'Memory', '기억', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1407.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1407.png', 'ACTIVE', 22, NOW(6), NOW(6)),
    ('1406', 'cipher', '사이퍼', 5, 'Warlock', '공허', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1406.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1406.png', 'ACTIVE', 23, NOW(6), NOW(6)),
    ('1405', 'anaxa', '아낙사', 5, 'Mage', '지식', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1405.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1405.png', 'ACTIVE', 24, NOW(6), NOW(6)),
    ('1404', 'mydei', '마이데이', 5, 'Warrior', '파멸', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1404.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1404.png', 'ACTIVE', 25, NOW(6), NOW(6)),
    ('1403', 'tribbie', '트리비', 5, 'Shaman', '화합', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1403.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1403.png', 'ACTIVE', 26, NOW(6), NOW(6)),
    ('1402', 'aglaea', '아글라이아', 5, 'Memory', '기억', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1402.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1402.png', 'ACTIVE', 27, NOW(6), NOW(6)),
    ('1401', 'theherta', '더 헤르타', 5, 'Mage', '지식', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1401.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1401.png', 'ACTIVE', 28, NOW(6), NOW(6)),
    ('1321', 'dahlia', '달리아', 5, 'Warlock', '공허', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1321.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1321.png', 'ACTIVE', 29, NOW(6), NOW(6)),
    ('1317', 'rappa', '라파', 5, 'Mage', '지식', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1317.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1317.png', 'ACTIVE', 30, NOW(6), NOW(6)),
    ('1315', 'boothill', '부트힐', 5, 'Rogue', '수렵', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1315.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1315.png', 'ACTIVE', 31, NOW(6), NOW(6)),
    ('1314', 'jade', '제이드', 5, 'Mage', '지식', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1314.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1314.png', 'ACTIVE', 32, NOW(6), NOW(6)),
    ('1313', 'sunday', '선데이', 5, 'Shaman', '화합', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1313.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1313.png', 'ACTIVE', 33, NOW(6), NOW(6)),
    ('1312', 'misha', '미샤', 4, 'Warrior', '파멸', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1312.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1312.png', 'ACTIVE', 34, NOW(6), NOW(6)),
    ('1310', 'sam', '반디', 5, 'Warrior', '파멸', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1310.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1310.png', 'ACTIVE', 35, NOW(6), NOW(6)),
    ('1309', 'robin', '로빈', 5, 'Shaman', '화합', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1309.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1309.png', 'ACTIVE', 36, NOW(6), NOW(6)),
    ('1308', 'acheron', '아케론', 5, 'Warlock', '공허', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1308.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1308.png', 'ACTIVE', 37, NOW(6), NOW(6)),
    ('1307', 'blackswan', '블랙 스완', 5, 'Warlock', '공허', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1307.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1307.png', 'ACTIVE', 38, NOW(6), NOW(6)),
    ('1306', 'sparkle', '스파클', 5, 'Shaman', '화합', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1306.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1306.png', 'ACTIVE', 39, NOW(6), NOW(6)),
    ('1305', 'drratio', 'Dr. 레이시오', 5, 'Rogue', '수렵', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1305.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1305.png', 'ACTIVE', 40, NOW(6), NOW(6)),
    ('1304', 'aventurine', '어벤츄린', 5, 'Knight', '보존', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1304.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1304.png', 'ACTIVE', 41, NOW(6), NOW(6)),
    ('1303', 'ruanmei', '완•매', 5, 'Shaman', '화합', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1303.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1303.png', 'ACTIVE', 42, NOW(6), NOW(6)),
    ('1302', 'argenti', '아젠티', 5, 'Mage', '지식', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1302.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1302.png', 'ACTIVE', 43, NOW(6), NOW(6)),
    ('1301', 'gallagher', '갤러거', 4, 'Priest', '풍요', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1301.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1301.png', 'ACTIVE', 44, NOW(6), NOW(6)),
    ('1225', 'fugue', '망귀인', 5, 'Warlock', '공허', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1225.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1225.png', 'ACTIVE', 45, NOW(6), NOW(6)),
    ('1224', 'mar7th2', 'Mar. 7th', 4, 'Rogue', '수렵', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1224.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1224.png', 'ACTIVE', 46, NOW(6), NOW(6)),
    ('1223', 'moze', '맥택', 4, 'Rogue', '수렵', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1223.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1223.png', 'ACTIVE', 47, NOW(6), NOW(6)),
    ('1222', 'lingsha', '영사', 5, 'Priest', '풍요', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1222.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1222.png', 'ACTIVE', 48, NOW(6), NOW(6)),
    ('1221', 'yunli', '운리', 5, 'Warrior', '파멸', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1221.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1221.png', 'ACTIVE', 49, NOW(6), NOW(6)),
    ('1220', 'feixiao', '비소', 5, 'Rogue', '수렵', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1220.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1220.png', 'ACTIVE', 50, NOW(6), NOW(6)),
    ('1218', 'jiaoqiu', '초구', 5, 'Warlock', '공허', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1218.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1218.png', 'ACTIVE', 51, NOW(6), NOW(6)),
    ('1217', 'huohuo', '곽향', 5, 'Priest', '풍요', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1217.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1217.png', 'ACTIVE', 52, NOW(6), NOW(6)),
    ('1215', 'hanya', '한아', 4, 'Shaman', '화합', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1215.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1215.png', 'ACTIVE', 53, NOW(6), NOW(6)),
    ('1214', 'xueyi', '설의', 4, 'Warrior', '파멸', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1214.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1214.png', 'ACTIVE', 54, NOW(6), NOW(6)),
    ('1213', 'danhengil', '단항•음월', 5, 'Warrior', '파멸', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1213.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1213.png', 'ACTIVE', 55, NOW(6), NOW(6)),
    ('1212', 'jingliu', '경류', 5, 'Warrior', '파멸', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1212.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1212.png', 'ACTIVE', 56, NOW(6), NOW(6)),
    ('1211', 'bailu', '백로', 5, 'Priest', '풍요', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1211.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1211.png', 'ACTIVE', 57, NOW(6), NOW(6)),
    ('1210', 'guinaifen', '계네빈', 4, 'Warlock', '공허', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1210.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1210.png', 'ACTIVE', 58, NOW(6), NOW(6)),
    ('1209', 'yanqing', '연경', 5, 'Rogue', '수렵', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1209.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1209.png', 'ACTIVE', 59, NOW(6), NOW(6)),
    ('1208', 'fuxuan', '부현', 5, 'Knight', '보존', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1208.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1208.png', 'ACTIVE', 60, NOW(6), NOW(6)),
    ('1207', 'yukong', '어공', 4, 'Shaman', '화합', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1207.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1207.png', 'ACTIVE', 61, NOW(6), NOW(6)),
    ('1206', 'sushang', '소상', 4, 'Rogue', '수렵', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1206.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1206.png', 'ACTIVE', 62, NOW(6), NOW(6)),
    ('1205', 'blade', '블레이드', 5, 'Warrior', '파멸', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1205.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1205.png', 'ACTIVE', 63, NOW(6), NOW(6)),
    ('1204', 'jingyuan', '경원', 5, 'Mage', '지식', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1204.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1204.png', 'ACTIVE', 64, NOW(6), NOW(6)),
    ('1203', 'luocha', '나찰', 5, 'Priest', '풍요', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1203.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1203.png', 'ACTIVE', 65, NOW(6), NOW(6)),
    ('1202', 'tingyun', '정운', 4, 'Shaman', '화합', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1202.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1202.png', 'ACTIVE', 66, NOW(6), NOW(6)),
    ('1201', 'qingque', '청작', 4, 'Mage', '지식', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1201.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1201.png', 'ACTIVE', 67, NOW(6), NOW(6)),
    ('1112', 'topaz', '토파즈&복순이', 5, 'Rogue', '수렵', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1112.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1112.png', 'ACTIVE', 68, NOW(6), NOW(6)),
    ('1111', 'luka', '루카', 4, 'Warlock', '공허', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1111.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1111.png', 'ACTIVE', 69, NOW(6), NOW(6)),
    ('1110', 'lynx', '링스', 4, 'Priest', '풍요', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1110.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1110.png', 'ACTIVE', 70, NOW(6), NOW(6)),
    ('1109', 'hook', '후크', 4, 'Warrior', '파멸', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1109.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1109.png', 'ACTIVE', 71, NOW(6), NOW(6)),
    ('1108', 'sampo', '삼포', 4, 'Warlock', '공허', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1108.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1108.png', 'ACTIVE', 72, NOW(6), NOW(6)),
    ('1107', 'clara', '클라라', 5, 'Warrior', '파멸', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1107.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1107.png', 'ACTIVE', 73, NOW(6), NOW(6)),
    ('1106', 'pela', '페라', 4, 'Warlock', '공허', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1106.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1106.png', 'ACTIVE', 74, NOW(6), NOW(6)),
    ('1105', 'natasha', '나타샤', 4, 'Priest', '풍요', 'Physical', '물리', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1105.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1105.png', 'ACTIVE', 75, NOW(6), NOW(6)),
    ('1104', 'gepard', '게파드', 5, 'Knight', '보존', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1104.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1104.png', 'ACTIVE', 76, NOW(6), NOW(6)),
    ('1103', 'serval', '서벌', 4, 'Mage', '지식', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1103.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1103.png', 'ACTIVE', 77, NOW(6), NOW(6)),
    ('1102', 'seele', '제레', 5, 'Rogue', '수렵', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1102.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1102.png', 'ACTIVE', 78, NOW(6), NOW(6)),
    ('1101', 'bronya', '브로냐', 5, 'Shaman', '화합', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1101.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1101.png', 'ACTIVE', 79, NOW(6), NOW(6)),
    ('1015', 'archer', '아처', 5, 'Rogue', '수렵', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1015.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1015.png', 'ACTIVE', 80, NOW(6), NOW(6)),
    ('1014', 'saber', '세이버', 5, 'Warrior', '파멸', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1014.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1014.png', 'ACTIVE', 81, NOW(6), NOW(6)),
    ('1013', 'herta', '헤르타', 4, 'Mage', '지식', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1013.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1013.png', 'ACTIVE', 82, NOW(6), NOW(6)),
    ('1009', 'asta', '아스타', 4, 'Shaman', '화합', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1009.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1009.png', 'ACTIVE', 83, NOW(6), NOW(6)),
    ('1008', 'arlan', '아를란', 4, 'Warrior', '파멸', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1008.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1008.png', 'ACTIVE', 84, NOW(6), NOW(6)),
    ('1006', 'silverwolf', '은랑', 5, 'Warlock', '공허', 'Quantum', '양자', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1006.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1006.png', 'ACTIVE', 85, NOW(6), NOW(6)),
    ('1005', 'kafka', '카프카', 5, 'Warlock', '공허', 'Thunder', '번개', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1005.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1005.png', 'ACTIVE', 86, NOW(6), NOW(6)),
    ('1004', 'welt', '웰트', 5, 'Warlock', '공허', 'Imaginary', '허수', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1004.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1004.png', 'ACTIVE', 87, NOW(6), NOW(6)),
    ('1003', 'himeko', '히메코', 5, 'Mage', '지식', 'Fire', '화염', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1003.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1003.png', 'ACTIVE', 88, NOW(6), NOW(6)),
    ('1002', 'danheng', '단항', 4, 'Rogue', '수렵', 'Wind', '바람', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1002.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1002.png', 'ACTIVE', 89, NOW(6), NOW(6)),
    ('1001', 'mar7th', 'Mar. 7th', 4, 'Knight', '보존', 'Ice', '얼음', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/icon/character/1001.png', 'https://raw.githubusercontent.com/Mar-7th/StarRailRes/master/image/character_portrait/1001.png', 'ACTIVE', 90, NOW(6), NOW(6));

INSERT INTO character_external_alias (external_id, character_id)
SELECT '1001', id FROM game_character WHERE canonical_external_id = '1001'
UNION ALL
SELECT '1002', id FROM game_character WHERE canonical_external_id = '1002'
UNION ALL
SELECT '1003', id FROM game_character WHERE canonical_external_id = '1003'
UNION ALL
SELECT '1004', id FROM game_character WHERE canonical_external_id = '1004'
UNION ALL
SELECT '1005', id FROM game_character WHERE canonical_external_id = '1005'
UNION ALL
SELECT '1006', id FROM game_character WHERE canonical_external_id = '1006'
UNION ALL
SELECT '1008', id FROM game_character WHERE canonical_external_id = '1008'
UNION ALL
SELECT '1009', id FROM game_character WHERE canonical_external_id = '1009'
UNION ALL
SELECT '1013', id FROM game_character WHERE canonical_external_id = '1013'
UNION ALL
SELECT '1014', id FROM game_character WHERE canonical_external_id = '1014'
UNION ALL
SELECT '1015', id FROM game_character WHERE canonical_external_id = '1015'
UNION ALL
SELECT '1101', id FROM game_character WHERE canonical_external_id = '1101'
UNION ALL
SELECT '1102', id FROM game_character WHERE canonical_external_id = '1102'
UNION ALL
SELECT '1103', id FROM game_character WHERE canonical_external_id = '1103'
UNION ALL
SELECT '1104', id FROM game_character WHERE canonical_external_id = '1104'
UNION ALL
SELECT '1105', id FROM game_character WHERE canonical_external_id = '1105'
UNION ALL
SELECT '1106', id FROM game_character WHERE canonical_external_id = '1106'
UNION ALL
SELECT '1107', id FROM game_character WHERE canonical_external_id = '1107'
UNION ALL
SELECT '1108', id FROM game_character WHERE canonical_external_id = '1108'
UNION ALL
SELECT '1109', id FROM game_character WHERE canonical_external_id = '1109'
UNION ALL
SELECT '1110', id FROM game_character WHERE canonical_external_id = '1110'
UNION ALL
SELECT '1111', id FROM game_character WHERE canonical_external_id = '1111'
UNION ALL
SELECT '1112', id FROM game_character WHERE canonical_external_id = '1112'
UNION ALL
SELECT '1201', id FROM game_character WHERE canonical_external_id = '1201'
UNION ALL
SELECT '1202', id FROM game_character WHERE canonical_external_id = '1202'
UNION ALL
SELECT '1203', id FROM game_character WHERE canonical_external_id = '1203'
UNION ALL
SELECT '1204', id FROM game_character WHERE canonical_external_id = '1204'
UNION ALL
SELECT '1205', id FROM game_character WHERE canonical_external_id = '1205'
UNION ALL
SELECT '1206', id FROM game_character WHERE canonical_external_id = '1206'
UNION ALL
SELECT '1207', id FROM game_character WHERE canonical_external_id = '1207'
UNION ALL
SELECT '1208', id FROM game_character WHERE canonical_external_id = '1208'
UNION ALL
SELECT '1209', id FROM game_character WHERE canonical_external_id = '1209'
UNION ALL
SELECT '1210', id FROM game_character WHERE canonical_external_id = '1210'
UNION ALL
SELECT '1211', id FROM game_character WHERE canonical_external_id = '1211'
UNION ALL
SELECT '1212', id FROM game_character WHERE canonical_external_id = '1212'
UNION ALL
SELECT '1213', id FROM game_character WHERE canonical_external_id = '1213'
UNION ALL
SELECT '1214', id FROM game_character WHERE canonical_external_id = '1214'
UNION ALL
SELECT '1215', id FROM game_character WHERE canonical_external_id = '1215'
UNION ALL
SELECT '1217', id FROM game_character WHERE canonical_external_id = '1217'
UNION ALL
SELECT '1218', id FROM game_character WHERE canonical_external_id = '1218'
UNION ALL
SELECT '1220', id FROM game_character WHERE canonical_external_id = '1220'
UNION ALL
SELECT '1221', id FROM game_character WHERE canonical_external_id = '1221'
UNION ALL
SELECT '1222', id FROM game_character WHERE canonical_external_id = '1222'
UNION ALL
SELECT '1223', id FROM game_character WHERE canonical_external_id = '1223'
UNION ALL
SELECT '1224', id FROM game_character WHERE canonical_external_id = '1224'
UNION ALL
SELECT '1225', id FROM game_character WHERE canonical_external_id = '1225'
UNION ALL
SELECT '1301', id FROM game_character WHERE canonical_external_id = '1301'
UNION ALL
SELECT '1302', id FROM game_character WHERE canonical_external_id = '1302'
UNION ALL
SELECT '1303', id FROM game_character WHERE canonical_external_id = '1303'
UNION ALL
SELECT '1304', id FROM game_character WHERE canonical_external_id = '1304'
UNION ALL
SELECT '1305', id FROM game_character WHERE canonical_external_id = '1305'
UNION ALL
SELECT '1306', id FROM game_character WHERE canonical_external_id = '1306'
UNION ALL
SELECT '1307', id FROM game_character WHERE canonical_external_id = '1307'
UNION ALL
SELECT '1308', id FROM game_character WHERE canonical_external_id = '1308'
UNION ALL
SELECT '1309', id FROM game_character WHERE canonical_external_id = '1309'
UNION ALL
SELECT '1310', id FROM game_character WHERE canonical_external_id = '1310'
UNION ALL
SELECT '1312', id FROM game_character WHERE canonical_external_id = '1312'
UNION ALL
SELECT '1313', id FROM game_character WHERE canonical_external_id = '1313'
UNION ALL
SELECT '1314', id FROM game_character WHERE canonical_external_id = '1314'
UNION ALL
SELECT '1315', id FROM game_character WHERE canonical_external_id = '1315'
UNION ALL
SELECT '1317', id FROM game_character WHERE canonical_external_id = '1317'
UNION ALL
SELECT '1321', id FROM game_character WHERE canonical_external_id = '1321'
UNION ALL
SELECT '1401', id FROM game_character WHERE canonical_external_id = '1401'
UNION ALL
SELECT '1402', id FROM game_character WHERE canonical_external_id = '1402'
UNION ALL
SELECT '1403', id FROM game_character WHERE canonical_external_id = '1403'
UNION ALL
SELECT '1404', id FROM game_character WHERE canonical_external_id = '1404'
UNION ALL
SELECT '1405', id FROM game_character WHERE canonical_external_id = '1405'
UNION ALL
SELECT '1406', id FROM game_character WHERE canonical_external_id = '1406'
UNION ALL
SELECT '1407', id FROM game_character WHERE canonical_external_id = '1407'
UNION ALL
SELECT '1408', id FROM game_character WHERE canonical_external_id = '1408'
UNION ALL
SELECT '1409', id FROM game_character WHERE canonical_external_id = '1409'
UNION ALL
SELECT '1410', id FROM game_character WHERE canonical_external_id = '1410'
UNION ALL
SELECT '1412', id FROM game_character WHERE canonical_external_id = '1412'
UNION ALL
SELECT '1413', id FROM game_character WHERE canonical_external_id = '1413'
UNION ALL
SELECT '1414', id FROM game_character WHERE canonical_external_id = '1414'
UNION ALL
SELECT '1415', id FROM game_character WHERE canonical_external_id = '1415'
UNION ALL
SELECT '1501', id FROM game_character WHERE canonical_external_id = '1501'
UNION ALL
SELECT '1502', id FROM game_character WHERE canonical_external_id = '1502'
UNION ALL
SELECT '1504', id FROM game_character WHERE canonical_external_id = '1504'
UNION ALL
SELECT '1505', id FROM game_character WHERE canonical_external_id = '1505'
UNION ALL
SELECT '1506', id FROM game_character WHERE canonical_external_id = '1506'
UNION ALL
SELECT '1507', id FROM game_character WHERE canonical_external_id = '1507'
UNION ALL
SELECT '1508', id FROM game_character WHERE canonical_external_id = '1508'
UNION ALL
SELECT '1509', id FROM game_character WHERE canonical_external_id = '1509'
UNION ALL
SELECT '1510', id FROM game_character WHERE canonical_external_id = '1510'
UNION ALL
SELECT '8001', id FROM game_character WHERE canonical_external_id = '8001'
UNION ALL
SELECT '8002', id FROM game_character WHERE canonical_external_id = '8001'
UNION ALL
SELECT '8003', id FROM game_character WHERE canonical_external_id = '8003'
UNION ALL
SELECT '8004', id FROM game_character WHERE canonical_external_id = '8003'
UNION ALL
SELECT '8005', id FROM game_character WHERE canonical_external_id = '8005'
UNION ALL
SELECT '8006', id FROM game_character WHERE canonical_external_id = '8005'
UNION ALL
SELECT '8007', id FROM game_character WHERE canonical_external_id = '8007'
UNION ALL
SELECT '8008', id FROM game_character WHERE canonical_external_id = '8007'
UNION ALL
SELECT '8009', id FROM game_character WHERE canonical_external_id = '8009'
UNION ALL
SELECT '8010', id FROM game_character WHERE canonical_external_id = '8009';

INSERT INTO character_evaluation
(character_id, game_version, status, opened_at, closed_at, created_at, updated_at)
SELECT id, '4.4', 'OPEN', NOW(6), NULL, NOW(6), NOW(6)
FROM game_character;

UPDATE game_character
SET display_order = display_order + 1000
WHERE canonical_external_id LIKE '8%';

INSERT INTO poll
(evaluation_id, type, title, status, created_at, updated_at)
SELECT id, 'TIER', '현재 버전 종합 티어', 'OPEN', NOW(6), NOW(6)
FROM character_evaluation;

INSERT INTO poll_option
(poll_id, code, label, description, score, display_order, created_at, updated_at)
SELECT p.id, options.code, options.label, options.description, options.score, options.display_order, NOW(6), NOW(6)
FROM poll p
JOIN (
    SELECT 'T0' AS code, 'T0' AS label, '현재 메타의 중심' AS description, 5 AS score, 1 AS display_order
    UNION ALL SELECT 'T05', 'T0.5', '대부분의 콘텐츠에서 강력함', 4, 2
    UNION ALL SELECT 'T1', 'T1', '조건이 맞으면 충분히 우수함', 3, 3
    UNION ALL SELECT 'T15', 'T1.5', '높은 투자나 특정 조합이 필요함', 2, 4
    UNION ALL SELECT 'T2', 'T2', '현재 기준 육성 효율이 낮음', 1, 5
) AS options
WHERE p.type = 'TIER';
