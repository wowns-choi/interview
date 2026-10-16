-- 1. 기본 섹션 (처음 한 번만)
INSERT INTO question_section (name, sort_order)
SELECT v.name, v.sort_order
FROM (VALUES
    ('오프닝', 1), ('경험', 2), ('태도', 3), ('협업', 4), ('프로젝트', 5),
    ('기술 검증', 6), ('AI 활용', 7), ('클로징', 8), ('필수 확인', 9), ('마무리', 10)
) AS v(name, sort_order)
WHERE NOT EXISTS (SELECT 1 FROM question_section)
  AND NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'seeded');

-- 2. 예전 버전 데이터 이관: section 코드 → 섹션 id
UPDATE question_template
SET section_id = (
    SELECT s.id FROM question_section s
    WHERE s.name = CASE question_template.section
        WHEN 'OPENING' THEN '오프닝' WHEN 'EXPERIENCE' THEN '경험' WHEN 'ATTITUDE' THEN '태도'
        WHEN 'COLLABORATION' THEN '협업' WHEN 'PROJECT' THEN '프로젝트' WHEN 'TECH' THEN '기술 검증'
        WHEN 'AI' THEN 'AI 활용' WHEN 'CLOSING' THEN '클로징' WHEN 'REQUIRED' THEN '필수 확인'
        WHEN 'FINAL' THEN '마무리' END
    ORDER BY s.id FETCH FIRST 1 ROWS ONLY)
WHERE section_id IS NULL AND section IS NOT NULL;

UPDATE interview_question
SET section = CASE section
    WHEN 'OPENING' THEN '오프닝' WHEN 'EXPERIENCE' THEN '경험' WHEN 'ATTITUDE' THEN '태도'
    WHEN 'COLLABORATION' THEN '협업' WHEN 'PROJECT' THEN '프로젝트' WHEN 'TECH' THEN '기술 검증'
    WHEN 'AI' THEN 'AI 활용' WHEN 'CLOSING' THEN '클로징' WHEN 'REQUIRED' THEN '필수 확인'
    WHEN 'FINAL' THEN '마무리' END
WHERE section IN ('OPENING', 'EXPERIENCE', 'ATTITUDE', 'COLLABORATION', 'PROJECT', 'TECH', 'AI', 'CLOSING', 'REQUIRED', 'FINAL');

-- 3. 기본 공통 질문 (처음 한 번만)
INSERT INTO question_template (section_id, content, sort_order, active_yn)
SELECT s.id, v.content, v.sort_order, 'Y'
FROM (VALUES
    ('오프닝',    '[출퇴근] 평소 출퇴근은 어느 정도 거리까지 괜찮으신 편인가요? 여기까지는 얼마나?', 1),
    ('오프닝',    '자기소개 준비한 거 있으면', 2),
    ('경험',      'PM이나 PL 역할을 맡아본 경험이 있나요? 공식적인 직책이 아니어도 팀 일정이나 업무 분배를 담당했던 경험이 있으면 말씀해주세요.', 1),
    ('경험',      '서버나 인프라 구성·설계를 직접 해본 경험이 있나요? 있다면 어떤 환경이었고, 본인이 어디까지 담당했는지 설명해주세요.', 2),
    ('태도',      '개발 이후 운영이나 유지보수를 직접 맡은 경험이 있나요? 기간과 담당 범위는요?', 1),
    ('태도',      '현업이나 고객의 문의, 불만을 직접 받아본 적이 있나요? 기억에 남는 사례는요?', 2),
    ('협업',      '코드 리뷰를 해본/받아본 경험이 있나요?', 1),
    ('협업',      '같이 일하기 어려웠던 동료가 있었나요? 어떤 점이 어려웠고 어떻게 일했나요?', 2),
    ('프로젝트',  '기존 코드에서 문제가 보이는데 당장 요구사항을 처리하는 데는 문제가 없다면 어떻게 하시겠어요?', 1),
    ('프로젝트',  '기존 프로젝트에 새로운 기능을 추가할 때, 기존 코드 스타일과 본인이 선호하는 방식이 다르면 어떤 기준으로 코드를 작성하시나요?', 2),
    ('AI 활용',   '이전 회사에서 AI 지원해줬는지, 개발 업무에서 어떤 AI 도구를 사용해봤나요? 사용해보셨으면 어떤 상황에서 어떻게 활용했는지 설명해주세요.', 1),
    ('클로징',    '[파견] 프로젝트에 따라 근무지가 변경되거나 고객사에서 일정 기간 상주해야 하는 경우가 있는데 괜찮으신가요? 지역은 어디까지?', 1),
    ('클로징',    '사내에서 SM 업무를 하게 되실 가능성이 큰데 괜찮으신지', 2),
    ('클로징',    '현재 회사를 이직하려는 이유가 무엇인가요?', 3),
    ('클로징',    '회사에서 중요하게 보는 조건은 무엇인가요?', 4),
    ('클로징',    '이직하려는 회사의 걱정되는 부분', 5),
    ('필수 확인', '출근은 언제부터 가능하신가요?', 1),
    ('필수 확인', '스펙터 작성 가능하신가요? (평판조회용 툴)', 2),
    ('마무리',    '저희 회사에 대해 궁금하신 점 있으세요?', 1),
    ('마무리',    '희망연봉은 어느 정도로 생각하고 계신가요?', 2),
    ('마무리',    '현재 받고 계신 연봉 말씀해주실 수 있으실까요?', 3),
    ('마무리',    '불합격/합격 통보는 차주 화요일까지. 1차면접 합격 후 스펙터 요청. 그 후 판단에 따라 2차 면접 진행', 4),
    ('마무리',    '수습 2개월은 계약직으로 입사하게 됨', 5),
    ('마무리',    '경력직도 수습 기간 동안 과제를 하고 평가를 받고, 팀원 앞에서 발표를 할 수도 있음', 6)
) AS v(section_name, content, sort_order)
JOIN question_section s ON s.name = v.section_name
WHERE NOT EXISTS (SELECT 1 FROM question_template)
  AND NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'seeded');

INSERT INTO app_setting (setting_key, setting_value)
SELECT 'seeded', 'Y' WHERE NOT EXISTS (SELECT 1 FROM app_setting WHERE setting_key = 'seeded');
