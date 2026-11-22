<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="${editing ? 'Edit user' : 'Add user'}" active="users">
    <div class="row justify-content-center">
        <div class="col-lg-8 col-xl-6">
            <h1 class="h3 page-title mb-3"><c:out value="${editing ? 'Edit user' : 'Add user'}"/></h1>
            <c:if test="${not empty errors}">
                <div class="alert alert-danger"><ul class="mb-0"><c:forEach items="${errors}" var="e"><li><c:out value="${e}"/></li></c:forEach></ul></div>
            </c:if>
            <div class="card"><div class="card-body p-4">
                <c:choose>
                    <c:when test="${editing}"><c:url var="action" value="/admin/users/${form.id}"/></c:when>
                    <c:otherwise><c:url var="action" value="/admin/users"/></c:otherwise>
                </c:choose>
                <form method="post" action="${action}">
                    <t:csrf/>
                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label" for="username">Username</label>
                            <input class="form-control" id="username" name="username" value="<c:out value='${form.username}'/>"
                                   required maxlength="60" ${editing ? 'readonly' : ''} autocomplete="off">
                        </div>
                        <div class="col-md-6">
                            <label class="form-label" for="fullName">Full name</label>
                            <input class="form-control" id="fullName" name="fullName" value="<c:out value='${form.fullName}'/>" required maxlength="120">
                        </div>
                        <div class="col-md-6">
                            <label class="form-label" for="email">E-mail</label>
                            <input class="form-control" id="email" name="email" type="email" value="<c:out value='${form.email}'/>" required maxlength="160">
                        </div>
                        <div class="col-md-3">
                            <label class="form-label" for="rollNo">Roll no</label>
                            <input class="form-control" id="rollNo" name="rollNo" value="<c:out value='${form.rollNo}'/>" maxlength="40">
                        </div>
                        <div class="col-md-3">
                            <label class="form-label" for="phone">Phone</label>
                            <input class="form-control" id="phone" name="phone" value="<c:out value='${form.phone}'/>" maxlength="30">
                        </div>
                        <div class="col-12">
                            <label class="form-label d-block">Roles</label>
                            <c:forEach items="${roleTypes}" var="r">
                                <div class="form-check form-check-inline">
                                    <input class="form-check-input" type="checkbox" name="roles" id="role_${r}" value="${r}" ${form.roles.contains(r) ? 'checked' : ''}>
                                    <label class="form-check-label" for="role_${r}"><c:out value="${r.label}"/></label>
                                </div>
                            </c:forEach>
                        </div>
                        <c:if test="${not editing}">
                            <div class="col-12">
                                <label class="form-label" for="password">Initial password <span class="text-muted">(optional)</span></label>
                                <input class="form-control" id="password" name="password" type="text" maxlength="72" autocomplete="off"
                                       placeholder="Leave blank to generate a random one">
                                <div class="form-text">The user must change it at first sign-in.</div>
                            </div>
                        </c:if>
                        <div class="col-12">
                            <div class="form-check form-switch">
                                <input class="form-check-input" type="checkbox" role="switch" id="active" name="active" value="true" ${form.active ? 'checked' : ''}>
                                <label class="form-check-label" for="active">Account is active</label>
                                <input type="hidden" name="_active" value="on">
                            </div>
                        </div>
                    </div>
                    <div class="mt-4 d-flex gap-2">
                        <button class="btn btn-primary" type="submit">Save</button>
                        <a class="btn btn-outline-secondary" href="<c:url value='/admin/users'/>">Cancel</a>
                    </div>
                </form>
            </div></div>
        </div>
    </div>
</t:layout>
