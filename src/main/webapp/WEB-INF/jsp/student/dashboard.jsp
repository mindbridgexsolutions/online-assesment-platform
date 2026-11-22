<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="My exams" active="dashboard">
    <h1 class="h3 page-title mb-3">My exams</h1>
    <c:set var="shown" value="0"/>
    <div class="row g-3">
        <c:forEach items="${cards}" var="c">
            <div class="col-md-6 col-xl-4">
                <div class="card h-100"><div class="card-body d-flex flex-column">
                    <div class="d-flex justify-content-between align-items-start mb-2">
                        <h2 class="h5 mb-0"><c:out value="${c.exam.title}"/></h2>
                        <c:choose>
                            <c:when test="${c.state == 'IN_PROGRESS'}"><span class="badge text-bg-warning">In progress</span></c:when>
                            <c:when test="${c.state == 'OPEN'}"><span class="badge text-bg-success">Open</span></c:when>
                            <c:when test="${c.state == 'UPCOMING'}"><span class="badge text-bg-info">Upcoming</span></c:when>
                            <c:otherwise><span class="badge text-bg-secondary">Closed</span></c:otherwise>
                        </c:choose>
                    </div>
                    <div class="text-muted small mb-2"><c:out value="${c.exam.subject.name}"/></div>
                    <ul class="list-unstyled small mb-3">
                        <li>Opens: <c:out value="${fmt.dateTime(c.exam.startAt)}"/></li>
                        <li>Closes: <c:out value="${fmt.dateTime(c.exam.endAt)}"/></li>
                        <li>Duration: ${c.exam.durationMinutes} minutes</li>
                        <li>Attempts: ${c.attemptsUsed} of ${c.exam.maxAttempts}</li>
                    </ul>
                    <div class="mt-auto d-flex gap-2">
                        <c:choose>
                            <c:when test="${c.state == 'IN_PROGRESS'}">
                                <a class="btn btn-warning" href="<c:url value='/student/attempts/${c.inProgressAttemptId}'/>">Resume exam</a>
                            </c:when>
                            <c:when test="${c.canStart}">
                                <a class="btn btn-primary" href="<c:url value='/student/exams/${c.exam.id}'/>">Instructions &amp; start</a>
                            </c:when>
                            <c:otherwise>
                                <a class="btn btn-outline-secondary" href="<c:url value='/student/exams/${c.exam.id}'/>">Details</a>
                            </c:otherwise>
                        </c:choose>
                        <c:if test="${not empty c.lastResult and c.lastResult.resultVisible}">
                            <a class="btn btn-outline-primary" href="<c:url value='/student/results/${c.lastResult.attemptId}'/>">Result</a>
                        </c:if>
                    </div>
                </div></div>
            </div>
        </c:forEach>
    </div>
    <c:if test="${empty cards}"><div class="card"><div class="card-body text-center text-muted py-5">No exams have been assigned to you yet.</div></div></c:if>
</t:layout>
