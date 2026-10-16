-- 발표용 측정 쿼리 (PostgreSQL). 지시서 8단계 "발표용 측정 항목"
-- 비용 = input_tokens × 입력 단가 + output_tokens × 출력 단가 (단가는 사용 모델의 공식 가격표 값)

-- 1. 지원자 1명당 AI 호출 수, 토큰, 소요 시간 (호출 ②, ③)
SELECT c.id, c.display_name,
       COUNT(l.id)                                       AS calls,
       SUM(CASE WHEN l.success_yn = 'N' THEN 1 ELSE 0 END) AS failed_calls,
       SUM(l.input_tokens)                               AS input_tokens,
       SUM(l.output_tokens)                              AS output_tokens,
       ROUND(SUM(l.latency_ms) / 1000.0, 1)              AS latency_sec
FROM candidate c
JOIN ai_call_log l ON l.ref_id = c.id AND l.purpose IN ('EVIDENCE', 'QUESTION', 'ARRANGE')
GROUP BY c.id, c.display_name
ORDER BY c.id;

-- 2. 호출 종류별 평균 (포지션당 1회 + 지원자당 2회 기준 비용 추정)
SELECT purpose, model,
       COUNT(*)                     AS calls,
       ROUND(AVG(input_tokens))     AS avg_input_tokens,
       ROUND(AVG(output_tokens))    AS avg_output_tokens,
       ROUND(AVG(latency_ms))       AS avg_latency_ms
FROM ai_call_log
WHERE success_yn = 'Y'
GROUP BY purpose, model
ORDER BY purpose;

-- 3. 근거 지어냄 비율 (현재 저장된 최신 분석 기준, 인용이 있는 근거만)
SELECT COUNT(*)                                                        AS quoted,
       SUM(CASE WHEN verified_yn = 'N' THEN 1 ELSE 0 END)              AS unverified,
       ROUND(100.0 * SUM(CASE WHEN verified_yn = 'N' THEN 1 ELSE 0 END) / NULLIF(COUNT(*), 0), 1) AS unverified_pct
FROM evidence
WHERE quote IS NOT NULL;

-- 4. 결과 일관성: 같은 이력서를 3회 분석할 때, 분석마다 아래 결과를 저장해 두고 비교한다
--    (재분석하면 evidence가 교체되므로 회차마다 실행해 결과를 기록)
SELECT e.criterion_id, cr.name,
       STRING_AGG(e.evidence_type, ',' ORDER BY e.evidence_type) AS evidence_types
FROM evidence e
JOIN eval_criterion cr ON cr.id = e.criterion_id
WHERE e.candidate_id = :candidate_id
GROUP BY e.criterion_id, cr.name
ORDER BY e.criterion_id;
