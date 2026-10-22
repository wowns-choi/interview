<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="설정"/>
<c:set var="activeTab" value="settings"/>
<%@ include file="common/header.jspf" %>

<div class="tpl-wrap">
<h1 class="tpl-title">설정</h1>

<section class="stage set-card">
  <header class="set-head">
    <h2>GPT API 키</h2>
    <c:choose>
      <c:when test="${apiKey.set}"><span class="set-state ok">✓ <c:out value="${apiKey.masked}"/></span></c:when>
      <c:otherwise><span class="set-state no">없음</span></c:otherwise>
    </c:choose>
  </header>
  <form class="set-row" method="post" action="<c:url value='/settings/api-key'/>">
    <input class="input" type="password" name="apiKey" autocomplete="off" placeholder="sk-..." required>
    <button class="btn btn-primary" type="submit">${apiKey.set ? '바꾸기' : '저장'}</button>
  </form>
</section>

<section class="stage set-card">
  <header class="set-head">
    <h2>로컬 AI (Ollama)</h2>
    <c:choose>
      <c:when test="${ollama.state == 'READY'}"><span class="set-state ok">✓ 준비됨 · v<c:out value="${ollama.version}"/></span></c:when>
      <c:when test="${ollama.state == 'NO_MODEL'}"><span class="set-state no">모델 없음</span></c:when>
      <c:when test="${ollama.state == 'NOT_RUNNING'}"><span class="set-state no">실행 중 아님</span></c:when>
      <c:otherwise><span class="set-state no">설치 안 됨</span></c:otherwise>
    </c:choose>
    <a class="btn btn-sm btn-ghost set-recheck" href="<c:url value='/settings'/>">다시 확인</a>
  </header>

  <ol class="set-steps">
    <li class="${ollama.state == 'NOT_INSTALLED' ? '' : 'done'}">
      <span>설치</span>
      <a href="<c:out value='${ollama.downloadUrl}'/>" target="_blank" rel="noopener"><c:out value="${ollama.downloadUrl}"/></a>
    </li>
    <li class="${ollama.state == 'NOT_INSTALLED' or ollama.state == 'NOT_RUNNING' ? '' : 'done'}">
      <span>실행</span>
      <em>Ollama 앱 실행</em>
    </li>
    <li class="${ollama.state == 'READY' ? 'done' : ''}">
      <span>모델 받기</span>
      <code class="set-cmd"><c:out value="${ollama.pullCommand}"/></code>
      <button type="button" class="btn btn-sm" data-copy="<c:out value='${ollama.pullCommand}'/>">복사</button>
    </li>
  </ol>
</section>
</div>

<script>
document.querySelectorAll('[data-copy]').forEach(b => b.addEventListener('click', () => {
  navigator.clipboard.writeText(b.dataset.copy).then(() => { b.textContent = '복사됨'; setTimeout(() => b.textContent = '복사', 1500); });
}));
</script>
<%@ include file="common/footer.jspf" %>
