import 'cypress-wait-until'

// eslint-disable-next-line @typescript-eslint/no-var-requires
require('@jahia/cypress/dist/support/registerSupport').registerSupport()

// jContent raises errors of its own scripts, which say nothing about the module under test. An error whose
// stack names the Site publication screen still fails the test.
Cypress.on('uncaught:exception', (err) => /site-publication|site-settings-publication/.test(err.stack ?? ''))
