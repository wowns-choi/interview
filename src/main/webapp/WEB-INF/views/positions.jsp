<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty position ? '평가 항목 추출' : position.title}"/>
<c:set var="activeTab" value="positions"/>
<%@ include file="common/header.jspf" %>

<div class="split" id="ws"
     data-position-base="<c:if test='${not empty position}'><c:url value='/positions/${position.id}'/></c:if>"
     data-result-base="">

  <aside class="side">
    <a class="side-new ${empty position ? 'active' : ''}" href="<c:url value='/positions'/>"><svg class="i"><use href="#i-plus"/></svg>새 포지션</a>
    <ul class="side-list">
      <c:forEach var="p" items="${positions}">
        <li class="side-pos">
          <a class="side-item ${not empty position and position.id == p.id ? 'active' : ''}" href="<c:url value='/positions/${p.id}'/>">
            <strong><c:out value="${p.title}"/></strong>
            <small>${fn:substring(fn:replace(p.createdAt, '-', '.'), 5, 10)} · 평가항목 ${p.criteriaCount} · 지원자 ${p.candidateCount}</small>
          </a>
          <button type="button" class="icon-btn side-rename" title="이름 바꾸기"><svg class="i"><use href="#i-pencil"/></svg></button>
        </li>
      </c:forEach>
    </ul>
  </aside>

  <section class="stage" id="criteria">
    <header class="stage-head">
      <c:choose>
        <c:when test="${empty position}"><h2>새 포지션</h2></c:when>
        <c:otherwise>
          <div class="title-edit">
            <h2 id="pos-title"><c:out value="${position.title}"/></h2>
            <button type="button" class="icon-btn" id="pos-rename" title="이름 바꾸기"><svg class="i"><use href="#i-pencil"/></svg></button>
          </div>
        </c:otherwise>
      </c:choose>
      <c:if test="${not empty position}">
        <div class="stage-actions">
          <c:if test="${not empty criteria}">
            <a class="btn btn-primary btn-sm" href="<c:url value='/fit?position=${position.id}'/>">적합도 검사<svg class="i"><use href="#i-arrow-right"/></svg></a>
          </c:if>
          <form method="post" action="<c:url value='/positions/${position.id}/criteria/extract'/>" data-busy="평가항목을 추출하는 중"
                data-confirm="평가항목을 새로 추출할까요? 지금 평가항목과 이 포지션의 검사 결과가 바뀝니다.">
            <button type="submit" class="btn btn-ghost btn-sm">다시 추출</button>
          </form>
          <form method="post" action="<c:url value='/positions/${position.id}/delete'/>" data-confirm="이 포지션을 삭제할까요? 검사한 이력서와 질문지도 함께 삭제됩니다.">
            <button type="submit" class="btn btn-ghost btn-sm btn-danger-text">삭제</button>
          </form>
        </div>
      </c:if>
    </header>

    <c:choose>
      <c:when test="${empty position}">
        <form class="composer crit-in" id="crit-form" method="post" enctype="multipart/form-data" action="<c:url value='/positions'/>"
              data-busy="평가항목을 추출하는 중">
          <textarea id="jdText" name="jdText" class="composer-text" placeholder="채용공고나 포지션 설명을 붙여 넣으세요"><c:out value="${jdText}"/></textarea>
          <input type="file" id="files" name="files" accept="image/png,image/jpeg,image/webp,image/gif,application/pdf,.pdf,text/plain,.txt,.md" multiple hidden>
          <ul class="img-list" id="img-list"></ul>
          <div class="composer-bar">
            <button type="button" class="btn btn-ghost btn-sm" id="img-add"><svg class="i"><use href="#i-file"/></svg>파일 추가</button>
            <span class="img-hint">PDF · TXT · MD · 이미지</span>
            <p class="img-note" id="img-note"></p>
            <button type="submit" class="btn btn-primary composer-go">평가항목 추출<svg class="i"><use href="#i-arrow-right"/></svg></button>
          </div>
        </form>
      </c:when>
      <c:otherwise>
        <div class="pos-grid">
          <section class="pos-col">
            <h3 class="col-label">공고</h3>
            <c:if test="${not empty position.jdText}">
              <div class="posting"><c:out value="${position.jdText}"/></div>
            </c:if>
            <c:if test="${not empty imageIds}">
              <ul class="img-list">
                <c:forEach var="imgId" items="${imageIds}">
                  <li><a href="<c:url value='/positions/${position.id}/images/${imgId}'/>" target="_blank" rel="noopener">
                    <img src="<c:url value='/positions/${position.id}/images/${imgId}'/>" alt="포지션 이미지"></a></li>
                </c:forEach>
              </ul>
            </c:if>
          </section>
          <section class="pos-col">
            <h3 class="col-label">평가항목<span class="count" id="crit-count">${fn:length(criteria)}</span></h3>
            <dl class="terms conds" id="conds" data-exp-kind="${conds.expKind}" data-exp-years="${conds.expYears}"
                data-edu="${conds.eduLevel}">
              <div><dt>요구 경력</dt><dd data-key="requiredExperience"><c:out value="${position.requiredExperience}"/></dd></div>
              <div><dt>학력</dt><dd data-key="requiredEducation"><c:out value="${position.requiredEducation}"/></dd></div>
              <div><dt>학과</dt><dd data-key="requiredMajor"><c:out value="${position.requiredMajor}"/></dd></div>
              <button type="button" class="icon-btn conds-edit" id="conds-edit" title="조건 고치기"><svg class="i"><use href="#i-pencil"/></svg></button>
            </dl>
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
            <button type="button" class="add-row" id="crit-add"><svg class="i"><use href="#i-plus"/></svg>평가항목 추가</button>
          </section>
        </div>
      </c:otherwise>
    </c:choose>
  </section>
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
