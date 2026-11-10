// The Site publication screen of the module: a JSP webflow that jContent renders in an iframe.
// Each submit replaces the document of the iframe, so every lookup starts from this query chain.
const IFRAME = 'iframe[src*="site-publication.html"]'

export const frame = () => cy.get(IFRAME, { timeout: 30000 }).its('0.contentDocument.body')

export const sitePublication = {
    // The form binds its scope and language handlers in `$(document).ready`, which can run after the
    // `load` event: a click before that checks the input and changes nothing else. Wait for the binding.
    waitUntilLoaded: () =>
        cy
            .get(IFRAME, { timeout: 30000 })
            .its('0.contentWindow')
            .should((win) => {
                const scopeNode = win.document.getElementById('scopeNode')
                expect(scopeNode, 'the scope input of the publication form').to.exist
                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                const events = (win as any).jQuery?._data(scopeNode, 'events') ?? {}
                expect(events, 'the handlers of the scope input').to.have.property('change')
            }),
    entry: () => cy.get('[data-sel-role="sitepublication"]'),
    // A built-in entry of the same jContent accordion: it proves the accordion has rendered.
    tagManagerEntry: () => cy.get('[data-sel-role="jctagsmanager"]', { timeout: 30000 }),
    title: () => frame().find('h2'),
    showLastPublicationsButton: () => frame().find('button[name="showLastPublicationsButton"]'),
    publishButton: () => frame().find('#btnSitePublicationPublish'),
    // The Material skin hides the native inputs behind a styled span, so a check is forced.
    selectSiteScope: () => frame().find('#scopeSite').check({ force: true }),
    selectNodeScope: () => frame().find('#scopeNode').check({ force: true }),
    nodePath: () => frame().find('#nodePath'),
    language: (lang: string) => frame().find(`#languages${lang}`),
    selectLanguage: (lang: string) => frame().find(`#languages${lang}`).check({ force: true }),
    selectAllLanguages: () => frame().find('#languagesAll').check({ force: true }),
    confirmDialog: () => frame().find('.bootbox.modal'),
    confirmCancel: () => frame().find('.bootbox.modal [data-bb-handler="cancel"]'),
    confirmPublish: () => frame().find('.bootbox.modal [data-bb-handler="confirm"]'),
    successAlert: () => frame().find('.alert-success'),
    historyRows: () => frame().find('#tablePublicationJobs tbody tr'),
    backToPublication: () => frame().find('button[name="backButton"]'),
    visit: (siteKey: string) => {
        cy.visit(`/jahia/jcontent/${siteKey}/en/apps`)
        // jContent then opens the first entry of the accordion by itself. A click before that redirect
        // loses the screen it opened, so wait until an entry is selected.
        cy.location('pathname', { timeout: 30000 }).should('match', /\/apps\/[^/]+/)
    },
}

const ACTIVE_JOB = ['ADDED', 'SCHEDULED', 'EXECUTING']

// The module runs one SiteAdminPublicationJob per language, and each one publishes in its own run.
export const waitForSitePublicationJobs = () => {
    cy.waitUntil(
        () =>
            cy
                .apolloClient()
                .apollo({ queryFile: 'graphql/siteAdminPublicationJobs.graphql', fetchPolicy: 'no-cache' })
                .then((response) => {
                    const jobs = response?.data?.admin?.jahia?.scheduler?.jobs ?? []
                    return !jobs.some(
                        (job) =>
                            ['SiteAdminPublicationJob', 'PublicationJob'].includes(job.group) &&
                            ACTIVE_JOB.includes(job.jobStatus),
                    )
                }),
        { timeout: 60000, interval: 1000, errorMsg: 'The site publication jobs did not finish within a minute' },
    )
}

// A page that is live in a language renders there, and gives 404 in a language that was never published.
export const liveStatus = (lang: string, path: string) =>
    cy.request({ url: `/cms/render/live/${lang}${path}.html`, failOnStatusCode: false }).its('status')

// A role granted on a site adds the user to the privileged group asynchronously. A login before that
// keeps a session without it, and jContent then gets a 403 from its GWT services.
export const waitForPrivilegedMembership = (username: string) => {
    cy.waitUntil(
        () =>
            cy
                .apolloClient()
                .apollo({
                    queryFile: 'graphql/userGroupMembership.graphql',
                    variables: { username },
                    fetchPolicy: 'no-cache',
                })
                .then((response) => {
                    const groups = response?.data?.admin?.userAdmin?.user?.groupMembership?.nodes ?? []
                    return groups.some((group) => group.name === 'privileged')
                }),
        { timeout: 30000, interval: 500, errorMsg: `${username} did not join the privileged group within 30 seconds` },
    )
}
