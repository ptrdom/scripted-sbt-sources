# scripted-sbt-sources

scripted-sbt-sources is a sbt plugin for sbt plugins, it adds improved modularisation of `scripted` tests to assist with 
easier maintenance and clearer presentation of plugin usage examples.

## Motivation

One of the most important goals for plugin developers is making their creations easy to understand and adopt, and to achieve
that the plugin needs not only documentation, but also clear and simple examples. There are at least two ways of 
implementing that:

1. Providing `/examples` subdirectory with select projects.
1. Referencing `scripted` tests as examples.

Problem with `/examples` subdirectory is that the examples must be kept up to date and made sure to remain working, and the 
workflow for that is not immediately obvious. This can be solved by just using `scripted` tests as examples, but truly
good tests bring in a lot of test specific noise into the example code, so the part of the test that represents the example
itself becomes less clear. So maybe there is a way to solve these two problems at once - have clear examples and good tests.

## Usage

scripted-sbt-sources adds new configuration file named `.sources` to `scripted` tests. Developers can specify a newline
separated list of directories in `.sources` that will be merged together with `scripted` test directory. Directories are
merged in decreasing priority, meaning that files in `scripted` test directory and sources listed at the top of the
`.sources` list overwrite directories below them. This new merged test directory is placed in `/target` directory and is
used by the `scripted` task to run tests. 

See [src/sbt-test/scripted-sources-plugin/basic-plugin-project](src/sbt-test/scripted-sources-plugin/basic-plugin-project) 
for example plugin project.

## License

This software is licensed under the MIT license
