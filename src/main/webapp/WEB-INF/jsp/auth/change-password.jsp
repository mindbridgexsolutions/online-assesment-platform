<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Change password">
    <div class="row justify-content-center">
        <div class="col-md-8 col-lg-5">
            <h1 class="h3 page-title mb-3">Change password</h1>
            <c:if test="${currentUser.mustChangePassword}">
                <div class="alert alert-warning">You must choose a new password before continuing.</div>
            </c:if>
            <c:if test="${not empty error}">
                <div class="alert alert-danger" role="alert"><c:out value="${error}"/></div>
            </c:if>
            <div class="card">
                <div class="card-body p-4">
                    <form method="post" action="<c:url value='/auth/change-password'/>">
                        <t:csrf/>
                        <div class="mb-3">
                            <label class="form-label" for="currentPassword">Current password</label>
                            <input class="form-control" id="currentPassword" name="currentPassword" type="password"
                                   required autocomplete="current-password" maxlength="72">
                        </div>
                        <div class="mb-3">
                            <label class="form-label" for="newPassword">New password</label>
                            <input class="form-control" id="newPassword" name="newPassword" type="password"
                                   required minlength="8" maxlength="72" autocomplete="new-password">
                            <div class="form-text">At least 8 characters, with a letter and a digit.</div>
                        </div>
                        <div class="mb-4">
                            <label class="form-label" for="confirmPassword">Confirm new password</label>
                            <input class="form-control" id="confirmPassword" name="confirmPassword" type="password"
                                   required minlength="8" maxlength="72" autocomplete="new-password">
                        </div>
                        <button class="btn btn-primary" type="submit">Update password</button>
                        <c:if test="${not currentUser.mustChangePassword}">
                            <a class="btn btn-link" href="<c:url value='/'/>">Cancel</a>
                        </c:if>
                    </form>
                </div>
            </div>
            <p class="text-muted small mt-2">You will be signed out on all devices after changing your password.</p>
        </div>
    </div>
</t:layout>
