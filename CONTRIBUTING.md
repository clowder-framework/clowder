# How to contribute

We would love to get contributions from you! To keep the process manageable we please ask you to follow the workflow 
below. By participating in this project, agree to abide by the [code of conduct](https://clowder.ncsa.illinois.edu/).

Most of the core development happens on [NCSA Opensource][0]. The `master` and `develop` branches are pushed to 
[GitHub][1] nightly.

We encourage contributors to create an account on [NCSA Opensource][0] and make pull requests there. We also accept 
contributions directly on GitHub.

## Workflow

* Make sure you have a [Jira account on NCSA Opensource][0]
* If you want to make your pull request on GitHub make sure you have a [GitHub account](https://github.com/signup/free)
* If it's a new feature or improvement please discuss your ideas with the community on the 
  [HipChat][hipchat] channel or by sending an email to the 
  [mailing list](mailto:clowder@lists.illinois.edu)
* Submit a ticket in [Jira][jira] for your issue, if one doesn't exist already
  * If it's a bug, clearly describe the issue including steps to reproduce it
  * If it's a new feature, clearly describe requirements and how your proposed solution would fulfill them. For complex
  new features it might help to create a [wiki page][wiki]
  * If it's an improvement, clearly describe the current behavior and how your contribution will improve current functionality.
* Create a fork on NCSA Bitbucket or GitHub
* Check out the `develop` branch
* Make a feature branch
  * If you created the fork on NCSA Bitbucket, use the "Create branch" link in Jira. This will properly name the branch
   and keep the link in the issue
  * If you created the fork in GitHub, follow the Atlassian Bitbucket [branch naming scheme][2] when naming your branch. 
    For example `feature/CATS-438-validate-all-jsonld`
  * On your machine use `git checkout -b "cool-new-feature"`
* Make your cool new feature or bugfix on your branch
* From your branch, make a pull request against `ncsa/clowder/develop`
* Work with repo maintainers to get your change reviewed on GitHub or NCSA Bitbucket
* When ready to be merged, the branch will first be pulled into `ncsa/clowder/develop` on NCSA Bitbucket
    Overnight it will be pushed to `ncsa/clowder/develop` on GitHub
* Merge `ncsa/clowder/develop` into your origin `develop`
* Delete your feature branch

## Code Reviews

Code reviews for pull requests in GitHub will happen in GitHub. Code reviews for pull requests in NCSA Opensource 
Bitbucket will happen in NCSA Opensource Bitbucket. When a pull request is ready to be merged into `develop` it will 
first be pushed to NCSA Opensource Bitbucket if it's on GitHub. It will then be merged with the `develop` branch in 
NCSA Opensource Bitbucket. It will become available on `develop` on GitHub overnight with the nightly updates.

## Issues

We use some of the common issue types available in Jira. For the most part they are self explanatory: `New Feature`, 
`Improvement`, `Bug`.

Make sure your issue to get feedback from the community (either on [HipChat][hipchat] or by sending an email 
to the [mailing list](mailto:clowder@lists.illinois.edu)) before you start implementing a solution. Given the 
distributed nature of the team and the different requirements from the different projects using and contributing to 
Clowder, it's important that contributions to the core are compatible with all projects and the overall design.

## Testing

We use the [ScalaTest testing framework][scalatest] for our tests. Our testing suite is currently not comprehensive. If 
you feel brave enough please consider adding a test for your new feature (or for existing ones!).

## Continuous Integration

Branches in [Bitbucket][bitbucket] are automatically built with [Bamboo][bamboo].

## Documentation

Developer documentation is available in [Confluence][]. User documentation (still rough) is available in the source 
`/doc/src/sphinx` and [online][userdocs].

[0]: https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS
[1]: https://github.com/ncsa/clowder
[2]: https://confluence.atlassian.com/bitbucketserver/using-branches-in-bitbucket-server-776639968.html#UsingbranchesinBitbucketServer-Creatingbranches
[scalatest]: http://www.scalatest.org/
[hipchat]: https://hipchat.ncsa.illinois.edu/ggYc5FGDP
[jira]: https://opensource.ncsa.illinois.edu/jira/projects/CATS
[wiki]: https://opensource.ncsa.illinois.edu/confluence/display/CATS
[bitbucket]: https://opensource.ncsa.illinois.edu/bitbucket/projects/CATS
[bamboo]: https://opensource.ncsa.illinois.edu/bamboo/browse/CATS
[userdocs]: https://clowder.ncsa.illinois.edu/docs/
[confluence]: https://opensource.ncsa.illinois.edu/confluence/display/CATS/Home