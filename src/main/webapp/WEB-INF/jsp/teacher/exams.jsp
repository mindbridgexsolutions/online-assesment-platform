<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Exams" active="exams">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h3 page-title mb-0">Exams</h1>
        <a class="btn btn-primary" href="<c:url value='/teacher/exams/new'/>">Create exam</a>
    </div>
    <div class="card"><div class="table-responsive">
        <table class="table table-hover align-middle mb-0">
            <thead><tr><th>Title</th><th>Subject</th><th>Window</th><th>Duration</th><th>Status</th><th></th></tr></thead>
            <tbody>
            <c:forEach items="${page.content}" var="e">
                <tr>
                    <td><a class="fw-semibold text-decoration-none" href="<c:url value='/teacher/exams/${e.id}'/>"><c:out value="${e.title}"/></a></td>
                    <td><c:out value="${e.subject.name}"/></td>
                    <td class="small"><c:out value="${fmt.dateTime(e.startAt)}"/><br><c:out value="${fmt.dateTime(e.endAt)}"/></td>
                    <td>${e.durationMinutes} min</td>
                    <td><span class="badge text-bg-${e.status == 'PUBLISHED' ? 'success' : (e.status == 'DRAFT' ? 'secondary' : 'dark')}"><c:out value="${e.status.label}"/></span></td>
                    <td class="text-end">
                        <a class="btn btn-sm btn-outline-primary" href="<c:url value='/teacher/exams/${e.id}'/>">Manage</a>
                        <c:if test="${e.resultPolicy == 'MANUAL' and e.status != 'DRAFT'}"><form class="d-inline" method="post" action="<c:url value='/teacher/exams/${e.id}/release'/>"><t:csrf/><input type="hidden" name="released" value="${!e.resultsReleased}"><input type="hidden" name="back" value="list">
                            <button class="btn btn-sm ${e.resultsReleased ? 'btn-success' : 'btn-warning'}" type="submit" data-confirm="${e.resultsReleased ? 'Hide the results from students again?' : 'Show results to all students of this exam now?'}">${e.resultsReleased ? 'Results released &#10003;' : 'Release results'}</button></form></c:if>
                        <a class="btn btn-sm btn-outline-secondary" href="<c:url value='/reports/exams/${e.id}'/>">Results</a>
                    </td>
                </tr>
            </c:forEach>
            <c:if test="${empty page.content}"><tr><td colspan="6" class="text-center text-muted py-4">No exams yet.</td></tr></c:if>
            </tbody>
        </table>
    </div></div>
    <t:pager page="${page}" path="/teacher/exams"/>
</t:layout>
