package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Meetup;
import fr.ipssi.discordbot.model.MeetupStatus;
import fr.ipssi.discordbot.model.RsvpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface MeetupRepository {

    long create(Meetup meetup);

    void attachMessage(long meetupId, long channelId, long messageId);

    Optional<Meetup> find(long meetupId);

    List<Meetup> findUpcoming(long guildId, Instant now);

    List<Meetup> findStarted(Instant now);

    List<Meetup> findPendingReminders(Instant after, Instant until);

    void markReminded(long meetupId);

    void updateStatus(long meetupId, MeetupStatus status);

    Optional<RsvpStatus> findRsvp(long meetupId, long userId);

    void saveRsvp(long meetupId, long userId, RsvpStatus status);

    void removeRsvp(long meetupId, long userId);

    Map<RsvpStatus, List<Long>> findRsvps(long meetupId);
}
