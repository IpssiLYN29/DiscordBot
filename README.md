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

1. Declare each promo with `/config promo-set code:LYN channel:#planning-lyn role:@rappels-lyn` (same for `BDX`). The code must match the `[LYN]` / `[BDX]` tags of the calendar descriptions. The role is optional for members and appears in the `/roles-panel` menu.
   Without any promo, `/config planning-channel` and `/config planning-role` define a single planning for everyone.
2. `/planning import` with the `.ics` file: re-run it whenever the school updates the calendar. Existing reminders already sent are kept.
3. The bot posts a summary about 30 minutes before each class in the channel of every promo concerned, and pings its role. A class tagged for one promo only is not sent to the other; a class without tags goes to all.
4. At startup the bot **deletes every message of each planning channel** and posts a pinned recap of the current week (the next week on Saturday and Sunday). The recap is updated when the week changes and after each import. The bot needs Manage Messages and Read Message History in these channels, which should be dedicated to the planning.
5. When an import cancels, moves or edits an upcoming class (time, teacher, room, title, promos) or adds one, the bot posts the changes in the planning channel of the promos concerned. The first import never raises alerts.

Recurring events (`RRULE`) and all-day events are not supported.

## Help threads

1. `/config help-channel` sets the channel where threads are created, and `/config subject-add name:Algorithmique role:@algo` declares each subject (its role also appears in the `/roles-panel` menu).
2. `/question` (subject with autocompletion, title, optional details) opens a public thread, pings the subject role and adds the author.
3. `/resolu` in the thread, by its author or a moderator, renames it `[Résolu] ...` and archives it.

The bot needs Create Public Threads, Send Messages in Threads and Manage Threads in the help channel.

## Persistence

Commands and services depend on repository interfaces (`fr.ipssi.discordbot.repository`), never on a database directly.
SQLite implementations live in `repository.sqlite`. To use another database (MongoDB, PostgreSQL...), add
implementations of the interfaces in a new package (e.g. `repository.mongo`) and instantiate them in `Main`.

## Commands

| Command | Permission | Description |
|---|---|---|
| `/ping` | everyone | Bot latency |
| `/config` | administrator | Channels (welcome, logs, deadlines, help), promos, subjects, member role, selectable roles, `show` |
| `/question`, `/resolu` | everyone | Ask for help in a dedicated thread, close it once solved |
| `/rules-panel` | administrator | Post the rules (`src/main/resources/rules.txt`) with an accept button |
| `/roles-panel` | administrator | Post the role selection menu |
| `/announce` | manage messages | Publish an announcement, with an optional role mention |
| `/clear` | manage messages | Delete the latest messages of the channel |
| `/deadline add`, `/deadline remove` | manage messages | Manage deadlines (date format `JJ/MM/AAAA HH:mm`, Paris time) |
| `/deadlines` | everyone | Show the upcoming deadlines |
| `/poll create` | everyone | Poll with 2 to 5 options and an optional automatic closing (hours). One vote per person, click again to remove it |
| `/poll close` | author or manage messages | Close a poll and freeze its results |
| `/event create` | everyone | Organize an outing: title, date (`JJ/MM/AAAA HH:mm`), optional place and description. Members answer with "Je peux", "Peut-être" or "Je peux pas" |
| *(automatic)* | | "Me rappeler" button of an event: one hour before it starts, the bot replies to the event and pings only the members who asked for it |
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
