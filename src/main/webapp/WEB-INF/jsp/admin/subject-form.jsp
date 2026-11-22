<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<c:set var="isEdit" value="${not empty form.id}"/>
<t:layout title="${isEdit ? 'Edit subject' : 'New subject'}" active="subjects">
    <div class="row justify-content-center"><div class="col-lg-7 col-xl-5">
        <h1 class="h3 page-title mb-3"><c:out value="${isEdit ? 'Edit subject' : 'New subject'}"/></h1>
        <c:if test="${not empty errors}"><div class="alert alert-danger"><ul class="mb-0"><c:forEach items="${errors}" var="e"><li><c:out value="${e}"/></li></c:forEach></ul></div></c:if>
        <div class="card"><div class="card-body p-4">
            <c:choose>
                <c:when test="${isEdit}"><c:url var="action" value="/admin/subjects/${form.id}"/></c:when>
                <c:otherwise><c:url var="action" value="/admin/subjects"/></c:otherwise>
            </c:choose>
            <form method="post" action="${action}">
                <t:csrf/>
                <div class="mb-3"><label class="form-label" for="name">Name</label>
                    <input class="form-control" id="name" name="name" value="<c:out value='${form.name}'/>" required maxlength="120"></div>
                <div class="mb-3"><label class="form-label" for="code">Code</label>
                    <input class="form-control" id="code" name="code" value="<c:out value='${form.code}'/>" required maxlength="40"></div>
                <div class="mb-3">
                    <label class="form-label d-block">Teachers who can manage this subject's question bank and exams</label>
                    <c:if test="${not empty teachers}">
                        <div class="d-flex flex-wrap gap-2 align-items-center mb-2">
                            <input type="search" class="form-control form-control-sm" style="max-width:300px" placeholder="Search teachers by name or username" data-filter-list="#teacherList" autocomplete="off" aria-label="Search teachers">
                            <button type="button" class="btn btn-sm btn-outline-primary" data-check-all="#teacherList">Select all</button>
                            <button type="button" class="btn btn-sm btn-outline-secondary" data-check-none="#teacherList">Clear</button>
                            <span class="small text-muted" data-check-count="#teacherList"></span>
                        </div>
                    </c:if>
                    <div id="teacherList" data-check-list class="border rounded p-2" style="max-height:340px;overflow:auto">
                    <c:forEach items="${teachers}" var="t">
                        <div class="form-check" data-search="<c:out value='${t.fullName} ${t.username}'/>">
                            <input class="form-check-input" type="checkbox" name="teacherIds" id="t_${t.id}" value="${t.id}" ${form.teacherIds.contains(t.id) ? 'checked' : ''}>
                            <label class="form-check-label" for="t_${t.id}"><c:out value="${t.fullName}"/> <span class="text-muted small">(<c:out value="${t.username}"/>)</span></label>
                        </div>
                    </c:forEach>
                    <div class="text-muted small d-none" data-no-match>No teacher matches your search.</div>
                    </div>
                    <c:if test="${empty teachers}"><div class="text-muted small">No active teachers yet. Create users with the Teacher role first.</div></c:if>
                </div>
                <div class="form-check form-switch mb-4">
                    <input class="form-check-input" type="checkbox" role="switch" id="active" name="active" value="true" ${form.active ? 'checked' : ''}>
                    <label class="form-check-label" for="active">Active</label>
                    <input type="hidden" name="_active" value="on"></div>
                <button class="btn btn-primary" type="submit">Save</button>
                <a class="btn btn-outline-secondary" href="<c:url value='/admin/subjects'/>">Cancel</a>
            </form>
        </div></div>
    </div></div>
</t:layout>
