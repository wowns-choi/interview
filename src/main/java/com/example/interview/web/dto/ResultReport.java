package com.example.interview.web.dto;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.example.interview.ai.dto.CareerEvalResult;
import com.example.interview.ai.dto.OpinionResult;
import com.example.interview.domain.Candidate;
import com.example.interview.domain.CandidateProfile;
import com.example.interview.domain.Evidence;
import com.example.interview.domain.EvidenceType;
import com.example.interview.domain.Position;

import lombok.Value;

/** 결과 화면과 PDF가 함께 쓰는 추출 결과 전체 */
@Value
public class ResultReport {
    Position position;
    Candidate candidate;
    /** 인적사항·학력·경력 요약. 업로드 때 못 찾았으면 null */
    CandidateProfile profile;
    /** 외부 AI가 계산한 총 경력·관련 경력·최종 학력. 업로드 때 계산하고 적합도 검사 때 관련 경력까지 다시 계산한다. 실패하면 null */
    CareerEvalResult careerEval;
    List<CriterionEvidenceView> criteria;
    /** 공통 질문과 합친 질문지 */
    List<SectionView> sections;
    /** 추출만 해 두고 아직 질문지에 넣지 않은 질문. 유형별로 묶는다 */
    List<SectionView> extracted;
    /** AI 의견 참고. 아직 묻지 않았으면 null */
    OpinionResult opinion;
    Map<Long, Evidence> evidenceById;
    AiUsageSummary usage;

    /** 경력 막대. 공고 경력이나 지원자 경력을 숫자로 읽을 수 없으면 null */
    public CareerGauge getCareerGauge() {
        return CareerGauge.of(profile, position.getRequiredExperience(), careerEval);
    }

    /** 이력 타임라인. 기간을 읽기 전이면 null */
    public TimelineView getTimeline() {
        return TimelineView.of(profile, careerEval, YearMonth.now());
    }

    /** 지원자 비교 점수. 적합도 검사 전이면 null */
    public FitScore getScore() {
        return isFitChecked() ? FitScore.of(position, careerEval, getCareerGauge(), criteria) : null;
    }

    /** 경력·학력 충족 도장 */
    public RequirementFit getFit() {
        return RequirementFit.of(position, careerEval, getCareerGauge());
    }

    public long getDirectCount() {
        return countMatch(EvidenceType.DIRECT);
    }

    public long getIndirectCount() {
        return countMatch(EvidenceType.INDIRECT);
    }

    public long getNoneCount() {
        return countMatch(EvidenceType.NONE);
    }

    public boolean isFitChecked() {
        return criteria.stream().anyMatch(CriterionEvidenceView::isChecked);
    }

    /** "해본 적 있어?"로 물을 평가항목(직접 근거가 없는 것)이 있는지 */
    public boolean isExperienceAskable() {
        return criteria.stream().anyMatch(c -> c.getMatch() != EvidenceType.DIRECT);
    }

    public boolean isHasUncheckedCriteria() {
        return criteria.stream().anyMatch(c -> !c.isChecked());
    }

    public long getExtractedCount() {
        return extracted.stream().mapToLong(s -> s.getQuestions().size()).sum();
    }

    public long getQuestionCount() {
        return sections.stream().mapToLong(s -> s.getQuestions().size()).sum();
    }

    public long getAnsweredCount() {
        return sections.stream().flatMap(s -> s.getQuestions().stream())
                .filter(q -> q.getQuestion().getAnswer() != null && !q.getQuestion().getAnswer().isBlank()).count();
    }

    public long getUnverifiedCount() {
        return evidenceById.values().stream()
                .filter(e -> e.getQuote() != null && !"Y".equals(e.getVerifiedYn())).count();
    }

    private long countMatch(EvidenceType type) {
        return criteria.stream().filter(c -> c.getMatch() == type).count();
    }
}
