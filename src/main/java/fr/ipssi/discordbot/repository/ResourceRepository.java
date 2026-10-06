package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Resource;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository {

    long add(Resource resource);

    Optional<Resource> find(long resourceId);

    Optional<Resource> findByUrl(long guildId, String url);

    List<Resource> search(long guildId, String query, String tag, int limit);

    boolean remove(long guildId, long resourceId);
}
