/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.modules.sitesettings.publication.webflow;

import java.io.Serializable;
import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.jahia.modules.sitesettings.publication.SiteAdminPublicationJob;
import org.jahia.modules.sitesettings.publication.service.MailTemplateLocationService;
import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.scheduler.BackgroundJob;
import org.jahia.services.scheduler.SchedulerService;
import org.jahia.utils.LanguageCodeConverters;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.binding.message.MessageContext;

/**
 * Webflow handler for the site publication panel, that aims to schedule background publication jobs for the site content in selected
 * languages.
 *
 * @author Sergiy Shyrkov
 */
public class SitePublicationFlowHandler implements Serializable {

    /**
     * Comparator implementation that compares language display names in a certain locale.
     */
    private static class LanguageDisplayNameComparator implements Comparator<String> {

        private transient Collator collator = Collator.getInstance();
        private Locale locale;

        public LanguageDisplayNameComparator(Locale locale) {
            if (locale != null) {
                this.locale = locale;
                collator = Collator.getInstance(locale);
            }
        }

        @Override
        public int compare(String lang1, String lang2) {
            if (lang1.equals(lang2)) {
                return 0;
            }
            return collator.compare(LanguageCodeConverters.languageCodeToLocale(lang1).getDisplayName(locale),
                    LanguageCodeConverters.languageCodeToLocale(lang2).getDisplayName(locale));
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(SitePublicationFlowHandler.class);

    private static final long serialVersionUID = -7862783658070459239L;

    @Autowired
    private transient SchedulerService schedulerService;

    @Autowired
    private transient MailTemplateLocationService mailTemplateLocationService;

    /**
     * Returns a new instance of the site publication data model to be used when displaying the form.
     *
     * @param renderContext current DX rendering context instance
     * @return a new instance of the site publication data model to be used when displaying the form
     */
    public SitePublication initSitePublication(final RenderContext renderContext) {
        JCRSiteNode site = renderContext.getSite();

        SitePublication sitePublication = new SitePublication(site.getSiteKey(), site.getTitle());

        sitePublication.setNodePath(site.getPath());
        List<String> siteLanguages = new LinkedList<>(site.getLanguages());
        if (siteLanguages.size() > 1) {
            // in case of multiple languages sort them by display name using current UI locale
            Collections.sort(siteLanguages, new LanguageDisplayNameComparator(renderContext.getUILocale()));
        } else {
            // if we have only a single language we "pre-select" it
            Set<String> languages = new HashSet<>(1);
            languages.add(siteLanguages.iterator().next());
            sitePublication.setLanguages(languages);
        }
        sitePublication.setSiteLanguages(siteLanguages);

        return sitePublication;
    }

    private boolean isNodePathValid(final String nodePath, String sitePath) throws RepositoryException {

        if (!nodePath.equals(sitePath) && !nodePath.startsWith(sitePath + "/")) {
            return false;
        }

        return JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
            @Override
            public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                return session.nodeExists(nodePath);
            }
        });
    }

    private void scheduleJob(String nodePath, String lang, Locale uiLocale) throws SchedulerException {
        logger.info("Schedulling publication job for node {} in language {}", nodePath, lang);
        JobDetail jobDetail = BackgroundJob.createJahiaJob("Publication", SiteAdminPublicationJob.class);
        JobDataMap jobDataMap = jobDetail.getJobDataMap();
        jobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_PATH, nodePath);
        jobDataMap.put(SiteAdminPublicationJob.PUBLICATION_JOB_LANGUAGE, lang);
        jobDataMap.put(SiteAdminPublicationJob.MAIL_TEMPLATE, mailTemplateLocationService.getMailTemplateLocation());
        jobDataMap.put(SiteAdminPublicationJob.UI_LOCALE, uiLocale);
        schedulerService.scheduleJobNow(jobDetail);
    }

    /**
     * Schedules background jobs for the site publication in selected languages, preliminary performing data validation, i.e. non empty node
     * path and at least one language selected.
     *
     * @param sitePublication the site publication data model object
     * @param renderContext current DX rendering context instance
     * @param messages the message context instance
     * @return the site publication data model object to be used for the further flow; in case of a successful scheduling of publication
     *         jobs it returns a new data model object with all values reset
     */
    public SitePublication startPublication(SitePublication sitePublication, RenderContext renderContext,
            MessageContext messages) {

        try {

            if (sitePublication.getScope() == null) {
                messages.addMessage(new MessageBuilder().error().code("siteSettingsPublication.scope.mandatory").build());
                return sitePublication;
            }
            if (sitePublication.getLanguages().isEmpty()) {
                messages.addMessage(new MessageBuilder().error().code("siteSettingsPublication.languages.mandatory").build());
                return sitePublication;
            }

            boolean isEntireSitePublication = sitePublication.getScope() == SitePublication.Scope.ENTIRE_SITE;
            String currentSitePath = "/sites/" + sitePublication.getCurrentSiteKey();

            if (!isEntireSitePublication && !isNodePathValid(sitePublication.getNodePath(), currentSitePath)) {
                messages.addMessage(
                        new MessageBuilder().error().code("siteSettingsPublication.scope.node.invalid").build());
                return sitePublication;
            }

            for (String lang : sitePublication.getLanguages()) {
                scheduleJob(isEntireSitePublication ? currentSitePath : sitePublication.getNodePath(), lang, renderContext.getUILocale());
            }
            messages.addMessage(new MessageBuilder().info().code("siteSettingsPublication.started").build());
            // we are successful, reset the model data
            return initSitePublication(renderContext);

        } catch (Exception e) {
            logger.error("An error occurred starting publication", e);
            messages.addMessage(new MessageBuilder().error().code("siteSettingsPublication.error.general")
                    .arg(e.getMessage()).build());
            if (logger.isDebugEnabled()) {
                logger.debug("Unable to publish", e);
            }
            return sitePublication;
        }
    }

    public List<JobDetail> getPublicationJobs() throws SchedulerException {
        return schedulerService.getAllJobs(BackgroundJob.getGroupName(SiteAdminPublicationJob.class));
    }
}
