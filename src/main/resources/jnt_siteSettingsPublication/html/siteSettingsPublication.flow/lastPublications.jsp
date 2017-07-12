<%@ page import="java.util.Map" %>
<%@ page import="java.util.Date" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="fnt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>

<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>

<template:addResources type="javascript"
                       resources="jquery.min.js,jquery-ui.min.js,admin-bootstrap.js,jquery.tablesorter.js,jquery.blockUI.js,workInProgress.js,bootbox.min.js"/>
<template:addResources type="css" resources="admin-bootstrap.css"/>
<template:addResources type="css" resources="jquery-ui.smoothness.css,jquery-ui.smoothness-jahia.css,tablecloth.css"/>

<spring:eval var="SUCCESS" expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).SUCCESS"/>
<spring:eval var="ERROR" expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).ERROR"/>
<spring:eval var="NOTHING_TO_PUBLISH" expression="T(org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob).NOTHING_TO_PUBLISH"/>

<h2><fmt:message key="siteSettingsPublication.publicationJobs.title"/></h2>


<c:set var="totalCount" value="${fn:length(publicationJobs)}"/>

<template:addResources type="inlinejavascript">
    <script type="text/javascript">
        <c:if test="${totalCount > 0}">

        $(document).ready(function () {
            $("#tablePublicationJobs").tablesorter({
                sortList: [[0, 0]],
                headers: {4: {sorter: false}}
            });
        });

        </c:if>

    </script>
</template:addResources>

<div>
    <div>
        <form action="${flowExecutionUrl}" method="post" style="display: inline;">
            <input type="hidden" name="_eventId" value="back"/>
            <button class="btn" type="submit" name="mybutton">
                <i class="icon-back"></i>
                &nbsp;<fmt:message key="siteSettingsPublication.publicationJobs.back"/>
            </button>
        </form>
    </div>

    <div>
        <table class="table table-bordered table-striped table-hover" id="tablePublicationJobs">
            <thead>
            <tr>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.startDate"/>
                </th>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.endDate"/>
                </th>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.path"/>
                </th>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.language"/>
                </th>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.status"/>
                </th>
                <th>
                    <fmt:message key="siteSettingsPublication.publicationJobs.details"/>
                </th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${totalCount > 0}">
                    <c:forEach items="${publicationJobs}" var="publicationJob">
                        <c:set var="jobDetail" value="${publicationJob.jobDataMap}"/>
                        <c:if test="${fn:startsWith(jobDetail['path'], renderContext.site.path)}">
                            <jsp:useBean id="dateValue" class="java.util.Date"/>
                            <jsp:setProperty name="dateValue" property="time" value="${jobDetail['begin']}"/>
                            <tr>
                                <td>
                                    <fmt:formatDate value="${dateValue}" pattern="MM/dd/yyyy HH:mm:ss"/>
                                </td>
                                <jsp:setProperty name="dateValue" property="time" value="${jobDetail['end']}"/>
                                <td>
                                    <fmt:formatDate value="${dateValue}" pattern="MM/dd/yyyy HH:mm:ss"/>
                                </td>
                                <td>
                                        ${jobDetail['path']}
                                </td>
                                <td>
                                        ${jobDetail['language']}
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${jobDetail['result'] == ERROR}">
                                            <fmt:message key="siteSettingsPublication.publicationJobs.error"/>
                                        </c:when>
                                        <c:when test="${jobDetail['result'] == SUCCESS}">
                                            <fmt:message key="siteSettingsPublication.publicationJobs.success"/>
                                        </c:when>
                                        <c:when test="${jobDetail['result'] == NOTHING_TO_PUBLISH}">
                                            <fmt:message key="siteSettingsPublication.publicationJobs.nothingToPublish"/>
                                        </c:when>
                                    </c:choose>
                                </td>
                                <td>
                                    detail
                                </td>
                            </tr>
                        </c:if>
                    </c:forEach>
                </c:when>
                <c:otherwise>
                    <tr>
                        <td colspan="6"><fmt:message key="siteSettingsPublication.publicationJobs.noItemFound"/></td>
                    </tr>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>
    </div>
</div>

