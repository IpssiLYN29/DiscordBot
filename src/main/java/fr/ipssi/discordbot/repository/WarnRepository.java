package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Warn;

import java.util.List;

public interface WarnRepository {

    void add(Warn warn);

    List<Warn> findByMember(long guildId, long userId);
}
