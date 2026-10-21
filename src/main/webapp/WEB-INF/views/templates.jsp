<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="공통 질문 설정"/>
<c:set var="activeTab" value="templates"/>
<%@ include file="common/header.jspf" %>

<div class="tpl-wrap">
<h1 class="tpl-title">공통 질문</h1>

<div class="board" id="board" data-base="<c:url value='/templates'/>">
  <c:forEach var="b" items="${board}">
    <section class="sec" data-id="${b.section.id}">
      <header class="sec-head">
        <span class="handle sec-handle" title="끌어서 섹션 순서 바꾸기"><svg class="i"><use href="#i-grip"/></svg></span>
        <h2 class="sec-name"><c:out value="${b.section.name}"/></h2>
        <span class="sec-count">${fn:length(b.questions)}</span>
        <button type="button" class="icon-btn js-rename" title="섹션 이름 수정"><svg class="i"><use href="#i-pencil"/></svg></button>
        <span class="sec-spacer"></span>
        <button type="button" class="icon-btn danger js-del-section" title="섹션 삭제"><svg class="i"><use href="#i-trash"/></svg></button>
      </header>
      <ol class="q-list">
        <c:forEach var="t" items="${b.questions}">
          <li class="q" data-id="${t.id}">
            <span class="handle q-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
            <p class="q-content"><c:out value="${t.content}"/></p>
            <span class="q-actions">
              <button type="button" class="icon-btn js-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
              <button type="button" class="icon-btn danger js-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
            </span>
          </li>
        </c:forEach>
      </ol>
      <button type="button" class="add-q js-add-q"><svg class="i"><use href="#i-plus"/></svg>질문 추가</button>
    </section>
  </c:forEach>
</div>
<p class="board-empty" id="board-empty" ${empty board ? '' : 'hidden'}>아직 섹션이 없습니다</p>
<button type="button" class="add-sec" id="add-section"><svg class="i"><use href="#i-plus"/></svg>섹션 추가</button>
</div>

<%-- JS가 새 섹션·질문을 만들 때 복제하는 틀 --%>
<template id="tpl-section">
  <section class="sec" data-id="">
    <header class="sec-head">
      <span class="handle sec-handle" title="끌어서 섹션 순서 바꾸기"><svg class="i"><use href="#i-grip"/></svg></span>
      <h2 class="sec-name"></h2>
      <span class="sec-count">0</span>
      <button type="button" class="icon-btn js-rename" title="섹션 이름 수정"><svg class="i"><use href="#i-pencil"/></svg></button>
      <span class="sec-spacer"></span>
      <button type="button" class="icon-btn danger js-del-section" title="섹션 삭제"><svg class="i"><use href="#i-trash"/></svg></button>
    </header>
    <ol class="q-list"></ol>
    <button type="button" class="add-q js-add-q"><svg class="i"><use href="#i-plus"/></svg>질문 추가</button>
  </section>
</template>
<template id="tpl-question">
  <li class="q" data-id="">
    <span class="handle q-handle" title="끌어서 옮기기"><svg class="i"><use href="#i-grip"/></svg></span>
    <p class="q-content"></p>
    <span class="q-actions">
      <button type="button" class="icon-btn js-edit" title="수정"><svg class="i"><use href="#i-pencil"/></svg></button>
      <button type="button" class="icon-btn danger js-del" title="삭제"><svg class="i"><use href="#i-trash"/></svg></button>
    </span>
  </li>
</template>

<div class="toast" id="toast" role="status" hidden></div>

<script src="<c:url value='/js/templates.js'/>?v=${applicationScope.assetVersion}"></script>
<%@ include file="common/footer.jspf" %>
