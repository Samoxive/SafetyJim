<div align="center">
  <img width="200" height="200" style="border-radius: 100%; border: 3px solid #7188d4" src="https://raw.githubusercontent.com/Samoxive/SafetyJim-Client/master/src/assets/jimbo.jpg">
  <h1>SafetyJim</h1>
  <p>
    A moderation bot for discord communities.
  </p>
</div>

# Getting Started

You will need the following to run Safety Jim

- Rust
- PostgreSQL

# Development
Make a copy of `config_example.json` called `config.json` and fill in anything you need, Jim will create the needed tables in PostgreSQL automatically. To launch Jim, use `cargo run --release`. To create an executable file, run `cargo build --release`. For the first run `--create-slash-commands` flag must be used to initialize slash commands on Discord side.

# License
MIT
