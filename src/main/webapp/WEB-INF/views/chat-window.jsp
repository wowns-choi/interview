<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- AI와 대화 팝업 창 (사용자 2026-10-28): 지원자 화면의 「AI와 대화」 버튼으로만 연다. 그 화면을 떠나면 닫힌다 (chat.js) --%>
<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>AI와 대화 - <c:out value="${r.candidate.displayName}"/></title>
  <link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/variable/pretendardvariable-dynamic-subset.min.css">
  <link rel="stylesheet" href="<c:url value='/css/app.css'/>?v=${applicationScope.assetVersion}">
</head>
<body class="chat-window-body">
<%@ include file="/WEB-INF/views/common/icons.jspf" %>
<div class="chat-dialog is-window" id="dlg-chat" data-url="<c:url value='/results/${r.candidate.id}/chat'/>"
     data-home="<c:url value='/results/${r.candidate.id}'/>">
  <header>
    <h3>AI와 대화 <span class="chat-who">- <c:out value="${r.candidate.displayName}"/></span></h3>
    <details class="chat-src" id="chat-src-box">
      <summary class="chat-src-cap">AI가 보는 자료 <span class="chat-src-n" id="chat-src-n"></span></summary>
      <ul class="chat-src-list" id="chat-src"></ul>
    </details>
    <button type="button" class="btn btn-ghost btn-sm" id="chat-reset">새 대화</button>
    <button type="button" class="icon-btn" id="chat-dock" title="지원자 화면 안으로 되돌리기"><svg class="i" viewBox="0 0 24 24"><path d="M10 20H4v-6M4 20l8-8M6 10V6a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button>
  </header>
  <div class="chat-log" id="chat-log"></div>
  <div class="chat-examples" id="chat-examples"
       data-before="제공된 정보들을 토대로 채용해야 할 이유와 채용하지 말아야 할 이유를 각각 정리해 주세요.|이력서에서 서로 맞지 않거나 과장된 것으로 보이는 내용이 있다면 짚어 주세요.|이 포지션 기준으로 지원자의 핵심 강점과 우려되는 점을 하나씩 알려 주세요."
       data-after="제공된 정보들을 토대로 채용해야 할 이유와 채용하지 말아야 할 이유를 각각 정리해 주세요.|이력서 내용과 면접에서의 답변이 일치하지 않는 부분이 있다면 짚어 주세요.|면접 답변 중 구체적이고 설득력 있었던 부분과 부족했던 부분을 정리해 주세요."></div>
  <form class="chat-form" id="chat-form">
    <textarea id="chat-input" rows="2" maxlength="4000" placeholder="이 지원자에 대해 물어보세요"></textarea>
    <button type="submit" class="btn btn-primary" id="chat-send">보내기</button>
  </form>
</div>
<div class="toast" id="toast" role="status" hidden></div>
<script src="<c:url value='/js/chat.js'/>?v=${applicationScope.assetVersion}"></script>
<script src="<c:url value='/js/app.js'/>?v=${applicationScope.assetVersion}"></script>
</body>
</html>
