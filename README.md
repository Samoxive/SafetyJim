<div align="center">
  <img width="200" height="200" style="border-radius: 100%; border: 3px solid #7188d4" src="https://safetyjim.xyz/static/media/jimbo.9aee7eba.jpg">
  <h1>SafetyJim</h1>
  <p>
    A moderation bot for discord communities.
  </p>
</div>

# Getting Started

You will need the following to run Safety Jim

- JDK 8
- Gradle
- PostgreSQL
- Docker (optional)

# Development
Make a copy of `config_example.toml` called `config.toml` and fill in anything you need, Jim will create the needed tables in PostgreSQL automatically. To launch Jim, use `gradle run`. To create a jar file with dependencies included, run `gradle shadowJar`.

# Development with Docker
To modify the username, password, and database name, set the environment variables `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB` respectively, and update them accordingly in the `config.toml` file. If you are doing development, the default config file is all you need. 

Run `docker-compose build` and then to launch the bot in the background `docker-compose up -d`. To make modifications to the code without rebuilding the entire image, you must share the drives that hold the code. Refer to the Docker documentation for further information. 

# License
MIT
