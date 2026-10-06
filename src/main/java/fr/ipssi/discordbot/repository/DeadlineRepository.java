package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Deadline;

import java.time.Instant;
import java.util.List;

public interface DeadlineRepository {

    long add(Deadline deadline);

    boolean remove(long guildId, long id);

    List<Deadline> findUpcoming(long guildId, Instant now);

    List<Deadline> findAllUpcoming(Instant now);

    void updateLastReminder(long id, int days);
}
