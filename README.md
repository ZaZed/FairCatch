# Fair Catch

Fair Catch is a Java desktop app that analyzes fantasy football trades using Sleeper rosters and weekly player projections. You can optimize lineups, propose trade changes and see how those changes effect each team’s best projected starting lineup.

## Project Goal

A player’s value to a team depends on its starting positions, roster needs, and available bench replacements. Fair Catch helps managers understand a trade by comparing both teams’ strongest legal lineups before and after the exchange.

## MVP Scope

The current version supports one for one player trades for a single regular-season week.

Notable features include: 

- Load league information, teams, managers, and players using a Sleeper league ID.

- Fetch weekly projections directly from Sleeper.

- Select Standard, Half PPR, or PPR scoring.

- Choose two teams and one player from each using a Java Swing interface.

- Preview the proposed trade.

- Find each team’s best projected starting lineup, including supported FLEX positions.

- Simulate the trade using copies of the loaded rosters.

- Display before and after totals, point differences, and players entering or leaving the starting lineup.

- Report invalid selections, missing projections, and lineups that cannot be filled.

All trades are hypothetical. Fair Catch does not submit trades or change rosters on Sleeper.

## Future Plans

These features are planned for later versions:

- Compare trades across multiple weeks, accounting for bye weeks and player availability.
- Support two for one trades, required player drops, and selected replacement pickups.
- Connect to a verified projection provider and calculate custom scoring from projected player statistics.
- Evaluate bench depth by checking what happens when a starter is unavailable.
- Suggest trades that help both teams and compare multiple offers.
- Save previous analyses and export comparison reports.
- Package the app so users can launch it without installing Maven.

## Tech Stack

| Tool | Purpose |
| --- | --- |
| Java | Player and team information, lineup calculations, and trade analysis |
| Java Swing | Desktop windows, menus, file selection, and results tables |
| Java HttpClient | Requests to the Sleeper API |
| Jackson | Convert JSON responses into Java objects |
| Maven | Manage dependencies and build the project |
| Git and GitHub | Track changes and store the project |

## Data Sources

League information, rosters, managers, and player metadata come from Sleeper’s public API, which provides read only access without an API token.

Weekly projections come from a separate, undocumented Sleeper endpoint. Both sources return JSON. Because the projection endpoint is undocumented, its availability and format may change.

Player metadata is cached locally for up to 24 hours to reduce repeated downloads. CSV files are not required.

## Project Status

The core MVP is implemented. Fair Catch loads Sleeper league data and weekly projections, simulates one for one trades, and compares each team’s best starting lineup before and after the exchange.

The app currently runs through Maven and supports a single regular season week using Standard, Half PPR, or PPR scoring presets. 

More features from  the future plans list in this markdown may or may not be implemented at a later date.
