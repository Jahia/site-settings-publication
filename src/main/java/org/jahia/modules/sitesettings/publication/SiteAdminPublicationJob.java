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
package org.jahia.modules.sitesettings.publication;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.content.*;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.mail.MailService;
import org.jahia.services.mail.MailServiceImpl;
import org.jahia.services.preferences.user.UserPreferencesHelper;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.jahia.utils.i18n.Messages;
import org.jahia.utils.i18n.ResourceBundles;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.script.ScriptException;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * This job is used by Publication site settings to execute a publication of a given node identified by its path, in a given language.
 * In case some nodes are conflicting with live nodes or some mandatory properties are missing, the job will be aborted before starting to publish anything.
 *
 * This job needs two mandatory parameters "path" and "language".
 *
 * This job assumes that some basics checks have been done early:
 * - the path corresponds to an existing node
 * - the path corresponds to a child node of the current site
 *
 * @author kevan
 */
public class SiteAdminPublicationJob extends BackgroundJob {

    public static final int SUCCESS = 0;
    public static final int ERROR = 1;
    public static final int NOTHING_TO_PUBLISH = 2;

    /**
     * Key of the job data containing the path of the node to be published.
     */
    public static String PUBLICATION_JOB_PATH = "path";

    /**
     * Key of the job data containing code of the language to publish the node in.
     */
    public static String PUBLICATION_JOB_LANGUAGE = "language";

    /**
     * Key of the result status of the job execution
     */
    public static String PUBLICATION_JOB_RESULT = "result";

    /**
     * Key of the reported conflicts path
     */
    public static String PUBLICATION_JOB_CONFLICTS = "conflict";

    /**
     * Key of the reported missing properties path
     */
    public static String PUBLICATION_JOB_MISSING_PROPERTY = "missingProperty";

    /**
     * Key for the mail template
     */
    public static String MAIL_TEMPLATE = "mailTemplate";

    /**
     * Key for UI Locale
     */
    public static String UI_LOCALE = "uiLocale";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationJob.class);
    private static final SimpleDateFormat notificationDateTimeFormat = new SimpleDateFormat("MM-dd-yyyy HH:mm:ss");

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {

        final JCRPublicationService publicationService = JCRPublicationService.getInstance();

        // get job data
        JobDetail jobDetail = jobExecutionContext.getJobDetail();
        final JobDataMap jobDataMap = jobDetail.getJobDataMap();
        final String path = (String) jobDataMap.get(PUBLICATION_JOB_PATH);
        final String language = (String) jobDataMap.get(PUBLICATION_JOB_LANGUAGE);

        // check data
        if (StringUtils.isEmpty(path) || StringUtils.isEmpty(language)) {
            throw new IllegalArgumentException("Path and language are mandatory to execute the site admin publication job");
        }

        JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<Object>() {

            @Override
            public Object doInJCR(JCRSessionWrapper session) throws RepositoryException {

                JCRNodeWrapper node = session.getNode(path);
                List<PublicationInfo> publicationInfos = publicationService.getPublicationInfo(node.getIdentifier(), Collections.singleton(language), true, true, true, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);

                // check for conflict issues and mandatory properties
                List<PublicationInfoNode> nonPublishableInfos = new LinkedList<>();
                boolean needPublication = false;
                for (PublicationInfo publicationInfo : publicationInfos) {
                    needPublication |= populateNonPublishableInfos(publicationInfo.getRoot(), nonPublishableInfos);
                }
                if (!nonPublishableInfos.isEmpty()) {
                    logger.warn("Site admin publication job for path [{}] and language [{}] has been aborted due to conflicts or missing mandatory properties", path, language);
                    jobDataMap.put(PUBLICATION_JOB_RESULT, ERROR);
                    List<String> conflictNodes = new ArrayList<>();
                    List<String> missingMandatoryPropertyNodes = new ArrayList<>();
                    for (PublicationInfoNode publicationInfo : nonPublishableInfos) {
                        if (publicationInfo.getStatus() == PublicationInfo.CONFLICT) {
                            conflictNodes.add(publicationInfo.getPath());
                        } else {
                            missingMandatoryPropertyNodes.add(publicationInfo.getPath());
                        }
                    }
                    jobDataMap.put(PUBLICATION_JOB_CONFLICTS, conflictNodes);
                    jobDataMap.put(PUBLICATION_JOB_MISSING_PROPERTY, missingMandatoryPropertyNodes);
                } else if (!needPublication) {
                    // nothing to publish
                    logger.info("Site admin publication job for path [{}] and language [{}] finished with nothing to publish", path, language);
                    jobDataMap.put(PUBLICATION_JOB_RESULT, NOTHING_TO_PUBLISH);
                } else {
                    // do the publication
                    publicationService.publishByMainId(node.getIdentifier(), Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, Collections.singleton(language), true, Collections.<String>emptyList());
                    jobDataMap.put(PUBLICATION_JOB_RESULT, SUCCESS);
                }
                sendMail(jobDataMap);
                return null;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void sendMail(JobDataMap jobDataMap) {
        MailService mailService = MailServiceImpl.getInstance();
        if (mailService.isEnabled()) {
            // get user mail informations
            JahiaUserManagerService userManagerService = JahiaUserManagerService.getInstance();
            JCRUserNode user = userManagerService.lookupUserByPath((String) jobDataMap.get(BackgroundJob.JOB_USERKEY));
            String mailTo = UserPreferencesHelper.getEmailAddress(user);
            if (StringUtils.isNotEmpty(mailTo)) {
                Locale currentLocale = (Locale) jobDataMap.get(UI_LOCALE);
                // Build subject
                ResourceBundle resourceBundle = ResourceBundles.get("resources.SiteSettings-Publication", currentLocale);
                String subjectKey = "siteSettingsPublication.publicationJobs.notification.subject.";
                switch ((Integer) jobDataMap.get(PUBLICATION_JOB_RESULT)) {
                    case ERROR:
                        subjectKey += "error";
                        break;
                    case NOTHING_TO_PUBLISH:
                        subjectKey += "nothingToPublish";
                        break;
                    default:
                        subjectKey += "success";
                        break;
                }
                // Fill bindings with custom job detail infos.
                Map<String, Object> bindings = new HashMap<>();
                bindings.put("conflictSize", jobDataMap.get(PUBLICATION_JOB_CONFLICTS) != null ? ((List<?>) jobDataMap.get(PUBLICATION_JOB_CONFLICTS)).size() : 0);
                bindings.put("missingPropertySize", jobDataMap.get(PUBLICATION_JOB_MISSING_PROPERTY) != null ? ((List<?>) jobDataMap.get
                        (PUBLICATION_JOB_MISSING_PROPERTY)).size() : 0);
                bindings.put("beginDate", notificationDateTimeFormat.format(new Date(Long.parseLong((String) jobDataMap.get(BackgroundJob.JOB_BEGIN)))));
                String jobEnd = (String) jobDataMap.get(BackgroundJob.JOB_END);
                if (StringUtils.isNotEmpty(jobEnd)) {
                    bindings.put("endDate", notificationDateTimeFormat.format(new Date(Long.parseLong(jobEnd))));
                }
                bindings.put("subject", Messages.getWithArgs(resourceBundle, subjectKey, jobDataMap.get(PUBLICATION_JOB_PATH), jobDataMap.get(PUBLICATION_JOB_LANGUAGE)));
                bindings.putAll(jobDataMap);
                try {
                    mailService.sendMessageWithTemplate((String) jobDataMap.get(MAIL_TEMPLATE), bindings, mailTo, mailService.getSettings().getFrom(), null, null, currentLocale, "Site Settings - Publication");
                } catch (RepositoryException | ScriptException e) {
                    logger.error("Unable to send notification mail to {} because {}", mailTo, e.getMessage());
                    logger.debug("due to error", e);
                }
            } else {
                logger.debug("Unable to send mail for user [{}] because the mail information is not set", user.getUserKey());
            }
        } else {
            logger.debug("Mail service not configured");
        }
    }

    private boolean populateNonPublishableInfos(PublicationInfoNode publicationInfoNode, List<PublicationInfoNode> nonPublishableInfos) {
        // Do a publication only if the content status is different than published
        boolean needPublication = publicationInfoNode.getStatus() != PublicationInfo.PUBLISHED;
        if (publicationInfoNode.getStatus() == PublicationInfo.CONFLICT || publicationInfoNode.getStatus() == PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE) {
            nonPublishableInfos.add(publicationInfoNode);
        }
        for (PublicationInfoNode child : publicationInfoNode.getChildren()) {
            needPublication |= populateNonPublishableInfos(child, nonPublishableInfos);
        }
        for (PublicationInfo reference : publicationInfoNode.getReferences()) {
            needPublication |= populateNonPublishableInfos(reference.getRoot(), nonPublishableInfos);
        }
        return needPublication;
    }
}