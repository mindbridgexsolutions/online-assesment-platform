<%@ page session="false" contentType="text/html;charset=UTF-8" language="java" trimDirectiveWhitespaces="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="t" tagdir="/WEB-INF/tags" %>
<t:layout title="Forgot password" bare="true">
    <div class="login-wrap">
        <div class="login-card px-3">
            <div class="text-center mb-4">
                <img src="<c:url value='/img/favicon.svg'/>" alt="" width="56" height="56" class="mb-2">
                <h1 class="h3 page-title mb-0">MindBridgeX Assess</h1>
                <div class="text-muted">Forgot your password?</div>
            </div>
            <div class="card">
                <div class="card-body p-4">
                    <c:if test="${not empty error}"><div class="alert alert-danger py-2" role="alert"><c:out value="${error}"/></div></c:if>
                    <c:choose>
                        <c:when test="${sent}">
                            <div class="alert alert-success py-2">If this username or e-mail belongs to an active account, your administrator has been asked to reset the password.
                                They will give you a temporary password, which you must change when you sign in.</div>
                            <a class="btn btn-primary w-100" href="<c:url value='/login'/>">Back to sign in</a>
                        </c:when>
                        <c:otherwise>
                            <p class="small text-muted">Enter your username or registered e-mail. Your administrator will reset your password and give you a temporary one.</p>
                            <form method="post" action="<c:url value='/forgot-password'/>">
                                <t:csrf/>
                                <div class="mb-3">
                                    <label class="form-label" for="identifier">Username or e-mail</label>
                                    <input class="form-control form-control-lg" id="identifier" name="identifier" type="text" required autofocus maxlength="160">
                                </div>
                                <button class="btn btn-primary btn-lg w-100" type="submit">Ask for a password reset</button>
                            </form>
                            <div class="text-center mt-3"><a href="<c:url value='/login'/>">Back to sign in</a></div>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>
</t:layout>
