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
</div>
</c:if>

<%-- 평가 기준이 바뀐 지원자를 한 번에 다시 검사 (사용자 2026-10-28): 표 위 띠, 누르면 무엇이 바뀌는지 알리는 창 --%>
<c:if test="${changedCount > 0}">
<div class="rk-notice">
  <span class="rk-notice-ico"><svg class="i"><use href="#i-refresh"/></svg></span>
  <span class="rk-notice-text">평가 기준이 바뀐 뒤 아직 다시 검사하지 않은 지원자 <b>${changedCount}명</b></span>
  <button type="button" class="btn btn-primary btn-sm" data-dialog="dlg-recheck">지금 평가 기준으로 다시 검사</button>
</div>
<dialog class="pf-dialog rk-dialog" id="dlg-recheck">
  <header><h3>${changedCount}명 다시 검사</h3><button type="button" class="icon-btn" data-close title="닫기"><svg class="i"><use href="#i-x"/></svg></button></header>
  <ul class="rk-dlg-who">
    <c:forEach var="row" items="${rows}"><c:if test="${changedIds.contains(row.candidateId)}"><li><b><c:out value="${row.displayName}"/></b><span>${row.createdText}</span></li></c:if></c:forEach>
  </ul>
  <div class="rk-dlg-cols">
    <div class="rk-dlg-col is-change">
      <h4>새로 바뀌어요</h4>
      <ul>
        <li>개인정보</li>
        <li>평가 기준 충족 여부</li>
      </ul>
    </div>
  </div>
  <form class="rk-dlg-actions" method="post" action="<c:url value='/positions/${position.id}/recheck'/>">
    <button type="button" class="btn btn-ghost" data-close>취소</button>
    <button type="submit" class="btn btn-primary">${changedCount}명 다시 검사</button>
  </form>
</dialog>
</c:if>

<section class="stage rk-stage"<c:if test="${rankBusy}"> data-poll="5000"</c:if>>
  <c:choose>
    <c:when test="${empty rows}">
      <div class="empty-box">아직 이력서가 없습니다. <a href="<c:url value='/fit?position=${position.id}'/>">이력서 올리기</a></div>
    </c:when>
    <c:otherwise>
      <div class="rk-scroll">
        <table class="rk">
          <thead>
            <tr>
              <th class="rk-no">순위</th>
              <th class="rk-name">지원자</th>
              <th class="rk-cnd">경력</th>
              <th class="rk-cnd">학력</th>
              <th class="rk-cnd">정보처리기사</th>
              <th class="rk-cnd">SQLD</th>
              <th class="rk-ivc">면접</th>
              <th class="rk-decc">합격 여부</th>
              <th class="rk-total">평가 기준 충족</th>
              <th class="rk-morec"></th>
            </tr>
          </thead>
          <tbody>
            <c:forEach var="row" items="${rows}" varStatus="st">
              <c:if test="${row.state != 'RANKED' and (st.first or rows[st.index - 1].state != row.state)}">
                <c:choose>
                  <c:when test="${row.state == 'EXCLUDED'}">
                    <tr class="rk-group rk-fold" data-rk-fold="EXCLUDED"><td colspan="10">제외됨 ${excludedCount}명<span class="rk-fold-mark">›</span></td></tr>
                  </c:when>
                  <c:otherwise>
                    <c:choose><c:when test="${row.state == 'STALE'}"><tr class="rk-group rk-fold" data-rk-fold="STALE"><td colspan="10">재검사 필요 ${staleCount}명<span class="rk-fold-mark">›</span></td></tr></c:when><c:otherwise><tr class="rk-group rk-fold" data-rk-fold="UNCHECKED"><td colspan="10">검사 전 ${uncheckedCount}명<span class="rk-fold-mark">›</span></td></tr></c:otherwise></c:choose>
                  </c:otherwise>
                </c:choose>
              </c:if>
              <c:set var="s" value="${row.score}"/>
              <tr class="rk-row is-${row.state}"${row.state != 'RANKED' ? ' hidden' : ''} data-interviewed="${not empty row.interviewDate}" data-decision="${row.decision}" data-engineer="${row.infoEngineer}" data-sqld="${row.sqld}">
                <td class="rk-no">${empty row.rank ? '' : row.rank.toString().concat('등')}</td>
                <td class="rk-name"><span class="rk-who"><c:out value="${row.displayName}"/></span><c:if test="${changedIds.contains(row.candidateId) and not row.inProgress}"><small class="rk-why rk-old">이전 평가 기준</small></c:if><c:if test="${not empty row.exclusion}"><small class="rk-why">${row.exclusion}</small></c:if></td>
                <c:choose>
                  <c:when test="${empty s}">
                    <td class="rk-pending" colspan="2"><span class="muted">${row.statusLabel}</span></td>
                  </c:when>
                  <c:otherwise>
                    <td class="rk-cnd"><span class="rk-cond is-${s.career == 1 ? 'met' : 'unmet'}">${s.career == 1 ? '충족' : '미충족'}</span></td>
                    <td class="rk-cnd"><span class="rk-cond is-${s.education == 1 ? 'met' : 'unmet'}">${s.education == 1 ? '충족' : '미충족'}</span></td>
                  </c:otherwise>
                </c:choose>
                <td class="rk-cnd"><c:if test="${not empty s}"><span class="rk-cond is-${row.infoEngineer ? 'met' : 'unmet'}">${row.infoEngineer ? '있음' : '없음'}</span></c:if></td>
                <td class="rk-cnd"><c:if test="${not empty s}"><span class="rk-cond is-${row.sqld ? 'met' : 'unmet'}">${row.sqld ? '있음' : '없음'}</span></c:if></td>
                <td class="rk-ivc"><span class="rk-iv${empty row.interviewDate ? ' is-none' : ''}">${empty row.interviewDate ? '면접 전' : row.interviewText}</span></td>
                <td class="rk-decc"><c:if test="${not empty row.decision}"><span class="rk-dec is-${row.decision}">${row.decision == 'PASS' ? '합격' : '불합격'}</span></c:if></td>
                <td class="rk-total"><c:if test="${not empty s}"><span class="rk-score">${s.score100}<small>점</small></span><span class="rk-open-mark" aria-hidden="true"></span></c:if></td>
                <td class="rk-morec"><a class="rk-more" href="<c:url value='/results/${row.candidateId}'/>">자세히<span class="rk-more-mark">›</span></a></td>
              </tr>
              <c:if test="${not empty s}">
              <tr class="rk-detail" hidden>
                <td colspan="10">
                  <div class="rk-fold"><div class="rk-fold-in"><div class="rk-detail-box">
                    <ol class="rk-crits">
                      <c:forEach var="it" items="${s.items}" varStatus="is">
                        <c:set var="evK" value="${empty it.match ? 'UNCHECKED' : it.match}"/>
                        <li><i class="rk-sq ev-${evK}"></i><span><c:out value="${it.name}"/></span><em>${empty it.match ? '검사 전' : it.match == 'DIRECT' ? '충족' : it.match == 'INDIRECT' ? '부분 충족' : '미확인'}</em></li>
                      </c:forEach>
                    </ol>
                    <div class="rk-detail-sum">
                      <span class="rk-detail-total">${s.totalText}<small> / ${s.max}</small></span>
                      <span class="rk-legend"><span><i class="rk-sq ev-DIRECT"></i>충족 1</span><span><i class="rk-sq ev-INDIRECT"></i>부분 충족 0.5</span><span><i class="rk-sq ev-NONE"></i>미확인 0</span></span>
                    </div>
                  </div></div></div>
                </td>
              </tr>
              </c:if>
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
