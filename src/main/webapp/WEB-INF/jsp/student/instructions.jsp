<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="e" value="${card.exam}"/>
<t:layout title="${e.title}" active="dashboard">
    <div class="row justify-content-center"><div class="col-lg-8">
        <h1 class="h3 page-title mb-1"><c:out value="${e.title}"/></h1>
        <div class="text-muted mb-3"><c:out value="${e.subject.name}"/></div>
        <div class="card mb-3"><div class="card-body">
            <div class="row text-center g-3">
                <div class="col-6 col-md-3"><div class="fs-4 fw-bold">${e.durationMinutes}</div><div class="small text-muted">minutes</div></div>
                <div class="col-6 col-md-3"><div class="fs-4 fw-bold">${card.attemptsUsed}/${e.maxAttempts}</div><div class="small text-muted">attempts used</div></div>
                <div class="col-6 col-md-3"><div class="fs-4 fw-bold"><c:out value="${fmt.num(e.passingMarks)}"/></div><div class="small text-muted">passing marks</div></div>
                <div class="col-6 col-md-3"><div class="fs-4 fw-bold">${e.negativeMarking ? 'Yes' : 'No'}</div><div class="small text-muted">negative marking</div></div>
            </div>
        </div></div>
        <div class="card mb-3"><div class="card-body">
            <h2 class="h6">Instructions</h2>
            <ol class="mb-3">
                <li><c:choose><c:when test="${not empty questionCount}">All ${questionCount} questions</c:when><c:otherwise>All questions</c:otherwise></c:choose> are compulsory; there is no internal choice.</li>
                <li><c:choose><c:when test="${not empty marksEach}">Each question carries ${marksEach} <c:out value="${marksEach == '1' ? 'mark' : 'marks'}"/>. </c:when><c:otherwise>Each question carries the marks shown with it. </c:otherwise></c:choose>For a single-answer question only one of the options is correct.</li>
                <c:choose>
                    <c:when test="${e.negativeMarking}"><li>For each incorrect answer, <c:choose><c:when test="${not empty marksEach}">${negativeEach} <c:out value="${negativeEach == '1' ? 'mark' : 'marks'}"/></c:when><c:otherwise><c:out value="${fmt.num(e.negativeFraction)}"/> of the question's marks</c:otherwise></c:choose> will be deducted. An unanswered question earns 0 marks and attracts no penalty.</li></c:when>
                    <c:otherwise><li>There is no negative marking. An unanswered question earns 0 marks.</li></c:otherwise>
                </c:choose>
                <li>Mark only one answer for each question (unless the question asks for more than one) using this examination platform.</li>
                <li>Use of scientific calculators is permitted.</li>
            </ol>
            <c:if test="${not empty customInstructions}"><div class="q-text mb-3"><c:forEach var="ln" items="${customInstructions}"><div><c:out value="${ln}"/></div></c:forEach></div></c:if>
            <ul class="mb-0">
                <li>The timer starts when you press <strong>Start exam</strong> and cannot be paused. It never runs past <c:out value="${fmt.dateTime(e.endAt)}"/>.</li>
                <li>Your answers are saved automatically. If your connection drops, keep the page open &mdash; it will save when the connection returns.</li>
                <li>If the page is closed, sign in again and use <strong>Resume exam</strong>; the original timer continues.</li>
                <li>The exam is submitted automatically when time is over.</li>
                <c:if test="${e.maxFocusLosses > 0}"><li>Switching tabs or windows is recorded. After ${e.maxFocusLosses} switches your exam is submitted automatically.</li></c:if>
                <c:if test="${e.blockCopyPaste}"><li>Copy, paste and right-click are disabled during the exam.</li></c:if>
            </ul>
        </div></div>
        <c:choose>
            <c:when test="${card.state == 'IN_PROGRESS'}">
                <a class="btn btn-warning btn-lg" href="<c:url value='/student/attempts/${card.inProgressAttemptId}'/>">Resume exam</a>
            </c:when>
            <c:when test="${card.canStart}">
                <form method="post" action="<c:url value='/student/exams/${e.id}/start'/>"><t:csrf/>
                    <button class="btn btn-primary btn-lg" type="submit">Start exam</button>
                    <a class="btn btn-outline-secondary btn-lg" href="<c:url value='/student'/>">Back</a></form>
            </c:when>
            <c:otherwise>
                <div class="alert alert-secondary">
                    <c:choose>
                        <c:when test="${card.state == 'UPCOMING'}">This exam opens on <c:out value="${fmt.dateTime(e.startAt)}"/>.</c:when>
                        <c:when test="${card.attemptsUsed >= e.maxAttempts}">You have used all your attempts.</c:when>
                        <c:otherwise>This exam is closed.</c:otherwise>
                    </c:choose>
                </div>
                <a class="btn btn-outline-secondary" href="<c:url value='/student'/>">Back</a>
            </c:otherwise>
        </c:choose>
    </div></div>
</t:layout>
