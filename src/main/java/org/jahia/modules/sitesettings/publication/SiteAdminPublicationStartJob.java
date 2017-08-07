package org.jahia.modules.sitesettings.publication;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import javax.jcr.RepositoryException;

import org.jahia.api.Constants;
import org.jahia.modules.sitesettings.publication.util.SiteAdminPublicationUtils;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.PublicationInfoNode;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts publication of a node via scheduling multiple SiteAdminPublicationJob on per language basis.
 * <p>
 * Also, removes live content prior to starting any publication jobs if told to do so via the "force" parameter.
 * However, this is done only in case any nodes to publish don't miss any mandatory properties, otherwise the job is aborted.
 */
public class SiteAdminPublicationStartJob extends SiteAdminPublicationJobSupport {

    /**
     * Key of the job data containing a list of languages to publish content in.
     */
    public static final String PUBLICATION_JOB_LANGUAGES = "languages";

    /**
     * Key of the boolean telling whether live content should be removed before publishing.
     */
    public static final String PUBLICATION_JOB_FORCE = "force";

    private static final Logger logger = LoggerFactory.getLogger(SiteAdminPublicationStartJob.class);

    @Override
    protected void doExecute(JobDataMap jobDataMap) throws Exception {

        final String path = jobDataMap.getString(PUBLICATION_JOB_PATH);
        String siteUuid = jobDataMap.getString(PUBLICATION_JOB_SITE_UUID);
        @SuppressWarnings("unchecked") Collection<String> languages = (Collection<String>) jobDataMap.get(PUBLICATION_JOB_LANGUAGES);
        boolean force = jobDataMap.getBoolean(PUBLICATION_JOB_FORCE);
        Locale uiLocale = (Locale) jobDataMap.get(PUBLICATION_JOB_UI_LOCALE);

        if (force) {

            JCRNodeWrapper node = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<JCRNodeWrapper>() {

                @Override
                public JCRNodeWrapper doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    return session.getNode(path);
                }
            });

            JCRPublicationService publicationService = (JCRPublicationService) SpringContextSingleton.getBean("jcrPublicationService");
            LinkedList<String> missingMandatoryPropertiesNodes = new LinkedList<>();
            for (String language : languages) {
                List<PublicationInfo> publicationInfos = publicationService.getPublicationInfo(node.getIdentifier(), Collections.singleton(language), true, true, true, Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE);
                for (PublicationInfo publicationInfo : publicationInfos) {
                    SiteAdminPublicationUtils.PublicationData publicationData = SiteAdminPublicationUtils.getPublicationData(publicationInfo);
                    for (PublicationInfoNode publicationInfoNode : publicationData.getMissingMandatoryPropertiesNodes()) {
                        missingMandatoryPropertiesNodes.add(publicationInfoNode.getPath());
                    }
                }
            }

            if (missingMandatoryPropertiesNodes.isEmpty()) {

                // Remove live content and publish it from scratch only in case there are no missing mandatory properties.
                // At the same time, we remove live content and re-publish it in case there are any conflicts or content has been fully published before.

                logger.info("Removing live content located at [{}]", path);

                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, new JCRCallback<Void>() {

                    @Override
                    public Void doInJCR(JCRSessionWrapper session) throws RepositoryException {
                        session.getNode(path).remove();
                        session.save();
                        return null;
                    }
                });

            } else {
                // In case there are any missing mandatory properties, neither delete live content nor publish it; just send a notification.
                logger.warn("Site admin publication job for path [{}] has been aborted due to missing mandatory properties", path);
                jobDataMap.put(PUBLICATION_JOB_RESULT, ERROR);
                jobDataMap.put(PUBLICATION_JOB_MISSING_PROPERTY, missingMandatoryPropertiesNodes);
                return;
            }
        }

        // Schedule multiple SiteAdminPublicationJob, one per language.
        SchedulerService schedulerService = (SchedulerService) SpringContextSingleton.getBean("SchedulerService");
        for (String language : languages) {
            logger.info("Schedulling publication job for node {} in language {}", path, language);
            JobDetail publicationJobDetail = BackgroundJob.createJahiaJob("Publication", SiteAdminPublicationJob.class);
            JobDataMap publicationJobDataMap = publicationJobDetail.getJobDataMap();
            publicationJobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_SITE_UUID, siteUuid);
            publicationJobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_PATH, path);
            publicationJobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_LANGUAGE, language);
            publicationJobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_UI_LOCALE, uiLocale);
            schedulerService.scheduleJobNow(publicationJobDetail);
        }
        jobDataMap.put(PUBLICATION_JOB_RESULT, SUCCESS);
    }

    @Override
    protected void sendNotification(JobDataMap jobDataMap) {
        // In case of success, notifications will be sent by SiteAdminPublicationJob instances when they complete.
        String result = jobDataMap.getString(PUBLICATION_JOB_RESULT);
        if (!result.equals(SUCCESS)) {
            super.sendNotification(jobDataMap);
        }
    }
}
