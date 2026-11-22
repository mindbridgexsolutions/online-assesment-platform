<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="${batch.name}" active="batches">
    <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 gap-2">
        <div>
            <h1 class="h3 page-title mb-0"><c:out value="${batch.name}"/> <small class="text-muted fs-6"><code><c:out value="${batch.code}"/></code></small></h1>
            <div class="text-muted"><c:out value="${batch.description}"/></div>
        </div>
        <div class="d-flex gap-2">
            <a class="btn btn-outline-secondary" href="<c:url value='/admin/batches/${batch.id}/edit'/>">Edit batch</a>
            <a class="btn btn-outline-primary" href="<c:url value='/admin/users/import'/>">Bulk upload students</a>
        </div>
    </div>
    <div class="row g-3">
        <div class="col-lg-4">
            <div class="card"><div class="card-header">Add students</div><div class="card-body">
                <form method="post" action="<c:url value='/admin/batches/${batch.id}/students'/>">
                    <t:csrf/>
                    <label class="form-label small" for="identifiers">Usernames, e-mails or roll numbers</label>
                    <textarea class="form-control mb-2" id="identifiers" name="identifiers" rows="6" required placeholder="One per line or comma separated"></textarea>
                    <button class="btn btn-primary w-100" type="submit">Add to batch</button>
                </form>
            </div></div>
        </div>
        <div class="col-lg-8">
            <div class="card">
                <div class="card-header">Students in this batch (<c:out value="${members.size()}"/>)</div>
                <div class="table-responsive"><table class="table table-sm table-hover mb-0">
                    <thead><tr><th>Name</th><th>Username</th><th>Roll no</th><th>E-mail</th><th></th></tr></thead>
                    <tbody>
                    <c:forEach items="${members}" var="m">
                        <tr>
                            <td><c:out value="${m.fullName}"/></td><td><c:out value="${m.username}"/></td>
                            <td><c:out value="${m.rollNo}"/></td><td><c:out value="${m.email}"/></td>
                            <td class="text-end">
                                <form method="post" action="<c:url value='/admin/batches/${batch.id}/students/${m.id}/remove'/>">
                                    <t:csrf/><button class="btn btn-sm btn-outline-danger" type="submit" data-confirm="Remove this student from the batch?">Remove</button>
                                </form>
                            </td>
                        </tr>
                    </c:forEach>
                    <c:if test="${empty members}"><tr><td colspan="5" class="text-center text-muted py-4">No students yet.</td></tr></c:if>
                    </tbody>
                </table></div>
            </div>
        </div>
    </div>
</t:layout>
