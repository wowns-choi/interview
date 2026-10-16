package com.example.interview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.example.interview.domain.QuestionSection;
import com.example.interview.domain.QuestionTemplate;
import com.example.interview.service.BusinessException;
import com.example.interview.service.TemplateService;
import com.example.interview.service.TemplateService.SectionOrder;
import com.example.interview.web.dto.SectionBoard;

@SpringBootTest
@Transactional
class InterviewApplicationTests {

    @Autowired
    TemplateService templateService;

    @Test
    void seedSectionsAndQuestionsAreLoaded() {
        List<SectionBoard> board = templateService.board();
        assertThat(board).extracting(b -> b.getSection().getName()).containsExactly(
                "오프닝", "경험", "태도", "협업", "프로젝트", "기술 검증", "AI 활용", "클로징", "필수 확인", "마무리");
        assertThat(board.stream().mapToInt(b -> b.getQuestions().size()).sum()).isEqualTo(24);
        assertThat(board.get(0).getQuestions().get(0).getContent()).startsWith("[출퇴근]");
        assertThat(board.get(5).getQuestions()).isEmpty();
    }

    @Test
    void sectionsAndQuestionsCanBeEditedAndReordered() {
        QuestionSection added = templateService.createSection("  컬처핏  ");
        assertThat(added.getName()).isEqualTo("컬처핏");
        QuestionTemplate q = templateService.createQuestion(added.getId(), "가장 몰입했던 경험은?");
        templateService.renameSection(added.getId(), "컬처 핏");
        templateService.updateQuestion(q.getId(), "가장 몰입했던 순간은?");

        List<SectionBoard> board = templateService.board();
        SectionBoard last = board.get(board.size() - 1);
        assertThat(last.getSection().getName()).isEqualTo("컬처 핏");
        assertThat(last.getQuestions()).extracting(QuestionTemplate::getContent).containsExactly("가장 몰입했던 순간은?");

        // 새 섹션을 맨 앞으로, 오프닝 첫 질문을 새 섹션 끝으로 옮긴다
        List<SectionOrder> order = new ArrayList<>();
        Long movedId = board.get(0).getQuestions().get(0).getId();
        List<Long> lastIds = new ArrayList<>(List.of(q.getId(), movedId));
        order.add(new SectionOrder(added.getId(), lastIds));
        for (SectionBoard b : board.subList(0, board.size() - 1)) {
            order.add(new SectionOrder(b.getSection().getId(),
                    b.getQuestions().stream().map(QuestionTemplate::getId).filter(id -> !id.equals(movedId)).toList()));
        }
        templateService.reorder(order);

        List<SectionBoard> after = templateService.board();
        assertThat(after.get(0).getSection().getName()).isEqualTo("컬처 핏");
        assertThat(after.get(0).getQuestions()).extracting(QuestionTemplate::getId).containsExactly(q.getId(), movedId);
        assertThat(after.get(1).getSection().getName()).isEqualTo("오프닝");
        assertThat(after.get(1).getQuestions()).hasSize(1);

        // 화면이 오래된 경우(질문 하나가 빠진 순서)는 거부한다
        List<SectionOrder> stale = new ArrayList<>(order);
        stale.set(0, new SectionOrder(added.getId(), List.of(q.getId())));
        assertThatThrownBy(() -> templateService.reorder(stale)).isInstanceOf(BusinessException.class);

        // 섹션을 지우면 질문도 함께 지워진다
        templateService.deleteSection(added.getId());
        assertThat(templateService.board().stream().mapToInt(b -> b.getQuestions().size()).sum()).isEqualTo(23);

        assertThatThrownBy(() -> templateService.createSection(" ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> templateService.updateQuestion(q.getId(), "x")).hasMessageContaining("찾을 수 없습니다");
    }
}
