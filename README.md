# IPSSI Lyon Discord Bot

Discord bot built with [JDA](https://github.com/discord-jda/JDA) (Java 21+, Maven).

## Setup

1. Create an application at https://discord.com/developers/applications and copy the bot token.
2. Enable the **Server Members Intent** in the Bot tab.
3. Invite the bot with the `bot` and `applications.commands` scopes, with the permissions Manage Roles, Manage Messages, Moderate Members and Send Messages. Its role must be above the roles it manages.
4. Set the environment variables below, then build and start:

```bash
mvn package
java -jar target/discord-bot.jar
```

On PowerShell: `$env:DISCORD_TOKEN = "..."` before `java -jar`.

| Variable | Required | Description |
|---|---|---|
| `DISCORD_TOKEN` | yes | Bot token |
| `DATABASE_FILE` | no | SQLite file path (default `data/bot.db`) |
| `DEV_MODE` | no | Set to `1` to announce the next class 10 seconds after startup, as if it started in 30 minutes (demo) |

5. On the server, an administrator configures the bot with `/config` (welcome channel, logs channel, member role, selectable roles), then posts the panels with `/rules-panel` and `/roles-panel`.

## Class reminders

1. `/config planning-channel` and `/config planning-role`: the role is optional for members and appears in the `/roles-panel` menu.
2. `/planning import` with the `.ics` file: re-run it whenever the school updates the calendar. Existing reminders already sent are kept.
3. The bot posts a summary about 30 minutes before each class and pings the role.
4. At startup the bot **deletes every message of the planning channel** and posts a pinned recap of the current week (the next week on Saturday and Sunday). The recap is updated when the week changes and after each import. The bot needs Manage Messages and Read Message History in that channel, which should be dedicated to the planning.

Recurring events (`RRULE`) and all-day events are not supported.

## Persistence

Commands and services depend on repository interfaces (`fr.ipssi.discordbot.repository`), never on a database directly.
SQLite implementations live in `repository.sqlite`. To use another database (MongoDB, PostgreSQL...), add
implementations of the interfaces in a new package (e.g. `repository.mongo`) and instantiate them in `Main`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/ping` | everyone | Bot latency |
| `/config` | administrator | Welcome, logs and deadline channels, member role, selectable roles, `show` |
| `/rules-panel` | administrator | Post the rules (`src/main/resources/rules.txt`) with an accept button |
| `/roles-panel` | administrator | Post the role selection menu |
| `/announce` | manage messages | Publish an announcement, with an optional role mention |
| `/clear` | manage messages | Delete the latest messages of the channel |
| `/deadline add`, `/deadline remove` | manage messages | Manage deadlines (date format `JJ/MM/AAAA HH:mm`, Paris time) |
| `/deadlines` | everyone | Show the upcoming deadlines |
| `/poll create` | everyone | Poll with 2 to 5 options and an optional automatic closing (hours). One vote per person, click again to remove it |
| `/poll close` | author or manage messages | Close a poll and freeze its results |
| `/event create` | everyone | Organize an outing: title, date (`JJ/MM/AAAA HH:mm`), optional place and description. Members answer with "Je peux" / "Je peux pas" buttons |
| *(automatic)* | | One hour before an event, the bot replies to it and pings the organizer and everyone who answered "Je peux" |
| `/event cancel`, `/event list` | organizer or manage messages / everyone | Cancel an event, list the upcoming ones |
| `/resource add` | everyone | Share a link with a title, up to 5 tags and a description. Duplicate links are refused |
| `/resource search` | everyone | Find resources by words and/or tag (latest first) |
| `/resource remove` | author or manage messages | Delete a resource |
| `/planning import` | administrator | Import the school calendar (`.ics`), replacing the previous one |
| `/warn` | moderate members | Warn a member (DM + log + stored in the database) |
| `/warns` | moderate members | Show the warning history of a member |
| `/mute`, `/unmute` | moderate members | Timeout / remove the timeout of a member |

## Adding a command

Implement `fr.ipssi.discordbot.command.Command` and register it in `Main`.
