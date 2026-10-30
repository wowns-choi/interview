<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Interviewer"/>
<c:set var="activeTab" value="about"/>
<%@ include file="common/header.jspf" %>
<%-- 소개 화면 (사용자 2026-10-28): 면접 흐름 중 AI가 덜어 주는 네 단계만 보인다 --%>

<div class="ab">
  <section class="ab-hero">
    <div class="ab-brand">Interviewer</div>
    <p class="ab-lead">면접관을 위한 AI</p>
  </section>

  <ol class="ab-flow">
    <li><b>채용 공고</b></li>
    <li class="is-ai"><b>평가 기준 추출</b><span>공고에서 볼 것을 뽑는다</span></li>
    <li><b>지원자 접수</b></li>
    <li class="is-ai"><b>적합도 검사</b><span>이력서가 기준을 채우는지</span></li>
    <li class="is-ai"><b>면접 질문 준비</b><span>이력서에 맞춘 질문</span></li>
    <li><b>면접</b></li>
    <li class="is-ai"><b>보고서 작성</b><span>면접 기록으로 채운 보고서</span></li>
    <li class="is-human"><b>합격 · 불합격</b></li>
  </ol>

  <section class="ab-end">
    <a class="btn btn-primary ab-go" href="<c:url value='/positions'/>">시작하기<svg class="i"><use href="#i-arrow-right"/></svg></a>
  </section>
</div>

<%@ include file="common/footer.jspf" %>
