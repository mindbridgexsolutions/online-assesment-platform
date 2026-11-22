<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="isEdit" value="${not empty form.id}"/>
<t:layout title="${isEdit ? 'Edit batch' : 'New batch'}" active="batches">
    <div class="row justify-content-center"><div class="col-lg-7 col-xl-5">
        <h1 class="h3 page-title mb-3"><c:out value="${isEdit ? 'Edit batch' : 'New batch'}"/></h1>
        <c:if test="${not empty errors}"><div class="alert alert-danger"><ul class="mb-0"><c:forEach items="${errors}" var="e"><li><c:out value="${e}"/></li></c:forEach></ul></div></c:if>
        <div class="card"><div class="card-body p-4">
            <c:choose>
                <c:when test="${isEdit}"><c:url var="action" value="/admin/batches/${form.id}"/></c:when>
                <c:otherwise><c:url var="action" value="/admin/batches"/></c:otherwise>
            </c:choose>
            <form method="post" action="${action}">
                <t:csrf/>
                <div class="mb-3"><label class="form-label" for="name">Name</label>
                    <input class="form-control" id="name" name="name" value="<c:out value='${form.name}'/>" required maxlength="120"></div>
                <div class="mb-3"><label class="form-label" for="code">Code</label>
                    <input class="form-control" id="code" name="code" value="<c:out value='${form.code}'/>" required maxlength="40" pattern="[A-Za-z0-9._-]{2,40}">
                    <div class="form-text">Short unique code, e.g. <code>FY-BSC-2026</code>. Used in bulk upload files.</div></div>
                <div class="mb-3"><label class="form-label" for="description">Description</label>
                    <textarea class="form-control" id="description" name="description" rows="2" maxlength="500"><c:out value="${form.description}"/></textarea></div>
                <div class="form-check form-switch mb-4">
                    <input class="form-check-input" type="checkbox" role="switch" id="active" name="active" value="true" ${form.active ? 'checked' : ''}>
                    <label class="form-check-label" for="active">Active</label>
                    <input type="hidden" name="_active" value="on"></div>
                <button class="btn btn-primary" type="submit">Save</button>
                <a class="btn btn-outline-secondary" href="<c:url value='/admin/batches'/>">Cancel</a>
            </form>
        </div></div>
    </div></div>
</t:layout>
