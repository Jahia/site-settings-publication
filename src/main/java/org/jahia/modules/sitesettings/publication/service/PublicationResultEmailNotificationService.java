/*
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms & Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.sitesettings.publication.service;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.jcr.RepositoryException;
import javax.script.ScriptException;

import org.apache.commons.lang.StringUtils;
import org.jahia.exceptions.JahiaRuntimeException;
import org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.i18n.Messages;
import org.jahia.utils.i18n.ResourceBundles;
import org.quartz.JobDataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends email notifications on SiteAdminPublicationJob completion.
 */
public class PublicationResultEmailNotificationService {

    private static final SimpleDateFormat NOTIFICATION_DATE_TIME_FORMAT = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");
    private static final Logger LOGGER = LoggerFactory.getLogger(PublicationResultEmailNotificationService.class);

    private JahiaUserManagerService userManagerService;
    private MailService mailService;
    private String emailTemplate;

    /**
     * Send email notification about SiteAdminPublicationJob completion.
     *
     * @param jobDataMap Job details
     */
    @SuppressWarnings("unchecked")
    public void notifyJobCompleted(JobDataMap jobDataMap) {

        if (!mailService.isEnabled()) {
            LOGGER.debug("Will not send publication result notification as the mail service is disabled");
            return;
        }

        JCRUserNode user = userManagerService.lookupUserByPath((String) jobDataMap.get(BackgroundJob.JOB_USERKEY));
        String mailTo = UserPreferencesHelper.getEmailAddress(user);
        if (StringUtils.isEmpty(mailTo)) {
            LOGGER.warn("Unable to send mail for user [{}] because its address is not configured", user.getUserKey());
            return;
        }

        // Build subject.
        Locale locale = (Locale) jobDataMap.get(SiteAdminPublicationJob.UI_LOCALE);
        ResourceBundle resourceBundle = ResourceBundles.get("resources.SiteSettings-Publication", locale);
        String publicationResult = (String) jobDataMap.get(SiteAdminPublicationJob.PUBLICATION_JOB_RESULT);
        String subjectKey = "siteSettingsPublication.publicationJobs.notification.subject." + publicationResult;

        // Fill bindings with custom job detail infos.
        Map<String, Object> bindings = new HashMap<>();
        List<?> conflicts = (List<?>) jobDataMap.get(SiteAdminPublicationJob.PUBLICATION_JOB_CONFLICTS);
        List<?> missingProperties = (List<?>) jobDataMap.get(SiteAdminPublicationJob.PUBLICATION_JOB_MISSING_PROPERTY);
        bindings.put("conflictSize", (conflicts == null ? 0 : conflicts.size()));
        bindings.put("missingPropertySize", (missingProperties == null ? 0 : missingProperties.size()));
        bindings.put("beginDate", NOTIFICATION_DATE_TIME_FORMAT.format(new Date(Long.parseLong((String) jobDataMap.get(BackgroundJob.JOB_BEGIN)))));
        String jobEnd = (String) jobDataMap.get(BackgroundJob.JOB_END);
        if (StringUtils.isNotEmpty(jobEnd)) {
            bindings.put("endDate", NOTIFICATION_DATE_TIME_FORMAT.format(new Date(Long.parseLong(jobEnd))));
        }
        bindings.put("subject", Messages.getWithArgs(resourceBundle, subjectKey, jobDataMap.get(SiteAdminPublicationJob.PUBLICATION_JOB_PATH), jobDataMap.get(SiteAdminPublicationJob.PUBLICATION_JOB_LANGUAGE)));
        bindings.putAll(jobDataMap);

        try {
            mailService.sendMessageWithTemplate(emailTemplate, bindings, mailTo, mailService.getSettings().getFrom(), null, null, locale, "Site Settings - Publication");
        } catch (RepositoryException | ScriptException e) {
            throw new JahiaRuntimeException(e);
        }
    }

    /**
     * @param userManagerService Associated JahiaUserManagerService
     */
    public void setUserManagerService(JahiaUserManagerService userManagerService) {
        this.userManagerService = userManagerService;
    }

    /**
     * @param mailService Associated MailService
     */
    public void setMailService(MailService mailService) {
        this.mailService = mailService;
    }

    /**
     * @param emailTemplate Location of the mail template to be used for composing notification emails
     */
    public void setEmailTemplate(String emailTemplate) {
        this.emailTemplate = emailTemplate;
    }
}
