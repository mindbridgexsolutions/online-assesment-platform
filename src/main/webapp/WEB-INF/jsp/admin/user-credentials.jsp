<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="${headline}" active="users">
    <div class="row justify-content-center">
        <div class="col-lg-7 col-xl-5">
            <div class="card">
                <div class="card-body p-4">
                    <h1 class="h4 page-title"><c:out value="${headline}"/></h1>
                    <p class="text-muted">Share these sign-in details with the user. The password is shown <strong>only now</strong> and cannot be retrieved later; the user will be asked to change it at first sign-in.</p>
                    <dl class="row mb-0">
                        <dt class="col-sm-4">Name</dt><dd class="col-sm-8"><c:out value="${account.fullName}"/></dd>
                        <dt class="col-sm-4">Username</dt><dd class="col-sm-8"><code><c:out value="${account.username}"/></code></dd>
                        <dt class="col-sm-4">Password</dt><dd class="col-sm-8"><code class="fs-5"><c:out value="${password}"/></code></dd>
                    </dl>
                    <div class="mt-4 d-flex gap-2">
                        <a class="btn btn-primary" href="<c:url value='/admin/users'/>">Back to users</a>
                        <button type="button" class="btn btn-outline-secondary" data-print>Print</button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</t:layout>
