package fr.ipssi.discordbot.repository;

import fr.ipssi.discordbot.model.Promo;

import java.util.List;

public interface PromoRepository {

    void save(Promo promo);

    boolean remove(long guildId, String code);

    List<Promo> findAll(long guildId);
}
