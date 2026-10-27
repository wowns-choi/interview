<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="지원자 비교"/>
<c:set var="activeTab" value="ranking"/>
<%@ include file="common/header.jspf" %>

<c:choose>
<c:when test="${empty position}">
  <div class="empty-page">평가 기준을 추출한 포지션이 없습니다. <a href="<c:url value='/positions'/>">평가 기준 추출</a></div>
</c:when>
<c:otherwise>
<c:set var="pickBase" value="/ranking"/>
<%@ include file="common/position-pick.jspf" %>

<%-- 거르기: 글이 든 켜고 끄는 버튼 하나씩, 표 위 바탕에 (사용자 2026-10-27) --%>
<c:if test="${not empty rows}">
<div class="rk-filters" id="rk" data-base="<c:url value='/positions/${position.id}'/>">
  <label class="fchip"><input type="checkbox" data-rk-exclude="career" ${position.excludeCareer ? 'checked' : ''}><span>경력 미충족 제외</span></label>
  <label class="fchip"><input type="checkbox" data-rk-exclude="education" ${position.excludeEducation ? 'checked' : ''}><span>학력 미충족 제외</span></label>
  <label class="fchip"><input type="checkbox" data-rk-filter="interviewed"><span>면접 본 사람만</span></label>
  <label class="fchip"><input type="checkbox" data-rk-filter="passed"><span>합격한 사람만</span></label>
  <label class="fchip"><input type="checkbox" data-rk-filter="engineer"><span>정보처리기사 있는 사람만</span></label>
  <label class="fchip"><input type="checkbox" data-rk-filter="sqld"><span>SQLD 있는 사람만</span></label>
  <div class="rk-legend">
    <span><i class="rk-sq ev-DIRECT"></i>충족 1</span>
    <span><i class="rk-sq ev-INDIRECT"></i>부분 충족 0.5</span>
    <span><i class="rk-sq ev-NONE"></i>미확인 0</span>
  </div>
</div>
</c:if>

<section class="stage">
  <c:choose>
    <c:when test="${empty rows}">
      <div class="empty-box">아직 이력서가 없습니다. <a href="<c:url value='/fit?position=${position.id}'/>">이력서 올리기</a></div>
    </c:when>
    <c:otherwise>
      <div class="rk-scroll">
        <table class="rk">
          <thead>
            <tr>
              <th class="rk-no"></th>
              <th class="rk-name">지원자</th>
              <th class="rk-total">평가 기준 충족</th>
              <th class="rk-cnd">경력</th>
              <th class="rk-cnd">학력</th>
              <c:forEach var="c" items="${criteria}" varStatus="cs"><th class="rk-c${cs.first ? ' rk-first' : ''}${cs.last ? ' rk-last' : ''}" title="<c:out value='${c.name}'/>"><span class="rk-cwrap"><span class="rk-cname"><c:out value="${c.name}"/></span></span></th></c:forEach>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="row" items="${rows}" varStatus="st">
              <c:if test="${row.state != 'RANKED' and (st.first or rows[st.index - 1].state != row.state)}">
                <c:choose>
                  <c:when test="${row.state == 'EXCLUDED'}">
                    <tr class="rk-group rk-fold" data-rk-fold="EXCLUDED"><td colspan="${5 + fn:length(criteria)}">제외됨 ${excludedCount}명<span class="rk-fold-mark">›</span></td></tr>
                  </c:when>
                  <c:otherwise>
                    <c:choose><c:when test="${row.state == 'STALE'}"><tr class="rk-group rk-fold" data-rk-fold="STALE"><td colspan="${5 + fn:length(criteria)}">재검사 필요 ${staleCount}명<span class="rk-fold-mark">›</span></td></tr></c:when><c:otherwise><tr class="rk-group rk-fold" data-rk-fold="UNCHECKED"><td colspan="${5 + fn:length(criteria)}">검사 전 ${uncheckedCount}명<span class="rk-fold-mark">›</span></td></tr></c:otherwise></c:choose>
                  </c:otherwise>
                </c:choose>
              </c:if>
              <c:set var="s" value="${row.score}"/>
              <tr class="rk-row is-${row.state}"${row.state != 'RANKED' ? ' hidden' : ''} data-interviewed="${row.interviewed}" data-decision="${row.decision}" data-engineer="${row.infoEngineer}" data-sqld="${row.sqld}" data-href="<c:url value='/results/${row.candidateId}'/>">
                <td class="rk-no">${empty row.rank ? '' : row.rank}</td>
                <td class="rk-name"><a href="<c:url value='/results/${row.candidateId}'/>"><c:out value="${row.displayName}"/></a><c:if test="${not empty row.exclusion}"><small class="rk-why">${row.exclusion}</small></c:if><c:if test="${row.infoEngineer}"><span class="rk-cert">정보처리기사</span></c:if><c:if test="${row.sqld}"><span class="rk-cert">SQLD</span></c:if><c:if test="${row.interviewed}"><span class="rk-iv">면접<c:if test="${not empty row.interviewDate}"> ${row.interviewDate.monthValue}.${row.interviewDate.dayOfMonth}</c:if></span></c:if><c:if test="${not empty row.decision}"><span class="rk-dec is-${row.decision}">${row.decision == 'PASS' ? '합격' : '불합격'}</span></c:if></td>
                <c:choose>
                  <c:when test="${empty s}">
                    <td class="rk-total"><span class="muted">${row.statusLabel}</span></td>
                    <td colspan="${2 + fn:length(criteria)}"></td>
                  </c:when>
                  <c:otherwise>
                    <td class="rk-total"><span class="rk-score">${s.totalText}<small> / ${s.max}</small></span><span class="rk-bar"><span style="width: ${s.percent}%"></span></span></td>
                    <td class="rk-cnd"><span class="rk-cond is-${s.career == 1 ? 'met' : 'unmet'}">${s.career == 1 ? '충족' : '미충족'}</span></td>
                    <td class="rk-cnd"><span class="rk-cond is-${s.education == 1 ? 'met' : 'unmet'}">${s.education == 1 ? '충족' : '미충족'}</span></td>
                    <c:forEach var="it" items="${s.items}" varStatus="is">
                      <c:set var="evK" value="${empty it.match ? 'UNCHECKED' : it.match}"/>
                      <td class="rk-c${is.first ? ' rk-first' : ''}${is.last ? ' rk-last' : ''}"><i class="rk-sq ev-${evK}" title="${is.count}. <c:out value='${it.name}'/> · ${empty it.match ? '검사 전' : it.match == 'DIRECT' ? '충족' : it.match == 'INDIRECT' ? '부분 충족' : '미확인'}"></i></td>
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
