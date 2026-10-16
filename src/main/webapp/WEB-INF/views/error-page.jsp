<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="찾을 수 없음"/>
<%@ include file="common/header.jspf" %>

<div class="empty-state">
  <h1>찾을 수 없습니다</h1>
  <p class="muted"><c:out value="${errorMessage}"/></p>
  <a class="btn btn-primary" href="<c:url value='/extract'/>">면접 질문 추출하기로</a>
</div>

<%@ include file="common/footer.jspf" %>
