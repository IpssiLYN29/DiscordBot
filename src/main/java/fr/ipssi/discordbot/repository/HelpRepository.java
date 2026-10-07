package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.HelpQuestion;

import java.util.Optional;

public interface HelpRepository {

    void save(HelpQuestion question);

    Optional<HelpQuestion> find(long threadId);

    void markResolved(long threadId);
}
