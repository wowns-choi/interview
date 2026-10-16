package com.example.interview.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.interview.ai.AiClient;
import com.example.interview.ai.PromptLoader;
import com.example.interview.ai.dto.EvidenceResult;
import com.example.interview.domain.AiPurpose;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.EvalCriterion;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.resume.QuoteVerifier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/** AI 호출 ② 근거 분석과 인용 검증 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvidenceService {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final AiClient aiClient;
    private final PromptLoader promptLoader;
    private final QuoteVerifier quoteVerifier;

    /**
     * 근거를 분석하고 인용을 검증한다. 반환하는 Evidence는 아직 저장 전이라 id가 없다.
     * 검증에 실패한 근거도 버리지 않고 verified_yn=N으로 반환한다.
     */
    public List<Evidence> analyze(Candidate candidate, List<EvalCriterion> criteria) {
        Set<Long> criterionIds = criteria.stream().map(EvalCriterion::getId).collect(Collectors.toSet());
        String userContent = "<criteria>\n" + criteriaJson(criteria) + "\n</criteria>\n\n"
                + "<resume>\n" + candidate.getMaskedText() + "\n</resume>";

        EvidenceResult result = aiClient.call(AiPurpose.EVIDENCE, candidate.getId(),
                promptLoader.load("evidence"), userContent, EvidenceResult.class,
                r -> r.validate(criterionIds));

        List<Evidence> evidences = new ArrayList<>();
        int quoted = 0;
        int failed = 0;
        for (EvidenceResult.CriterionResult r : result.getResults()) {
            for (EvidenceResult.Item item : r.getEvidences()) {
                Evidence e = new Evidence();
                e.setCandidateId(candidate.getId());
                e.setCriterionId(r.getCriterionId());
                e.setEvidenceType(EvidenceType.valueOf(item.getType()));
                e.setInterpretation(item.getInterpretation());
                e.setInterviewPoint(r.getInterviewPoint());
                if (e.getEvidenceType() == EvidenceType.NONE) {
                    e.setQuote(null);
                    e.setVerifiedYn("Y");
                } else {
                    e.setQuote(item.getQuote());
                    boolean verified = quoteVerifier.verify(item.getQuote(), candidate.getMaskedText());
                    e.setVerifiedYn(verified ? "Y" : "N");
                    quoted++;
                    if (!verified) {
                        failed++;
                    }
                }
                evidences.add(e);
            }
        }
        // 발표 자료: "AI가 근거를 지어낸 비율"
        log.info("인용 검증 결과 candidateId={} 인용={} 검증실패={} 실패비율={}",
                candidate.getId(), quoted, failed,
                quoted == 0 ? "0%" : String.format("%.1f%%", failed * 100.0 / quoted));
        return evidences;
    }

    private String criteriaJson(List<EvalCriterion> criteria) {
        List<Object> list = criteria.stream()
                .map(c -> (Object) new CriterionInput(c.getId(), c.getName(), c.getDescription(),
                        c.getRequirement().name(), c.getCategory().name(),
                        "Y".equals(c.getResumeAssessableYn())))
                .toList();
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(list);
    }

    record CriterionInput(Long criterion_id, String name, String description, String requirement,
            String category, boolean resume_assessable) {
    }
}
