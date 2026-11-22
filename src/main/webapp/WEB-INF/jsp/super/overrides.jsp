<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Approvals" active="overrides">
    <h1 class="h3 page-title mb-1">Approve or reject</h1>
    <p class="text-muted">A rejected exam is taken back to Draft and cannot be published. A rejected question is left out of new exams. Rejecting needs a reason. Every action is saved in the audit log.</p>
    <h2 class="h5 mt-4">Exams <c:if test="${pendingApprovals > 0}"><span class="badge text-bg-warning ms-1">${pendingApprovals} waiting</span></c:if></h2>
    <div class="card"><div class="table-responsive"><table class="table mb-0 align-middle">
        <thead><tr><th>Exam</th><th>Subject</th><th>Status</th><th>Approval</th><th style="width:40%">Reason and action</th></tr></thead><tbody>
        <c:forEach items="${exams}" var="e"><tr>
            <td><c:out value="${e.title}"/></td><td><c:out value="${e.subject.name}"/></td><td><c:out value="${e.status.label}"/></td>
            <td><span class="badge ${e.approvalStatus == 'REJECTED' ? 'text-bg-danger' : (e.approvalStatus == 'PENDING' ? 'text-bg-warning' : 'text-bg-success')}"><c:out value="${e.approvalStatus}"/></span></td>
            <td><form class="d-flex gap-2" method="post" action="<c:url value='/super/overrides/exam/${e.id}'/>"><t:csrf/>
                <input class="form-control form-control-sm" name="reason" placeholder="Reason (needed to reject)" maxlength="300">
                <c:if test="${e.approvalStatus != 'APPROVED'}"><button class="btn btn-sm btn-success" name="approve" value="true" formnovalidate>Approve</button></c:if><c:if test="${e.approvalStatus != 'REJECTED'}"><button class="btn btn-sm btn-outline-danger" name="approve" value="false">Reject</button></c:if></form></td></tr></c:forEach>
        <c:if test="${empty exams}"><tr><td colspan="5" class="text-center text-muted py-3">No exams.</td></tr></c:if>
        </tbody></table></div></div>
    <h2 class="h5 mt-4">Latest questions</h2>
    <div class="card"><div class="table-responsive"><table class="table mb-0 align-middle">
        <thead><tr><th>Question</th><th>Subject</th><th>Approval</th><th style="width:40%">Reason and action</th></tr></thead><tbody>
        <c:forEach items="${questions}" var="q"><tr>
            <td><c:out value="${q.text}"/></td><td><c:out value="${q.subject.name}"/></td>
            <td><span class="badge ${q.approvalStatus == 'REJECTED' ? 'text-bg-danger' : (q.approvalStatus == 'PENDING' ? 'text-bg-warning' : 'text-bg-success')}"><c:out value="${q.approvalStatus}"/></span></td>
            <td><form class="d-flex gap-2" method="post" action="<c:url value='/super/overrides/question/${q.id}'/>"><t:csrf/>
                <input class="form-control form-control-sm" name="reason" placeholder="Reason (needed to reject)" maxlength="300">
                <c:if test="${q.approvalStatus != 'APPROVED'}"><button class="btn btn-sm btn-success" name="approve" value="true" formnovalidate>Approve</button></c:if><c:if test="${q.approvalStatus != 'REJECTED'}"><button class="btn btn-sm btn-outline-danger" name="approve" value="false">Reject</button></c:if></form></td></tr></c:forEach>
        </tbody></table></div></div>
</t:layout>
