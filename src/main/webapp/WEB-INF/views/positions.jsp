<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty position ? '평가 기준 추출' : position.title}"/>
<c:set var="activeTab" value="positions"/>
<%@ include file="common/header.jspf" %>

<div class="split pos-split" id="ws"
     data-position-base="<c:if test='${not empty position}'><c:url value='/positions/${position.id}'/></c:if>"
     data-result-base="">

  <aside class="side">
    <a class="side-new ${empty position ? 'active' : ''}" href="<c:url value='/positions'/>"><svg class="i"><use href="#i-plus"/></svg>새 포지션</a>
    <ul class="side-list" id="side-list" data-more="<c:url value='/positions/more'/>" data-page="${sidePage}" data-next="${sideNext}">
      <c:forEach var="p" items="${positions}"><%@ include file="common/side-pos.jspf" %></c:forEach>
    </ul>
  </aside>

  <section class="stage" id="criteria">
    <header class="stage-head">
      <c:choose>
        <c:when test="${empty position}"><h2>새 포지션</h2>
          <div class="stage-actions"><button type="submit" form="crit-form" class="btn btn-primary" id="crit-go">평가 기준 추출<svg class="i"><use href="#i-arrow-right"/></svg></button></div></c:when>
        <c:otherwise>
          <div class="title-edit">
            <h2 id="pos-title"><c:out value="${position.title}"/></h2>
            <button type="button" class="icon-btn" id="pos-rename" title="이름 바꾸기"><svg class="i"><use href="#i-pencil"/></svg></button>
          </div>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty position}">
        <div class="stage-actions">
          <form method="post" action="<c:url value='/positions/${position.id}/criteria/extract'/>" data-busy="평가 기준을 추출하는 중"
                data-confirm="평가 기준을 새로 추출할까요? 지금 평가 기준과 이 포지션의 검사 결과가 바뀝니다.">
            <button type="submit" class="btn btn-ghost btn-sm">다시 추출</button>
          </form>
          <form method="post" action="<c:url value='/positions/${position.id}/delete'/>" data-confirm="이 포지션을 삭제할까요? 검사한 이력서와 질문지도 함께 삭제됩니다.">
            <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
          </form>
        </div>
      </c:if>
    </header>
    <%-- 올린 공고 파일: 지원자 화면처럼 「첨부 파일」 버튼 하나로 모은다 (사용자 2026-10-27) --%>
    <c:if test="${not empty position and not empty files}">
      <details class="att is-left">
        <summary class="file-chip att-btn"><svg class="i"><use href="#i-clip"/></svg><span>첨부 파일</span><span class="att-count">${fn:length(files)}</span></summary>
        <ul class="att-menu">
          <c:forEach var="pf" items="${files}">
            <li><a class="att-item" href="<c:url value='/positions/${position.id}/files/${pf.id}'/>" download title="<c:out value='${pf.fileName}'/>">
              <svg class="i"><use href="#${fn:startsWith(pf.contentType, 'image/') ? 'i-image' : 'i-file'}"/></svg><span><c:out value="${pf.fileName}"/></span><em>내려받기</em></a></li>
          </c:forEach>
        </ul>
      </details>
    </c:if>

    <c:choose>
      <c:when test="${empty position}">
        <form class="composer crit-in" id="crit-form" method="post" enctype="multipart/form-data" action="<c:url value='/positions'/>"
              data-busy="평가 기준을 추출하는 중">
          <textarea id="jdText" name="jdText" class="composer-text" placeholder="채용공고나 포지션 설명을 붙여 넣으세요"><c:out value="${jdText}"/></textarea>
          <input type="file" id="files" name="files" accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.pdf,text/plain,.txt,.md" multiple hidden>
          <ul class="img-list" id="img-list"></ul>
          <div class="composer-bar">
            <button type="button" class="btn btn-ghost btn-sm" id="img-add"><svg class="i"><use href="#i-file"/></svg>파일 추가</button>
            <span class="img-hint">PDF · TXT · MD · 이미지</span>
            <p class="img-note" id="img-note"></p>
          </div>
        </form>
      </c:when>
      <c:otherwise>
        <div class="pos-grid"${justExtracted ? ' data-write' : ''}>
          <section class="pos-col">
            <h3 class="col-label">공고</h3>
            <c:if test="${not empty position.jdText}">
              <div class="posting"><c:out value="${position.jdText}"/></div>
            </c:if>
          </section>
          <section class="pos-col">
            <h3 class="col-label">지원 조건</h3>
            <%-- 칸마다 연필: 그 칸만 고르기 칸이 되고, 고르면 바로 저장 (사용자 2026-10-27) --%>
            <dl class="terms conds" id="conds" data-exp-kind="${conds.expKind}" data-exp-years="${conds.expYears}"
                data-edu="${conds.eduLevel}">
              <div><dt>요구 경력<button type="button" class="icon-btn cond-pen" data-edit="requiredExperience" title="요구 경력 고치기"><svg class="i"><use href="#i-pencil"/></svg></button></dt><dd data-key="requiredExperience"><c:out value="${position.requiredExperience}"/></dd></div>
              <div><dt>학력<button type="button" class="icon-btn cond-pen" data-edit="requiredEducation" title="학력 고치기"><svg class="i"><use href="#i-pencil"/></svg></button></dt><dd data-key="requiredEducation"><c:out value="${position.requiredEducation}"/></dd></div>
              <div><dt>학과<button type="button" class="icon-btn cond-pen" data-edit="requiredMajor" title="학과 고치기"><svg class="i"><use href="#i-pencil"/></svg></button></dt><dd data-key="requiredMajor"><c:out value="${position.requiredMajor}"/></dd></div>
            </dl>
            <h3 class="col-label crit-label">평가 기준<span class="count" id="crit-count">${fn:length(criteria)}</span></h3>
            <ul class="crit-list" id="crit-list">
              <c:forEach var="c" items="${criteria}">
                <li class="crit" data-id="${c.id}">
                  <div class="crit-body">
                    <p class="crit-name"><c:out value="${c.name}"/></p>
                    <p class="crit-desc"><c:out value="${c.description}"/></p>
                  </div>
                  <span class="row-actions">
                    <button type="button" class="icon-btn js-crit-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
                    <button type="button" class="icon-btn danger js-crit-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
                  </span>
                </li>
              </c:forEach>
            </ul>
            <button type="button" class="add-row" id="crit-add"><svg class="i"><use href="#i-plus"/></svg>평가 기준 추가</button>
          </section>
        </div>
      </c:otherwise>
    </c:choose>
  </section>
  <%-- 다음 단계: 흰 카드 오른쪽 바깥에 따라다니는 버튼 (사용자 2026-10-27) --%>
  <c:if test="${not empty position and not empty criteria}">
    <a class="next-step" href="<c:url value='/fit?position=${position.id}'/>">
      <span class="next-step-ico"><svg class="i"><use href="#i-arrow-right"/></svg></span>
      <span class="next-step-label">적합도<br>검사</span>
    </a>
  </c:if>
</div>

<template id="tpl-crit">
  <li class="crit" data-id="">
    <div class="crit-body"><p class="crit-name"></p><p class="crit-desc"></p></div>
    <span class="row-actions">
      <button type="button" class="icon-btn js-crit-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
      <button type="button" class="icon-btn danger js-crit-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
    </span>
  </li>
</template>
<div class="toast" id="toast" role="status" hidden></div>

<script src="<c:url value='/js/extract.js'/>?v=${applicationScope.assetVersion}"></script>
<%@ include file="common/footer.jspf" %>
