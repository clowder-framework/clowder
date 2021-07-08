# How to contribute

We would love to get contributions from you! To keep the process manageable we please ask you to follow the workflow 
below. By participating in this project, you agree to abide by the [code of conduct](https://clowderframework.org/pdf/Clowder-CoC.pdf).
Before your code can be accepted, you will have to sign a [CLA](https://clowderframework.org/pdf/Clowder-CLA.pdf)
and mail it to lmarini@illinois.edu.

Most of the core development happens on [NCSA Bitbucket][bitbucket]. The `master` and `develop` branches are pushed to 
[GitHub][github] nightly.

We encourage contributors to create an account on [NCSA Bitbucket][bitbucket] and make pull requests there. We also accept 
contributions directly on GitHub.

## Workflow

* Make sure you have an [NCSA opensource account][confluence_signup]
* If you want to make your pull request on GitHub make sure you have a [GitHub account](https://github.com/signup/free)
* If it's a new feature or improvement please discuss your ideas with the community on the [Slack][slack] channel 
  or by sending an email to the  [mailing list](mailto:clowder@lists.illinois.edu). You can subscribe 
  to the mailing list [here](https://lists.illinois.edu/lists/subscribe/clowder).
* Submit a ticket in [Jira][jira] for your issue, if one doesn't exist already
  * If it's a bug, clearly describe the issue including steps to reproduce it
  * If it's a new feature, clearly describe requirements and how your proposed solution would fulfill them. For complex
  new features it might help to create a [wiki page][wiki]
  * If it's an improvement, clearly describe the current behavior and how your contribution will improve current functionality.
* Create a fork on [NCSA Bitbucket][bitbucket] or [GitHub][github]
* Check out the `develop` branch
* Make a feature branch
  * If you created the fork on [NCSA Bitbucket][bitbucket], use the "Create branch" link in Jira. This will properly name the branch
   and keep the link in the issue
  * If you created the fork in [GitHub][github], follow the Atlassian Bitbucket [branch naming scheme][branches] when naming your 
    branch. For example `feature/CATS-438-validate-all-jsonld`.
* Make your cool new feature or bugfix on your branch
* From your branch, make a pull request against `develop`
* Work with repo maintainers to get your change reviewed on [GitHub][github] or [NCSA Bitbucket][bitbucket]
* When ready to be merged, the branch will first be pulled into develop on [NCSA Bitbucket][bitbucket]. It will appear on develop on 
  [GitHub][github] after the nightly push.
* When ready to be merged, the branch will first be pulled into `develop` on [NCSA Bitbucket][bitbucket].
    Overnight it will be pushed to `develop` on [GitHub][github].
* Merge the remote `develop` into your origin `develop`
* Delete your feature branch

## Code Reviews

Code reviews for pull requests in GitHub will happen in [GitHub][github]. Code reviews for pull requests in 
NCSA Bitbucket will happen in [NCSA Bitbucket][bitbucket]. When a pull request is ready to be merged into 
`develop` it will first be pushed to NCSA Bitbucket if it's on GitHub. It will then be merged with the `develop` 
branch in NCSA Bitbucket. It will become available on `develop` on GitHub overnight with the nightly updates.

## Issues

We use some of the common issue types available in Jira. For the most part they are self explanatory: `New Feature`, 
`Improvement`, `Bug`.

Make sure to get feedback from the community on your proposed solution before starting implementation, either on 
[Slack][slack]) or by sending an email to the [mailing list](mailto:clowder@lists.illinois.edu)). Given the 
distributed nature of the team and the different requirements from the different projects using and contributing to 
Clowder, it's important that contributions to the core are compatible with all projects and the overall design.

## Testing

We use the [ScalaTest testing framework][scalatest] for our tests. Our testing suite is currently not comprehensive. If 
you feel brave enough please consider adding a test for your new feature (or for existing ones!).

## Continuous Integration

Branches in [NCSA Bitbucket][bitbucket] are automatically built with [Bamboo][bamboo].

## Documentation

Developer documentation is available in [Confluence][confluence]. User documentation (still rough) is available in the source 
`/doc/src/sphinx` and [online][userdocs].

[confluence_signup]: https://opensource.ncsa.illinois.edu/confluence/signup.action
[github]: https://github.com/clowder-framework
[branches]: https://confluence.atlassian.com/bitbucketserver/using-branches-in-bitbucket-server-776639968.html#UsingbranchesinBitbucketServer-Creatingbranches
[scalatest]: http://www.scalatest.org/
[slack]: https://join.slack.com/t/clowder-software/shared_invite/enQtMzQzOTg0Nzk3OTUzLTUxYzVhMzZlZDlhMTc0NzNiZTBiNjcyMTEzNjdmMjc5MTA2MTAzMDQwNmUzYTdmNDQyNGMwOWM1Y2YxMzdhNGM
[jira]: https://opensource.ncsa.illinois.edu/jira/projects/CATS
[wiki]: https://opensource.ncsa.illinois.edu/confluence/display/CATS
[bitbucket]: https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS
[bamboo]: https://opensource.ncsa.illinois.edu/bamboo/browse/CATS
[userdocs]: https://clowderframework.org/docs/
[confluence]: https://opensource.ncsa.illinois.edu/confluence/display/CATS/Home
