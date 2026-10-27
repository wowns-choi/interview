<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="면접결과보고서_${candidate.displayName}"/>
<c:set var="activeTab" value="fit"/>
<%@ include file="common/header.jspf" %>

<%-- 화면에서만 보이는 도구 줄. 영역별 버튼은 각 영역 제목 옆에 둔다 --%>
<div class="rp-bar">
  <a class="rp-back" href="<c:url value='/results/${candidate.id}'/>"><svg class="i"><use href="#i-arrow-right"/></svg><c:out value="${candidate.displayName}"/></a>
  <span class="save-state" id="rp-state" aria-live="polite"></span>
  <button type="button" class="btn btn-primary btn-sm" id="rp-pdf"><svg class="i"><use href="#i-download"/></svg>PDF 저장</button>
</div>

<article class="rp-paper" id="rp" data-url="<c:url value='/results/${candidate.id}/report'/>" data-source="${report.qaSource}">
  <header class="rp-head">
    <h1 class="rp-title">면접 결과 보고서</h1>
  </header>

  <table class="rp-meta">
    <tr>
      <th>보고서 작성일</th><td><input type="date" data-f="reportDate" value="<c:out value='${report.reportDate}'/>"></td>
      <th>면접일</th><td><input type="date" data-f="interviewDate" value="<c:out value='${report.interviewDate}'/>"></td>
    </tr>
    <tr>
      <th>지원 포지션</th><td colspan="3"><input type="text" data-f="position" value="<c:out value='${report.position == null ? position.title : report.position}'/>"></td>
    </tr>
  </table>

  <section class="rp-sec">
    <div class="rp-h">
      <h2><span class="rp-h-no">1</span>지원자 정보</h2>    </div>
    <div class="rp-info" data-list="info">
      <c:forEach var="row" items="${report.info}">
        <div class="rp-cell rp-item">
          <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
          <input class="rp-label" data-k="label" value="<c:out value='${row.label}'/>" placeholder="항목">
          <textarea class="rp-value" data-k="value" rows="1"><c:out value="${row.value}"/></textarea>
          <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
        </div>
      </c:forEach>
    </div>
    <button type="button" class="rp-add" data-add="info"><svg class="i"><use href="#i-plus"/></svg>항목 추가</button>
  </section>

  <section class="rp-sec">
    <div class="rp-h">
      <h2><span class="rp-h-no">2</span>질문과 답변</h2>
      <div class="rp-tools">
        <form class="rp-seg" method="post" data-partial="#rp" action="<c:url value='/results/${candidate.id}/report/source'/>"
              data-confirm="질문과 답변을 다시 가져옵니다. 보고서에서 고친 질문과 답변은 사라집니다.">
          <button type="${report.qaSource == 'SHEET' ? 'button' : 'submit'}" name="source" value="SHEET" aria-pressed="${report.qaSource == 'SHEET'}">면접전(타이핑)</button>
          <button type="${report.qaSource == 'RECORD' ? 'button' : 'submit'}" name="source" value="RECORD" aria-pressed="${report.qaSource == 'RECORD'}" ${hasRecord ? '' : 'disabled title="면접 기록이 없습니다"'}>면접후(녹음본)</button>
        </form>
      </div>
    </div>
    <c:forEach var="g" begin="0" end="1">
      <c:set var="items" value="${g == 0 ? report.interviewerQa : report.candidateQa}"/>
      <div class="rp-qa-group${empty items ? ' is-empty' : ''}">
        <h3 class="rp-sub">${g == 0 ? '면접관 질문' : '지원자 질문'}</h3>
        <ol class="rp-qa" data-list="qa" data-kind="${g == 0 ? 'INTERVIEWER' : 'CANDIDATE'}">
          <c:forEach var="it" items="${items}">
            <li class="rp-q-item rp-item" data-excerpt="<c:out value='${it.excerpt}'/>">
              <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
              <div class="rp-q"><span class="rp-qno"></span><textarea data-k="question" rows="1" placeholder="질문"><c:out value="${it.question}"/></textarea></div>
              <div class="rp-a"><span class="rp-ano">A</span><textarea data-k="answer" rows="1" placeholder="답변"><c:out value="${it.answer}"/></textarea></div>
              <c:if test="${not empty it.excerpt}"><p class="rp-excerpt"><span>녹취</span><c:out value="${it.excerpt}"/></p></c:if>
              <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
            </li>
          </c:forEach>
        </ol>
        <button type="button" class="rp-add" data-add="qa" data-kind="${g == 0 ? 'INTERVIEWER' : 'CANDIDATE'}"><svg class="i"><use href="#i-plus"/></svg>질문 추가</button>
      </div>
    </c:forEach>
  </section>

  <section class="rp-sec">
    <div class="rp-h"><h2><span class="rp-h-no">3</span>면접관 의견</h2></div>
    <div class="rp-ops" data-list="opinions">
      <c:forEach var="op" items="${report.opinions}">
        <div class="rp-op rp-item">
          <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
          <div class="rp-op-who">
            <label><span>팀</span><input data-k="team" value="<c:out value='${op.team}'/>"></label>
            <label><span>성명</span><input data-k="name" value="<c:out value='${op.name}'/>"></label>
          </div>
          <textarea data-k="content" rows="3" placeholder="의견"><c:out value="${op.content}"/></textarea>
          <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
        </div>
      </c:forEach>
    </div>
    <button type="button" class="rp-add" data-add="opinions"><svg class="i"><use href="#i-plus"/></svg>면접관 추가</button>
  </section>

  <section class="rp-sec">
    <div class="rp-h"><h2><span class="rp-h-no">4</span>합격 여부</h2></div>
    <%-- 지원자의 합격 여부를 그대로 고친다 (적합도 검사 화면의 결과와 같은 값) --%>
    <div class="rp-result" id="rp-result" data-url="<c:url value='/results/${candidate.id}/interview'/>"
         data-interviewed="${candidate.interviewed}" data-date="${candidate.interviewDate}">
      <button type="button" class="rp-box${candidate.decision == 'PASS' ? ' is-on' : ''}" data-decision="PASS" aria-pressed="${candidate.decision == 'PASS'}">합격</button>
      <button type="button" class="rp-box${candidate.decision == 'FAIL' ? ' is-on' : ''}" data-decision="FAIL" aria-pressed="${candidate.decision == 'FAIL'}">불합격</button>
    </div>
  </section>
</article>

<template id="tpl-info">
  <div class="rp-cell rp-item">
    <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
    <input class="rp-label" data-k="label" placeholder="항목">
    <textarea class="rp-value" data-k="value" rows="1"></textarea>
    <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
  </div>
</template>
<template id="tpl-qa">
  <li class="rp-q-item rp-item" data-excerpt="">
    <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
    <div class="rp-q"><span class="rp-qno"></span><textarea data-k="question" rows="1" placeholder="질문"></textarea></div>
    <div class="rp-a"><span class="rp-ano">A</span><textarea data-k="answer" rows="1" placeholder="답변"></textarea></div>
    <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
  </li>
</template>
<template id="tpl-opinions">
  <div class="rp-op rp-item">
    <span class="rp-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
    <div class="rp-op-who">
      <label><span>팀</span><input data-k="team"></label>
      <label><span>성명</span><input data-k="name"></label>
    </div>
    <textarea data-k="content" rows="3" placeholder="의견"></textarea>
    <button type="button" class="rp-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
  </div>
</template>

<script src="<c:url value='/js/report.js'/>?v=${applicationScope.assetVersion}"></script>
<%@ include file="common/footer.jspf" %>
