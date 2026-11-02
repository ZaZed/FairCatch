# Fair Catch

Fair Catch is a planned Java desktop app that evaluates fantasy football trades using Sleeper rosters and imported player projections.

## Project Goal

A trade can affect a team differently depending on its roster, starting positions, and bench players. Fair Catch will show how a proposed trade changes each team's best projected starting lineup, making the gains and losses easier to understand.

## MVP Scope

The first version will evaluate one-for-one player trades for a single upcoming week.

- Load both teams' players, starting positions, and scoring settings from a Sleeper league ID.
- Read player projections from a CSV file prepared for the league's scoring format.
- Provide a Java Swing GUI to select the league, week, projection file, teams, and traded players.
- Check player ownership and report missing projections, invalid data, or lineups that cannot be filled.
- Find each team's highest-projected legal starting lineup, including FLEX positions, without using a player twice.
- Compare both teams before and after the trade and display projected totals, point differences, and lineup changes.

Trades will be hypothetical. The app will not change rosters on Sleeper. Results will describe the selected week's projected lineup impact, rather than a complete season-long trade valuation.

## Future Plans

These features are planned for later versions:

- Compare trades across multiple weeks, accounting for bye weeks and player availability.
- Support two-for-one trades, required player drops, and selected replacement pickups.
- Connect to a verified projection provider and calculate custom scoring from projected player statistics.
- Evaluate bench depth by checking what happens when a starter is unavailable.
- Suggest trades that help both teams and compare multiple offers.
- Save previous analyses and export comparison reports.

## Tech Stack

| Tool | Purpose |
| --- | --- |
| Java | Player and team information, lineup calculations, and trade analysis |
| Java Swing | Desktop windows, menus, file selection, and results tables |
| Java HttpClient | Requests to the Sleeper API |
| Jackson | Convert JSON responses into Java objects |
| Apache Commons CSV | Read projection files |
| Maven | Manage dependencies and build the project |
| JUnit | Test lineup calculations, trades, and input checks |
| Git and GitHub | Track changes and store the project |
| curl | Check API requests from the terminal |

## Data Sources

[Sleeper's public API](https://docs.sleeper.com/) will provide league settings, rosters, and player information in JSON format. It is free for noncommercial use and does not require an API token.

Player projections will come from a local CSV file in the MVP. Each row will identify a player by Sleeper player ID and include the week and projected points. Projections must match the selected league's scoring format.

## Project Status

Fair Catch is currently in the planning stage. The features above describe the intended build; the application is not implemented yet.
