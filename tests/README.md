# Cypress end-to-end tests

The suite drives the Site publication screen of the module as a site administrator, against a running Jahia.

## Run it

- In CI, the `Integration Tests` job of `on-code-change.yml` builds the module, starts Jahia with `docker-compose.yml` and runs `env.run.sh`, which installs the module from `artifacts/` and runs `yarn e2e:ci`.
- Against a local Jahia that runs the built module, from this folder: `yarn install`, then `JAHIA_URL=http://localhost:8080 SUPER_USER_PASSWORD=<root password> yarn e2e:ci`.

The suite needs no module beyond the Jahia image: each spec creates its own site from `templates-system`, and enables `site-settings-publication` on it.

## Conventions

- Each spec creates its own site and its own users, named with a random suffix, and deletes them in `after`. `before` first removes what an earlier run left behind under the same prefix.
- A spec opens the screen through jContent, so that it covers the entry of the module in the Additional accordion. The screen renders in an iframe whose handlers bind late: `sitePublication.waitUntilLoaded()` waits for them before the form is used.

## Tags

A test that migrates a legacy Selenium test carries the tags of its row in the tracking issue of `Jahia/selenium`, through `context.tag(...)` of `@jahia/cypress`. The tag `testrail-case-<n>` names the legacy TestRail case that the test replaces, and `jahia-reporter` writes the tags as labels on the TestRail case of the test.

A test written for this suite answers for no legacy case and carries no tag. A suite with some tests tagged and some not is the intended state.
