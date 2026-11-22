<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Branch" active="branches">
    <h1 class="h3 page-title mb-1"><c:out value="${branch.name}"/> <code><c:out value="${branch.code}"/></code></h1>
    <p class="text-muted">A subject, batch or teacher belongs to one branch. Adding it here moves it from any other branch.</p>
    <div class="row g-3">
    <div class="col-lg-4"><div class="card h-100"><div class="card-header">Subjects</div>
        <ul class="list-group list-group-flush">
        <c:forEach items="${subjectIn}" var="i"><li class="list-group-item d-flex justify-content-between align-items-center"><span><c:out value="${i.label}"/></span>
            <form method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/><input type="hidden" name="kind" value="subject"><input type="hidden" name="itemId" value="${i.id}"><input type="hidden" name="add" value="false">
            <button class="btn btn-sm btn-outline-danger" type="submit">Remove</button></form></li></c:forEach>
        <c:if test="${empty subjectIn}"><li class="list-group-item text-muted">None yet</li></c:if>
        </ul>
        <div class="card-footer"><form class="d-flex gap-2" method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/>
            <input type="hidden" name="kind" value="subject"><input type="hidden" name="add" value="true">
            <select class="form-select form-select-sm" name="itemId" required><option value="">Add...</option>
            <c:forEach items="${subjectOut}" var="i"><option value="${i.id}"><c:out value="${i.label}"/></option></c:forEach></select>
            <button class="btn btn-sm btn-primary" type="submit">Add</button></form></div>
    </div></div>
    <div class="col-lg-4"><div class="card h-100"><div class="card-header">Batches</div>
        <ul class="list-group list-group-flush">
        <c:forEach items="${batchIn}" var="i"><li class="list-group-item d-flex justify-content-between align-items-center"><span><c:out value="${i.label}"/></span>
            <form method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/><input type="hidden" name="kind" value="batch"><input type="hidden" name="itemId" value="${i.id}"><input type="hidden" name="add" value="false">
            <button class="btn btn-sm btn-outline-danger" type="submit">Remove</button></form></li></c:forEach>
        <c:if test="${empty batchIn}"><li class="list-group-item text-muted">None yet</li></c:if>
        </ul>
        <div class="card-footer"><form class="d-flex gap-2" method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/>
            <input type="hidden" name="kind" value="batch"><input type="hidden" name="add" value="true">
            <select class="form-select form-select-sm" name="itemId" required><option value="">Add...</option>
            <c:forEach items="${batchOut}" var="i"><option value="${i.id}"><c:out value="${i.label}"/></option></c:forEach></select>
            <button class="btn btn-sm btn-primary" type="submit">Add</button></form></div>
    </div></div>
    <div class="col-lg-4"><div class="card h-100"><div class="card-header">Teachers</div>
        <ul class="list-group list-group-flush">
        <c:forEach items="${teacherIn}" var="i"><li class="list-group-item d-flex justify-content-between align-items-center"><span><c:out value="${i.label}"/></span>
            <form method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/><input type="hidden" name="kind" value="teacher"><input type="hidden" name="itemId" value="${i.id}"><input type="hidden" name="add" value="false">
            <button class="btn btn-sm btn-outline-danger" type="submit">Remove</button></form></li></c:forEach>
        <c:if test="${empty teacherIn}"><li class="list-group-item text-muted">None yet</li></c:if>
        </ul>
        <div class="card-footer"><form class="d-flex gap-2" method="post" action="<c:url value='/super/branches/${branch.id}/assign'/>"><t:csrf/>
            <input type="hidden" name="kind" value="teacher"><input type="hidden" name="add" value="true">
            <select class="form-select form-select-sm" name="itemId" required><option value="">Add...</option>
            <c:forEach items="${teacherOut}" var="i"><option value="${i.id}"><c:out value="${i.label}"/></option></c:forEach></select>
            <button class="btn btn-sm btn-primary" type="submit">Add</button></form></div>
    </div></div>
    </div>
    <a class="btn btn-link mt-3" href="<c:url value='/super/branches'/>">Back to branches</a>
</t:layout>
