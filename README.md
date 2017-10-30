## Safety Jim has been rewritten! To view the old typescript version, click [here](https://github.com/Samoxive/SafetyJim/tree/2.3.3)

# SafetyJim 
A moderation bot for discord communities.


## Getting Started

You will need the following to run Safety Jim

- [ ] JDK 8
- [ ] Gradle
- [ ] PostgreSQL

## Development
To develop Jim, you need to first make sure you have a local instance of postgresql running with schemas properly setup. Gradle will connect to your postgresql instance
by using parameters defined (here)[build.gradle#L40] so feel free to change these to fit your own setup. You can find schema generation script (here)[schema.sql].

Next, you will need to create your own configuration file, you can simply copy `config_example.toml` in the project root and rename it to `config.toml` with your own parameters.

After you setup your postgresql instance, you can run `gradle run` command in your terminal and Jim will be up and running.

To generate a fat jar with dependencies included, you can run `gradle shadowRun`.

## License
MIT
