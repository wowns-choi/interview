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
      <div class="rk-top" id="rk" data-base="<c:url value='/positions/${position.id}'/>">
        <label class="switch"><input type="checkbox" data-rk-exclude="career" ${position.excludeCareer ? 'checked' : ''}><span class="switch-track"></span>경력 미충족 제외</label>
        <label class="switch"><input type="checkbox" data-rk-exclude="education" ${position.excludeEducation ? 'checked' : ''}><span class="switch-track"></span>학력 미충족 제외</label>
        <div class="rk-legend">
          <span><i class="hb ev-DIRECT"></i>직접 1</span>
          <span><i class="hb ev-INDIRECT"></i>간접 0.5</span>
          <span><i class="hb ev-NONE"></i>없음 0</span>
        </div>
      </div>
      <div class="rk-scroll">
        <table class="rk">
          <thead>
            <tr>
              <th class="rk-no"></th>
              <th class="rk-name">지원자</th>
              <th class="rk-total">점수</th>
              <th class="rk-c rk-first">경력</th>
              <th class="rk-c">학력</th>
              <c:forEach var="c" items="${criteria}" varStatus="cs"><th class="rk-c${cs.first ? ' rk-first' : ''}" title="<c:out value='${c.name}'/>"><span><c:out value="${c.name}"/></span></th></c:forEach>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="row" items="${rows}" varStatus="st">
              <c:if test="${row.state != 'RANKED' and (st.first or rows[st.index - 1].state != row.state)}">
                <c:choose>
                  <c:when test="${row.state == 'EXCLUDED'}">
                    <tr class="rk-group rk-fold" data-rk-fold><td colspan="${5 + fn:length(criteria)}">제외됨 ${excludedCount}명<span class="rk-fold-mark">›</span></td></tr>
                  </c:when>
                  <c:otherwise>
                    <tr class="rk-group"><td colspan="${5 + fn:length(criteria)}">${row.state == 'STALE' ? '재검사 필요' : '검사 전'}</td></tr>
                  </c:otherwise>
                </c:choose>
              </c:if>
              <c:set var="s" value="${row.score}"/>
              <tr class="rk-row is-${row.state}"${row.state == 'EXCLUDED' ? ' hidden' : ''} data-href="<c:url value='/results/${row.candidateId}'/>">
                <td class="rk-no">${empty row.rank ? '' : row.rank}</td>
                <td class="rk-name"><a href="<c:url value='/results/${row.candidateId}'/>"><c:out value="${row.displayName}"/></a><c:if test="${not empty row.exclusion}"><small class="rk-why">${row.exclusion}</small></c:if></td>
                <c:choose>
                  <c:when test="${empty s}">
                    <td class="rk-total"><span class="muted">${row.statusLabel}</span></td>
                    <td colspan="${2 + fn:length(criteria)}"></td>
                  </c:when>
                  <c:otherwise>
                    <td class="rk-total"><span class="rk-score">${s.totalText}<small> / ${s.max}</small></span><span class="rk-bar"><span style="width: ${s.percent}%"></span></span></td>
                    <td class="rk-c rk-first"><span class="rk-cond is-${s.career == 1 ? 'met' : 'unmet'}">${s.career == 1 ? '충족' : '미충족'}</span></td>
                    <td class="rk-c"><span class="rk-cond is-${s.education == 1 ? 'met' : 'unmet'}">${s.education == 1 ? '충족' : '미충족'}</span></td>
                    <c:forEach var="it" items="${s.items}" varStatus="is">
                      <td class="rk-c${is.first ? ' rk-first' : ''}"><i class="hb ev-${empty it.match ? 'UNCHECKED' : it.match}" title="${empty it.match ? '검사 전' : it.match == 'DIRECT' ? '직접 근거' : it.match == 'INDIRECT' ? '간접 근거' : '근거 없음'}"></i></td>
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
