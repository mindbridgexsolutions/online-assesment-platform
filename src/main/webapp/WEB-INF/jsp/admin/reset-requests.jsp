<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Password reset requests" active="users">
    <div class="d-flex justify-content-between align-items-center mb-3">
        <h1 class="h3 page-title mb-0">Password reset requests</h1>
        <a class="btn btn-outline-secondary" href="<c:url value='/admin/users'/>">Back to users</a>
    </div>
    <div class="card"><div class="card-body p-0">
        <c:choose>
            <c:when test="${empty requests}"><p class="text-muted p-3 mb-0">No pending requests. People can ask for a reset with &ldquo;Forgot password&rdquo; on the sign-in page.</p></c:when>
            <c:otherwise>
                <table class="table table-hover align-middle mb-0">
                    <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Asked at</th><th></th></tr></thead>
                    <tbody>
                    <c:forEach items="${requests}" var="r">
                        <tr>
                            <td><c:out value="${r.fullName}"/></td><td><c:out value="${r.username}"/></td><td><c:out value="${r.roles}"/></td>
                            <td><c:out value="${fmt.dateTime(r.requestedAt)}"/></td>
                            <td class="text-end"><form method="post" action="<c:url value='/admin/users/${r.userId}/reset-password'/>" class="d-inline"><t:csrf/>
                                <button class="btn btn-sm btn-primary" type="submit">Reset password</button></form></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </c:otherwise>
        </c:choose>
    </div></div>
    <p class="small text-muted mt-2">Resetting creates a temporary password, shown once on the next page. Give it to the person; they must change it at first sign-in.</p>
</t:layout>
