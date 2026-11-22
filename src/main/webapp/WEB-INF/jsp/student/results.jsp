<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="My results" active="results">
    <h1 class="h3 page-title mb-3">My results</h1>
    <div class="card"><div class="table-responsive">
        <table class="table align-middle mb-0">
            <thead><tr><th>Exam</th><th>Attempt</th><th>Submitted</th><th>Score</th><th>Result</th><th></th></tr></thead>
            <tbody>
            <c:forEach items="${rows}" var="r">
                <tr>
                    <td><c:out value="${r.examTitle}"/></td>
                    <td>#${r.attemptNo}</td>
                    <td class="small"><c:out value="${fmt.dateTime(r.submittedAt)}"/></td>
                    <c:choose>
                        <c:when test="${r.resultVisible}">
                            <td><c:out value="${fmt.num(r.score)}"/> / <c:out value="${fmt.num(r.maxScore)}"/> (<c:out value="${fmt.pct(r.percentage)}"/>)</td>
                            <td><c:choose>
                                <c:when test="${r.pendingManual > 0}"><span class="badge text-bg-warning">Partly graded</span></c:when>
                                <c:when test="${r.passed}"><span class="badge text-bg-success">Passed</span></c:when>
                                <c:otherwise><span class="badge text-bg-danger">Not passed</span></c:otherwise></c:choose></td>
                            <td class="text-end"><a class="btn btn-sm btn-outline-primary" href="<c:url value='/student/results/${r.attemptId}'/>">View</a></td>
                        </c:when>
                        <c:otherwise>
                            <td colspan="2" class="text-muted small">
                                <c:choose>
                                    <c:when test="${r.resultPolicy == 'AFTER_WINDOW'}">Available after <c:out value="${fmt.dateTime(r.examEndAt)}"/></c:when>
                                    <c:when test="${r.resultPolicy == 'SCHEDULED'}">Your exam has been recorded. Results will be published on <c:out value="${fmt.dateTime(r.resultsPublishAt)}"/></c:when>
                                    <c:when test="${r.resultPolicy == 'MANUAL'}">Your teacher will release results</c:when>
                                    <c:otherwise>Being graded</c:otherwise>
                                </c:choose></td><td></td>
                        </c:otherwise>
                    </c:choose>
                </tr>
            </c:forEach>
            <c:if test="${empty rows}"><tr><td colspan="6" class="text-center text-muted py-4">You have not completed any exam yet.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
</t:layout>
