package org.jahia.modules.sitesettings.publication;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
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
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StartSiteAdminPublicationJob extends BackgroundJob {

    public static final String PUBLICATION_JOB_SITE_UUID = "siteUuid";
    public static final String PUBLICATION_JOB_PATH = "path";
    public static final String PUBLICATION_JOB_LANGUAGES = "languages";
    public static final String PUBLICATION_JOB_FORCE = "force";
    public static final String PUBLICATION_JOB_UI_LOCALE = "uiLocale";

    private static final Logger logger = LoggerFactory.getLogger(StartSiteAdminPublicationJob.class);

    @Override
    public void executeJahiaJob(JobExecutionContext jobExecutionContext) throws Exception {

        JobDataMap dataMap = jobExecutionContext.getJobDetail().getJobDataMap();
        String siteUuid = dataMap.getString(PUBLICATION_JOB_SITE_UUID);
        final String path = dataMap.getString(PUBLICATION_JOB_PATH);
        @SuppressWarnings("unchecked") Collection<String> languages = (Collection<String>) dataMap.get(PUBLICATION_JOB_LANGUAGES);
        boolean force = dataMap.getBoolean(PUBLICATION_JOB_FORCE);
        Locale uiLocale = (Locale) dataMap.get(PUBLICATION_JOB_UI_LOCALE);

        if (force) {

            JCRNodeWrapper node = JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.EDIT_WORKSPACE, null, new JCRCallback<JCRNodeWrapper>() {

                @Override
                public JCRNodeWrapper doInJCR(JCRSessionWrapper session) throws RepositoryException {
                    return session.getNode(path);
                }
            });

            JCRPublicationService publicationService = (JCRPublicationService) SpringContextSingleton.getBean("jcrPublicationService");
            LinkedHashSet<String> missingMandatoryPropertiesNodes = new LinkedHashSet<>();
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

                JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, Constants.LIVE_WORKSPACE, null, new JCRCallback<Void>() {

                    @Override
                    public Void doInJCR(JCRSessionWrapper session) throws RepositoryException {
                        session.getNode(path).remove();
                        session.save();
                        return null;
                    }
                });
            } else {
                // TODO: Send notification
                return;
            }
        }

        SchedulerService schedulerService = (SchedulerService) SpringContextSingleton.getBean("SchedulerService");
        for (String language : languages) {
            logger.info("Schedulling publication job for node {} in language {}", path, language);
            JobDetail jobDetail = BackgroundJob.createJahiaJob("Publication", SiteAdminPublicationJob.class);
            JobDataMap jobDataMap = jobDetail.getJobDataMap();
            jobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_SITE_UUID, siteUuid);
            jobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_PATH, path);
            jobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_LANGUAGE, language);
            jobDataMap.put(SiteAdminPublicationJob.UI_LOCALE, uiLocale);
            schedulerService.scheduleJobNow(jobDetail);
        }
    }
}
