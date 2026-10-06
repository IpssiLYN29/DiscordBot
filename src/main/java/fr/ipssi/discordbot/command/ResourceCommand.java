package fr.ipssi.discordbot.command;

import fr.ipssi.discordbot.model.Resource;
import fr.ipssi.discordbot.repository.ResourceRepository;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ResourceCommand implements Command {

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 20;
    private static final int MAX_RESULTS = 8;
    private static final int MAX_EMBED_LENGTH = 3800;

    private final ResourceRepository resourceRepository;

    public ResourceCommand(ResourceRepository resourceRepository) {
        this.resourceRepository = resourceRepository;
    }

    @Override
    public SlashCommandData definition() {
        return Commands.slash("resource", "Share and find useful resources")
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("add", "Share a resource")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "url", "Link (http or https)", true)
                                                .setMaxLength(250),
                                        new OptionData(OptionType.STRING, "title", "Short title", true)
                                                .setMaxLength(80),
                                        new OptionData(OptionType.STRING, "tags", "Comma-separated, e.g. cours, java, tuto")
                                                .setMaxLength(100),
                                        new OptionData(OptionType.STRING, "description", "What is it about?")
                                                .setMaxLength(100)),
                        new SubcommandData("search", "Search the shared resources")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "query", "Words to look for")
                                                .setMaxLength(50),
                                        new OptionData(OptionType.STRING, "tag", "Only this tag")
                                                .setMaxLength(MAX_TAG_LENGTH)),
                        new SubcommandData("remove", "Remove a resource")
                                .addOptions(new OptionData(OptionType.INTEGER, "id", "Resource number", true)
                                        .setMinValue(1)));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        switch (event.getSubcommandName()) {
            case "add" -> add(event);
            case "search" -> search(event);
            case "remove" -> remove(event);
            default -> event.reply("Sous-commande inconnue.").setEphemeral(true).queue();
        }
    }

    private void add(SlashCommandInteractionEvent event) {
        Optional<String> url = normalizeUrl(event.getOption("url", OptionMapping::getAsString));
        if (url.isEmpty()) {
            event.reply("Lien invalide : il doit commencer par http:// ou https://.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        Optional<Resource> existing = resourceRepository.findByUrl(guildId, url.get());
        if (existing.isPresent()) {
            event.reply("Cette ressource a déjà été partagée (#" + existing.get().id() + ").").setEphemeral(true).queue();
            return;
        }

        Resource draft = Resource.create(
                guildId,
                url.get(),
                event.getOption("title", OptionMapping::getAsString).trim(),
                event.getOption("description", "", OptionMapping::getAsString).trim(),
                parseTags(event.getOption("tags", "", OptionMapping::getAsString)),
                event.getUser().getIdLong());
        long id = resourceRepository.add(draft);

        event.reply("%s a partagé une ressource : %s `#%d`%s".formatted(
                event.getUser().getAsMention(), link(draft), id, tagsLine(draft))).queue();
    }

    private void search(SlashCommandInteractionEvent event) {
        String query = event.getOption("query", "", OptionMapping::getAsString).trim();
        String tag = normalizeTag(event.getOption("tag", "", OptionMapping::getAsString));

        List<Resource> results = resourceRepository.search(event.getGuild().getIdLong(), query, tag, MAX_RESULTS);
        if (results.isEmpty()) {
            event.reply("Aucune ressource trouvée.").setEphemeral(true).queue();
            return;
        }

        List<String> entries = new ArrayList<>();
        int length = 0;
        for (Resource resource : results) {
            String entry = format(resource);
            if (length + entry.length() > MAX_EMBED_LENGTH) {
                break;
            }
            entries.add(entry);
            length += entry.length() + 2;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Ressources")
                .setDescription(String.join("\n\n", entries));
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void remove(SlashCommandInteractionEvent event) {
        long id = event.getOption("id", OptionMapping::getAsLong);
        Optional<Resource> found = resourceRepository.find(id)
                .filter(resource -> resource.guildId() == event.getGuild().getIdLong());
        if (found.isEmpty()) {
            event.reply("Aucune ressource avec le numéro " + id + ".").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        boolean allowed = found.get().createdBy() == event.getUser().getIdLong()
                || (member != null && member.hasPermission(Permission.MESSAGE_MANAGE));
        if (!allowed) {
            event.reply("Seul l'auteur ou un modérateur peut supprimer cette ressource.").setEphemeral(true).queue();
            return;
        }

        resourceRepository.remove(event.getGuild().getIdLong(), id);
        event.reply("Ressource #" + id + " supprimée.").setEphemeral(true).queue();
    }

    private static Optional<String> normalizeUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            boolean valid = (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
            return valid ? Optional.of(uri.toString()) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static List<String> parseTags(String raw) {
        return Arrays.stream(raw.split(","))
                .map(ResourceCommand::normalizeTag)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .limit(MAX_TAGS)
                .toList();
    }

    private static String normalizeTag(String raw) {
        String tag = raw.trim().toLowerCase(Locale.ROOT).replace("#", "").replace(",", "");
        return tag.length() > MAX_TAG_LENGTH ? tag.substring(0, MAX_TAG_LENGTH) : tag;
    }

    private static String link(Resource resource) {
        String title = resource.title().replace('[', '(').replace(']', ')');
        String url = resource.url().replace("(", "%28").replace(")", "%29");
        return "**[%s](%s)**".formatted(title, url);
    }

    private static String tagsLine(Resource resource) {
        if (resource.tags().isEmpty()) {
            return "";
        }
        return "\n" + resource.tags().stream().map(tag -> "`#" + tag + "`").collect(Collectors.joining(" "));
    }

    private static String format(Resource resource) {
        StringBuilder entry = new StringBuilder(link(resource)).append(" `#").append(resource.id()).append('`');
        if (!resource.description().isBlank()) {
            entry.append('\n').append(resource.description());
        }
        entry.append(tagsLine(resource).isEmpty() ? "\n" : tagsLine(resource) + " · ");
        entry.append("par <@").append(resource.createdBy()).append('>');
        return entry.toString();
    }
}
