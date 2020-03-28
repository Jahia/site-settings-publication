window.jahia.i18n.loadNamespaces('site-settings-publication');

window.jahia.uiExtender.registry.add('adminRoute', 'sitepublication', {
    targets: ['jcontent:40'],
    label: 'site-settings-publication:label.title',
    isSelectable: true,
    requiredPermission: 'siteAdminPublication',
    requireModuleInstalledOnSite: 'site-settings-publication',
    iframeUrl: window.contextJsParameters.contextPath + '/cms/editframe/default/$lang/sites/$site-key.site-publication.html'
});
