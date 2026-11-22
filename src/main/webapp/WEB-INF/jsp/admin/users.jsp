<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Users" active="users">
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <h1 class="h3 page-title mb-0">Users</h1>
        <div class="d-flex gap-2">
            <a class="btn btn-outline-secondary" href="<c:url value='/admin/users/reset-requests'/>">Password reset requests</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/users/import'/>">Bulk upload students / teachers</a>
            <a class="btn btn-primary" href="<c:url value='/admin/users/new'/>">Add user</a>
        </div>
    </div>

    <form class="card card-body mb-3" method="get" action="<c:url value='/admin/users'/>">
        <div class="row g-2 align-items-end">
            <div class="col-md-5">
                <label class="form-label small mb-1" for="q">Search</label>
                <input class="form-control" id="q" name="q" value="<c:out value='${q}'/>" placeholder="Name, username, e-mail or roll no">
            </div>
            <div class="col-md-3">
                <label class="form-label small mb-1" for="role">Role</label>
                <select class="form-select" id="role" name="role" data-autosubmit>
                    <option value="">All roles</option>
                    <c:forEach items="${roleTypes}" var="r">
                        <option value="${r}" ${r == role ? 'selected' : ''}><c:out value="${r.label}"/></option>
                    </c:forEach>
                </select>
            </div>
            <div class="col-md-2">
                <label class="form-label small mb-1" for="active">Status</label>
                <select class="form-select" id="active" name="active" data-autosubmit>
                    <option value="">All</option>
                    <option value="true" ${active == true ? 'selected' : ''}>Active</option>
                    <option value="false" ${active == false ? 'selected' : ''}>Inactive</option>
                </select>
            </div>
            <div class="col-md-2 d-grid"><button class="btn btn-outline-primary" type="submit">Filter</button></div>
        </div>
    </form>

    <div class="card">
        <div class="table-responsive">
            <table class="table table-hover mb-0">
                <thead>
                <tr><th>Name</th><th>Username</th><th>E-mail</th><th>Roll no</th><th>Roles</th><th>Status</th><th>Last sign-in</th><th class="text-end">Actions</th></tr>
                </thead>
                <tbody>
                <c:forEach items="${page.content}" var="u">
                    <tr>
                        <td class="fw-semibold"><c:out value="${u.fullName}"/></td>
                        <td><c:out value="${u.username}"/></td>
                        <td><c:out value="${u.email}"/></td>
                        <td><c:out value="${u.rollNo}"/></td>
                        <td>
                            <c:forEach items="${u.roles}" var="r"><span class="badge text-bg-secondary me-1"><c:out value="${r.label}"/></span></c:forEach>
                        </td>
                        <td>
                            <c:choose>
                                <c:when test="${not u.active}"><span class="badge text-bg-danger">Inactive</span></c:when>
                                <c:when test="${u.locked}"><span class="badge text-bg-warning">Locked</span></c:when>
                                <c:otherwise><span class="badge text-bg-success">Active</span></c:otherwise>
                            </c:choose>
                            <c:if test="${u.mustChangePassword}"><span class="badge text-bg-light border">Password pending</span></c:if>
                        </td>
                        <td class="small text-muted"><c:out value="${fmt.dateTime(u.lastLoginAt)}"/></td>
                        <td class="text-end text-nowrap">
                            <a class="btn btn-sm btn-outline-primary" href="<c:url value='/admin/users/${u.id}/edit'/>">Edit</a>
                            <div class="btn-group">
                                <button type="button" class="btn btn-sm btn-outline-secondary dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">More</button>
                                <ul class="dropdown-menu dropdown-menu-end">
                                    <li>
                                        <form method="post" action="<c:url value='/admin/users/${u.id}/reset-password'/>">
                                            <t:csrf/>
                                            <button class="dropdown-item" type="submit" data-confirm="Reset the password of <c:out value='${u.username}'/>? They will be signed out everywhere.">Reset password</button>
                                        </form>
                                    </li>
                                    <c:if test="${u.locked}">
                                        <li>
                                            <form method="post" action="<c:url value='/admin/users/${u.id}/unlock'/>"><t:csrf/>
                                                <button class="dropdown-item" type="submit">Unlock account</button></form>
                                        </li>
                                    </c:if>
                                    <li>
                                        <form method="post" action="<c:url value='/admin/users/${u.id}/toggle-active'/>">
                                            <t:csrf/>
                                            <input type="hidden" name="active" value="${not u.active}">
                                            <button class="dropdown-item ${u.active ? 'text-danger' : ''}" type="submit"
                                                    data-confirm="${u.active ? 'Deactivate this user? They are signed out immediately.' : 'Activate this user?'}">
                                                ${u.active ? 'Deactivate' : 'Activate'}
                                            </button>
                                        </form>
                                    </li>
                                </ul>
                            </div>
                        </td>
                    </tr>
                </c:forEach>
                <c:if test="${empty page.content}">
                    <tr><td colspan="8" class="text-center text-muted py-4">No users match the filter.</td></tr>
                </c:if>
                </tbody>
            </table>
        </div>
    </div>
    <t:pager page="${page}" path="/admin/users"/>
</t:layout>
