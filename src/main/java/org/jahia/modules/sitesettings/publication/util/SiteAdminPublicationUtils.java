package org.jahia.modules.sitesettings.publication.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.PublicationInfoNode;

public class SiteAdminPublicationUtils {

    public static class PublicationData {

        private boolean needsPublication;
        private Set<PublicationInfoNode> conflictingNodes = new LinkedHashSet<>();
        private Set<PublicationInfoNode> missingMandatoryPropertiesNodes = new LinkedHashSet<>();

        public boolean getNeedsPublication() {
            return needsPublication;
        }

        public void setNeedsPublication() {
            this.needsPublication = true;
        }

        public Collection<PublicationInfoNode> getConflictingNodes() {
            return Collections.unmodifiableCollection(conflictingNodes);
        }

        public void addConflictingNode(PublicationInfoNode node) {
            conflictingNodes.add(node);
        }

        public Collection<PublicationInfoNode> getMissingMandatoryPropertiesNodes() {
            return Collections.unmodifiableCollection(missingMandatoryPropertiesNodes);
        }

        public void addMissingMandatoryPropertiesNode(PublicationInfoNode node) {
            missingMandatoryPropertiesNodes.add(node);
        }
    }

    private SiteAdminPublicationUtils() {
    }

    public static PublicationData getPublicationData(PublicationInfo publicationInfo) {
        PublicationData publicationData = new PublicationData();
        populatePublicationData(publicationInfo.getRoot(), publicationData);
        return publicationData;
    }

    private static void populatePublicationData(PublicationInfoNode publicationInfoNode, PublicationData publicationData) {
        if (publicationInfoNode.getStatus() != PublicationInfo.PUBLISHED) {
            publicationData.setNeedsPublication();
        }
        if (publicationInfoNode.getStatus() == PublicationInfo.CONFLICT) {
            publicationData.addConflictingNode(publicationInfoNode);
        } else if (publicationInfoNode.getStatus() == PublicationInfo.MANDATORY_LANGUAGE_UNPUBLISHABLE) {
            publicationData.addMissingMandatoryPropertiesNode(publicationInfoNode);
        }
        for (PublicationInfoNode child : publicationInfoNode.getChildren()) {
            populatePublicationData(child, publicationData);
        }
        for (PublicationInfo reference : publicationInfoNode.getReferences()) {
            populatePublicationData(reference.getRoot(), publicationData);
        }
    }
}
