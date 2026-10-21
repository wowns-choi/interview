<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 비교"/>
<c:set var="activeTab" value="ranking"/>
<%@ include file="common/header.jspf" %>

<c:choose>
<c:when test="${empty position}">
  <div class="empty-page">평가항목을 추출한 포지션이 없습니다. <a href="<c:url value='/positions'/>">평가 항목 추출</a></div>
</c:when>
<c:otherwise>
<c:set var="pickBase" value="/ranking"/>
<%@ include file="common/position-pick.jspf" %>

<section class="stage">
  <c:choose>
    <c:when test="${empty rows}">
      <div class="empty-box">아직 이력서가 없습니다. <a href="<c:url value='/fit?position=${position.id}'/>">이력서 올리기</a></div>
    </c:when>
    <c:otherwise>
      <div class="ev-legend rk-legend">
        <span><i class="ev-DIRECT"></i>1점</span>
        <span><i class="ev-INDIRECT"></i>0.5점</span>
        <span><i class="ev-NONE"></i>0점</span>
      </div>
      <div class="rk-scroll">
        <table class="rk">
          <thead>
            <tr>
              <th class="rk-no">순위</th>
              <th class="rk-name">지원자</th>
              <th class="rk-total">점수</th>
              <th class="rk-c">경력</th>
              <th class="rk-c">학력</th>
              <c:forEach var="c" items="${criteria}"><th class="rk-c rk-crit" title="<c:out value='${c.name}'/>"><span><c:out value="${c.name}"/></span></th></c:forEach>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="row" items="${rows}" varStatus="st">
              <c:if test="${row.state != 'RANKED' and (st.first or rows[st.index - 1].state != row.state)}">
                <tr class="rk-group"><td colspan="${5 + fn:length(criteria)}">${row.state == 'STALE' ? '재검사 필요' : '검사 전'}</td></tr>
              </c:if>
              <c:set var="s" value="${row.score}"/>
              <tr class="rk-row is-${row.state}" data-href="<c:url value='/results/${row.candidateId}'/>">
                <td class="rk-no">${empty row.rank ? '' : row.rank}</td>
                <td class="rk-name"><a href="<c:url value='/results/${row.candidateId}'/>"><c:out value="${row.displayName}"/></a></td>
                <c:choose>
                  <c:when test="${empty s}">
                    <td class="rk-total"><span class="muted">${row.statusLabel}</span></td>
                    <td colspan="${2 + fn:length(criteria)}"></td>
                  </c:when>
                  <c:otherwise>
                    <td class="rk-total">
                      <div class="rk-bar"><span style="width: ${s.percent}%"></span></div>
                      <strong>${s.totalText}</strong><small> / ${s.max}</small>
                    </td>
                    <td class="rk-c"><span class="rk-cell ev-${s.career == 1 ? 'DIRECT' : 'NONE'}">${s.career}</span></td>
                    <td class="rk-c"><span class="rk-cell ev-${s.education == 1 ? 'DIRECT' : 'NONE'}">${s.education}</span></td>
                    <c:forEach var="it" items="${s.items}">
                      <td class="rk-c">
                        <c:choose>
                          <c:when test="${empty it.match}"><span class="rk-cell ev-UNCHECKED">-</span></c:when>
                          <c:otherwise><span class="rk-cell ev-${it.match}">${it.match == 'DIRECT' ? '1' : it.match == 'INDIRECT' ? '0.5' : '0'}</span></c:otherwise>
                        </c:choose>
                      </td>
                    </c:forEach>
                  </c:otherwise>
                </c:choose>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</section>
</c:otherwise>
</c:choose>
<%@ include file="common/footer.jspf" %>
