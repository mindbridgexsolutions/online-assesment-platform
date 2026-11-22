<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Exam approvals" active="hod">
    <h1 class="h3 page-title mb-1">Exams waiting for approval</h1>
    <p class="text-muted">Open an exam to check its questions and CO coverage, then approve or reject it. Faculty can publish only approved exams.</p>
    <div class="card"><div class="table-responsive"><table class="table mb-0 align-middle">
        <thead><tr><th>Exam</th><th>Course</th><th>Type</th><th>Status</th><th style="width:38%">Decision</th></tr></thead><tbody>
        <c:forEach items="${exams}" var="e"><tr>
            <td><a href="<c:url value='/teacher/exams/${e.id}'/>"><c:out value="${e.title}"/></a></td><td><c:out value="${e.subject.name}"/></td><td><c:out value="${e.examType.label}"/></td>
            <td><span class="badge text-bg-${e.approvalStatus == 'PENDING' ? 'warning' : 'danger'}">${e.approvalStatus == 'PENDING' ? 'Waiting' : 'Rejected'}</span></td>
            <td><form class="d-flex gap-2" method="post" action="<c:url value='/hod/exams/${e.id}'/>"><t:csrf/>
                <input class="form-control form-control-sm" name="reason" placeholder="Comment (needed to reject)">
                <button class="btn btn-sm btn-success" name="approve" value="true">Approve</button>
                <button class="btn btn-sm btn-outline-danger" name="approve" value="false">Reject</button></form></td></tr></c:forEach>
        <c:if test="${empty exams}"><tr><td colspan="5" class="text-center text-muted py-4">Nothing is waiting for approval.</td></tr></c:if>
        </tbody></table></div></div>
</t:layout>
