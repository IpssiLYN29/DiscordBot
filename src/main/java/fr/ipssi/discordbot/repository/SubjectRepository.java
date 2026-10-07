package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Subject;

import java.util.List;
import java.util.Optional;

public interface SubjectRepository {

    void save(Subject subject);

    boolean remove(long guildId, String name);

    Optional<Subject> find(long guildId, String name);

    List<Subject> findAll(long guildId);
}
