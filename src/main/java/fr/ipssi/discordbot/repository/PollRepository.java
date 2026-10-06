package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Poll;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PollRepository {

    long create(Poll poll);

    void attachMessage(long pollId, long channelId, long messageId);

    Optional<Poll> find(long pollId);

    List<Poll> findDue(Instant now);

    void close(long pollId);

    Optional<Integer> findVote(long pollId, long userId);

    void saveVote(long pollId, long userId, int optionIndex);

    void removeVote(long pollId, long userId);

    List<Integer> countVotes(long pollId, int optionCount);
}
