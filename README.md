# Online Exams

A system for managing online exams.

Getting started
---------------

* Check out the latest source
* Install latest asset dependencies: `npm ci`
* Build assets: `npm run dev` (you can use `npm run watch` after this to watch for asset changes and rebuild on the fly)
* Create a PostgreSQL database and user
* Copy `application-example.conf` to `application.conf`:
  * Set `domain`
  * Add your database details to `slick.dbs.default.db`
  * Update your `sso-client` settings
  * Run `./sbt newEncryptionKey` to generate an encryption key for blobStore settings
  * Change the `blobStore.default` settings to point to the onlineexams-dev object store, but override the container name with your name (e.g. `mmannion`)
  * Change the `mywarwick.instances.0` settings to have the onlineexams-dev settings
  * Add your mailtrap settings into `play.mailer`
  * Set `virusscan.api.key`
* Run the application with `./sbt run`

ESLint
------

Test with `npm run lint`, fix with `npm run lint-fix`.

Integration tests
-----------------

In the SBT shell the `integration/test` task will run the integration tests - they are self contained so shouldn't need any other setup.

They run webpack each time to ensure assets are present - if the assets are already generated and this is slowing you down you can disable this in the SBT shell with:

    set webpackEnabled := false
