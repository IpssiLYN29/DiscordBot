package fr.ipssi.discordbot.service;

import fr.ipssi.discordbot.model.ScheduleEvent;
import fr.ipssi.discordbot.util.TimeFormats;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ScheduleChangeService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleChangeService.class);
    private static final Color CHANGE_COLOR = new Color(0xFEE75C);
    private static final int MAX_CHANGES_LISTED = 40;
    private static final int MAX_FIELD_LENGTH = 1000;
    private static final String NONE = "aucun";

    private final PlanningTargetService targets;

    public ScheduleChangeService(PlanningTargetService targets) {
        this.targets = targets;
    }

    public void announce(Guild guild, ScheduleDiff diff) {
        if (diff.isEmpty()) {
            return;
        }

        for (PlanningTarget target : targets.targets(guild.getIdLong())) {
            ScheduleDiff relevant = diff.filter(target::matches);
            TextChannel channel = guild.getTextChannelById(target.channelId());
            if (relevant.isEmpty() || channel == null) {
                continue;
            }

            MessageCreateBuilder message = new MessageCreateBuilder()
                    .setContent(target.roleMention())
                    .setEmbeds(buildEmbed(relevant, target));
            channel.sendMessage(message.build()).queue(
                    null, error -> LOGGER.warn("Failed to announce schedule changes in {}", channel.getId(), error));
        }
    }

    private MessageEmbed buildEmbed(ScheduleDiff diff, PlanningTarget target) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Changements dans le planning" + target.titleSuffix())
                .setColor(CHANGE_COLOR);

        if (diff.size() > MAX_CHANGES_LISTED) {
            return embed.setDescription("Le planning a été fortement modifié (%d changements). Consulte le récap épinglé."
                    .formatted(diff.size())).build();
        }

        addField(embed, "Annulé", diff.removed().stream().map(ScheduleChangeService::describe).toList());
        addField(embed, "Modifié", diff.changed().stream().map(ScheduleChangeService::describeChange).toList());
        addField(embed, "Ajouté", diff.added().stream().map(ScheduleChangeService::describe).toList());
        return embed.build();
    }

    private static void addField(EmbedBuilder embed, String name, List<String> entries) {
        if (entries.isEmpty()) {
            return;
        }

        StringBuilder value = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            String entry = entries.get(i);
            if (value.length() + entry.length() + 1 > MAX_FIELD_LENGTH) {
                value.append("\n+").append(entries.size() - i).append(" autre(s)");
                break;
            }
            value.append(i == 0 ? "" : "\n").append(entry);
        }
        embed.addField(name + " (" + entries.size() + ")", value.toString(), false);
    }

    private static String describe(ScheduleEvent event) {
        return "%s · %s".formatted(slot(event), event.title());
    }

    private static String describeChange(ScheduleDiff.Change change) {
        ScheduleEvent before = change.before();
        ScheduleEvent after = change.after();

        List<String> lines = new ArrayList<>();
        lines.add("**%s** · %s".formatted(after.title(), TimeFormats.dayMonth(day(after))));
        if (!before.startsAt().equals(after.startsAt()) || !before.endsAt().equals(after.endsAt())) {
            lines.add("horaire : %s → %s".formatted(slot(before), slot(after)));
        }
        if (!before.title().equals(after.title())) {
            lines.add("intitulé : %s → %s".formatted(before.title(), after.title()));
        }
        if (!before.teacher().equals(after.teacher())) {
            lines.add("intervenant : %s → %s".formatted(orNone(before.teacher()), orNone(after.teacher())));
        }
        if (!before.location().equals(after.location())) {
            lines.add("salle : %s → %s".formatted(orNone(before.location()), orNone(after.location())));
        }
        if (!before.promos().equals(after.promos())) {
            lines.add("promos : %s → %s".formatted(promos(before.promos()), promos(after.promos())));
        }
        return String.join("\n", lines);
    }

    private static String slot(ScheduleEvent event) {
        return "%s %s–%s".formatted(
                TimeFormats.dayMonth(day(event)), TimeFormats.time(event.startsAt()), TimeFormats.time(event.endsAt()));
    }

    private static LocalDate day(ScheduleEvent event) {
        return event.startsAt().atZone(TimeFormats.ZONE).toLocalDate();
    }

    private static String orNone(String value) {
        return value.isBlank() ? NONE : value;
    }

    private static String promos(Set<String> promos) {
        return promos.isEmpty() ? "toutes" : String.join(", ", promos);
    }
}
