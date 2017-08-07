package org.jahia.modules.sitesettings.publication.util;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jahia.services.content.PublicationInfo;
import org.jahia.services.content.PublicationInfoNode;

/**
 * Site admin publication utilities.
 */
public class SiteAdminPublicationUtils {

    /**
     * Represents information about a sub-tree of nodes to publish (as compared to its live counterpart if any).
     */
    public static class PublicationData {

        private boolean needsPublication;
        private Set<PublicationInfoNode> conflictingNodes = new LinkedHashSet<>();
        private Set<PublicationInfoNode> missingMandatoryPropertiesNodes = new LinkedHashSet<>();

        /**
         * @return Whether this sub-tree needs publication, or has been fully published already.
         */
        public boolean getNeedsPublication() {
            return needsPublication;
        }

        /**
         * Set the "needs publication" flag.
         */
        public void setNeedsPublication() {
            this.needsPublication = true;
        }

        /**
         * @return Nodes to publish that conflict with their live counterpart
         */
        public Collection<PublicationInfoNode> getConflictingNodes() {
            return Collections.unmodifiableCollection(conflictingNodes);
        }

        /**
         * Add a node that conflicts with its live counterpart.
         * @param node Conflicting node
         */
        public void addConflictingNode(PublicationInfoNode node) {
            conflictingNodes.add(node);
        }

        /**
         * @return Nodes to publish that miss any mandatory properties
         */
        public Collection<PublicationInfoNode> getMissingMandatoryPropertiesNodes() {
            return Collections.unmodifiableCollection(missingMandatoryPropertiesNodes);
        }

        /**
         * Add a node that misses any mandatory properties.
         * @param node Node that misses any mandatory properties
         */
        public void addMissingMandatoryPropertiesNode(PublicationInfoNode node) {
            missingMandatoryPropertiesNodes.add(node);
        }
    }

    private SiteAdminPublicationUtils() {
    }

    /**
     * Convert publication info.
     * @param publicationInfo Publication info typically retrieved from the PublicationService
     * @return Converted publication info
     */
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
