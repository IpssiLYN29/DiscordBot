package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.ScheduleEvent;

import java.time.Instant;
import java.util.List;

public interface ScheduleRepository {

    void replaceAll(long guildId, List<ScheduleEvent> events);

    List<ScheduleEvent> findPendingReminders(Instant after, Instant until);

    List<ScheduleEvent> findStartingBetween(long guildId, Instant from, Instant until);

    List<ScheduleEvent> findAll(long guildId);

    void markReminded(long guildId, String uid);
}
