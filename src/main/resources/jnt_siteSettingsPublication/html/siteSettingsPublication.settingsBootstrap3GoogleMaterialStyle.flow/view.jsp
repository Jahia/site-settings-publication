<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo" type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<%--@elvariable id="flowRequestContext" type="org.springframework.webflow.execution.RequestContext"--%>
<template:addResources type="javascript" resources="jquery.min.js,jquery-ui.min.js,jquery.blockUI.js,workInProgress.js,bootbox.min.js"/>
<c:set var="multipleSiteLanguages" value="${fn:length(sitePublication.siteLanguages) > 1}"/>
<c:set var="currentSitePath" value="/sites/${sitePublication.currentSiteKey}"/>
<fmt:message var="i18nSitePublication" key="siteSettingsPublication.title"/>
<fmt:message var="i18nError" key="label.error"/>
<fmt:message var="i18nNodePathInvalid" key="siteSettingsPublication.scope.node.invalid"/>
<fmt:message var="i18nWaiting" key="label.workInProgressTitle"/>
<fmt:message var="i18nCancel" key="label.cancel"/>
<fmt:message var="i18nPublish" key="label.publish"/>
<fmt:message var="i18nConfirmSite" key="siteSettingsPublication.confirm.site">
    <fmt:param value="${sitePublication.currentSiteName}"/>
    <fmt:param value="${sitePublication.currentSiteKey}"/>
</fmt:message>
<fmt:message var="i18nConfirmNode" key="siteSettingsPublication.confirm.node"/>
<fmt:message var="i18nConfirmBackground" key="siteSettingsPublication.confirm.background"/>
<spring:eval var="ENTIRE_SITE" expression="T(org.jahia.modules.sitesettings.publication.webflow.SitePublication$Scope).ENTIRE_SITE"/>
<spring:eval var="SITE_SUBNODE" expression="T(org.jahia.modules.sitesettings.publication.webflow.SitePublication$Scope).SITE_SUBNODE"/>

<template:addResources>
    <script type="text/javascript">

        function sitePublicationSubmitForm(actionType) {
            workInProgress('${functions:escapeJavaScript(i18nWaiting)}');
            $('#sitePubAction').val(actionType);
            $('#formSitePublication').submit();
        }

        function sitePublicationConfirm() {
            var path = $('#nodePath').val().trim();
            if ($('#scopeNode').is(':checked')) { <%-- verify the provided node path --%>
                if (path != '${currentSitePath}' && path.match('^${currentSitePath}/') == null) {
                    bootbox.alert({
                        title: '${functions:escapeJavaScript(i18nError)}',
                        message: '${functions:escapeJavaScript(i18nNodePathInvalid)}'
                    });
                    return false;
                }
            }
            var msgConfirm = $('#scopeSite').is(':checked') ? '${functions:escapeJavaScript(i18nConfirmSite)}' : '${functions:escapeJavaScript(i18nConfirmNode)}'.replace('{0}', path);
            msgConfirm = msgConfirm + '<br/><ul>';
            $('input[name="languages"]:checked').each(function() {
                msgConfirm = msgConfirm + '<li>' + $(this).attr('title') + '</li>';
            });
            msgConfirm = msgConfirm + '</ul>'
                         + '${functions:escapeJavaScript(i18nConfirmBackground)}';
            bootbox.confirm({
                title: '${functions:escapeJavaScript(i18nSitePublication)}',
                message: msgConfirm,
                buttons: {
                    cancel: {
                        label: '${functions:escapeJavaScript(i18nCancel)}'
                    },
                    confirm: {
                        label: '${functions:escapeJavaScript(i18nPublish)}',
                        className: 'btn-warning btn-primary'
                    }
                },
                callback: function(result) {
                    if (result) {
                        sitePublicationSubmitForm('publish');
                    }
                }
            });
            return true;
        }

        $(document).ready(function() {

            $('#languagesAll').click(function() { <%-- handle click on the "Select all" languages checkbox --%>
                var chkd = $(this).is(':checked');
                $('input[name="languages"]').each(function() {
                    this.checked = chkd;
                });
                checkPublishButtonStatus();
            });
            $('input[name="languages"]').click(function() { <%-- on click on any of language checkboxes we need to update the state of "Select all" checkbox --%>
                if (!$(this).is(':checked')) {
                    $('#languagesAll').each(function() {
                        this.checked = false;
                    });
                }
                checkPublishButtonStatus();
            });
            $('input[name="scope"]').change(function() { <%-- when scope is changed we either enable or disable the node path input field  --%>
                $('#nodePath').prop('disabled', $('#scopeNode').is(':checked') == false);
                checkPublishButtonStatus();
            });
            $('#nodePath').click(function() {
                $(this).select();
            });

            function checkPublishButtonStatus() { <%-- we enable the publish button when a scope and a language (in case of multiple available languages) is selected  --%>
                var ready = false;
                if ($('#scopeSite').is(':checked') || $('#scopeNode').is(':checked')) {
                    if ('${multipleSiteLanguages}' == 'false' || $('input[name="languages"]:checked').length > 0) {
                        ready = true;
                    }
                }
                $('#btnSitePublicationPublish').prop('disabled', !ready);
            }
        });
    </script>
</template:addResources>

<div class="page-header">
    <h2>${fn:escapeXml(i18nSitePublication)}</h2>
</div>
<div class="panel panel-default">
    <div class="panel-body">
        <div class="row">
            <div class="col-md-12">
                <c:if test="${not empty flowRequestContext.messageContext.allMessages}">
                    <div>
                        <c:forEach items="${flowRequestContext.messageContext.allMessages}" var="message">
                            <c:if test="${message.severity eq 'INFO'}">
                                <div class="alert alert-success">
                                    <button type="button" class="close" data-dismiss="alert">&times;</button>
                                        ${fn:escapeXml(message.text)}
                                </div>
                            </c:if>
                            <c:if test="${message.severity eq 'ERROR'}">
                                <div class="alert alert-danger">
                                    <button type="button" class="close" data-dismiss="alert">&times;</button>
                                        ${fn:escapeXml(message.text)}
                                </div>
                            </c:if>
                        </c:forEach>
                    </div>
                </c:if>
                <div class="row">
                    <div class="col-sm-9 col-md-9">
                        <fmt:message key="label.error.mandatoryField" var="i18nMandatory"/><c:set var="i18nMandatory" value="${fn:escapeXml(i18nMandatory)}"/>
                        <c:set var="mandatoryLabel">&nbsp;<span class="text-error" title="${i18nMandatory}"><strong>*</strong></span></c:set>
                        <form action="${flowExecutionUrl}" method="post" id="formSitePublication">
                            <input type="hidden" id="sitePubAction" name="_eventId" value="" />
                            <fieldset>
                                <%--<div class="col-md-12">--%>
                                    <div class="row">
                                        <div class="col-md-6">
                                            <h4><fmt:message key="siteSettingsPublication.scope"/>${mandatoryLabel}</h4>
                                            <div class="radio">
                                                <label class="radio-inline" for="scopeSite">
                                                    <input type="radio" id="scopeSite" name="scope" value="${ENTIRE_SITE}"
                                                    ${sitePublication.scope
                                                            == ENTIRE_SITE ? 'checked' : ''}/>&nbsp;
                                                    <fmt:message key="siteSettingsPublication.scope.site"/>:&nbsp;${fn:escapeXml(sitePublication.currentSiteName)}&nbsp;(${fn:escapeXml(sitePublication.currentSiteKey)})
                                                </label>
                                            </div>
                                            <div class="radio">
                                                <label class="radio-inline" for="scopeNode">
                                                    <input type="radio" id="scopeNode" name="scope" value="${SITE_SUBNODE}" ${sitePublication.scope == SITE_SUBNODE ? 'checked' : ''}/>&nbsp;
                                                    <fmt:message key="siteSettingsPublication.scope.node"/>:&nbsp;
                                                </label>
                                            </div>
                                            <div class="row">
                                                <div class="col-sm-7 col-sm-offset-1">
                                                    <input type="text" name="nodePath" class="form-control" id="nodePath"
                                                           value="${fn:escapeXml(sitePublication.nodePath)}" ${sitePublication.scope != SITE_SUBNODE ? 'disabled="disabled"' : ''}/>

                                                    (<fmt:message key="siteSettingsPublication.scope.node.hint"><fmt:param
                                                        value="${sitePublication.currentSiteKey}"/></fmt:message>)
                                                </div>
                                            </div>
                                            <br/>
                                        </div>
                                    </div>
                                <%--</div>--%>
                            </fieldset>
                            <div class="col-md-6">
                                <c:if test="${multipleSiteLanguages}">
                                    <fieldset>
                                        <div class="row">
                                            <h4><fmt:message key="siteSettingsPublication.languages"/>${mandatoryLabel}</h4>
                                            <form class="form-horizontal">
                                                <div class="form-group">
                                                    <div class="checkbox">
                                                        <label for="languagesAll">
                                                            <input type="checkbox" id="languagesAll" name="languagesAll"
                                                                   value="true"/>&nbsp;
                                                            <fmt:message key="siteSettingsPublication.languages.all"/>
                                                        </label>
                                                    </div>
                                                    <c:forEach var="lang" items="${sitePublication.siteLanguages}">
                                                        <div class="checkbox">
                                                            <label for="languages${lang}">
                                                                <c:set var="displayLang" value="${fn:escapeXml(functions:displayLocaleNameWith(functions:toLocale(lang), renderContext.UILocale))}&nbsp;[${lang}]"/>
                                                                <input type="checkbox" id="languages${lang}" name="languages"
                                                                       value="${fn:escapeXml(lang)}" title="${displayLang}" ${fn:contains(sitePublication.languages, lang) ? 'checked="checked"' : ''}/>&nbsp;
                                                                    ${displayLang}
                                                            </label>
                                                        </div>
                                                    </c:forEach>
                                                </div>
                                            </form>
                                        </div>
                                    </fieldset>
                                </c:if>
                                <c:if test="${!multipleSiteLanguages}">
                                    <c:forEach var="lang" items="${sitePublication.siteLanguages}">
                                        <input type="checkbox" name="languages" value="${fn:escapeXml(lang)}" title="${fn:escapeXml(functions:displayLocaleNameWith(functions:toLocale(lang), renderContext.UILocale))}&nbsp;[${lang}]" checked="checked" style="display: none"/>
                                    </c:forEach>
                                </c:if>
                            </div>
                        </form>
                    </div>
                    <div class="col-sm-3 col-md-3">
                        <div class="pull-right">
                            <form action="${flowExecutionUrl}" method="post" style="display: inline;">
                                <input type="hidden" name="_eventId" value="lastPublications"/>
                                <button class="btn btn-default" type="submit" name="showLastPublicationsButton">
                                    <i class="icon-list"></i>
                                    &nbsp;<fmt:message key="siteSettingsPublication.showLastPublications"/>
                                </button>
                            </form>
                        </div>
                    </div>
                </div>
                <div class="row">
                    <div class="col-md-12">
                        <button class="btn btn-primary pull-left" type="submit" id="btnSitePublicationPublish" name="_eventId_publish"
                                onclick="sitePublicationConfirm(); return false;" ${sitePublication.scope == null || multipleSiteLanguages && empty sitePublication.languages ? 'disabled="disabled"' : ''}>
                            <i class="icon-ok-sign icon-white"></i>
                            &nbsp;${fn:escapeXml(i18nPublish)}
                        </button>
                    </div>
                </div>
                <hr/>
                <p>${mandatoryLabel} - ${i18nMandatory}</p>
            </div>
        </div>
    </div>
</div>
