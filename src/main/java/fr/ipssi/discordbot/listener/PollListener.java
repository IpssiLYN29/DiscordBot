package fr.ipssi.discordbot.listener;

import fr.ipssi.discordbot.model.Poll;
import fr.ipssi.discordbot.repository.PollRepository;
import fr.ipssi.discordbot.service.PollService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class PollListener extends ListenerAdapter {

    private final PollRepository pollRepository;
    private final PollService pollService;

    public PollListener(PollRepository pollRepository, PollService pollService) {
        this.pollRepository = pollRepository;
        this.pollService = pollService;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith(PollService.BUTTON_PREFIX)) {
            return;
        }

        String[] parts = componentId.substring(PollService.BUTTON_PREFIX.length()).split(":");
        long pollId = Long.parseLong(parts[0]);
        int optionIndex = Integer.parseInt(parts[1]);

        Optional<Poll> poll = pollRepository.find(pollId).filter(found -> !found.closed());
        if (poll.isEmpty()) {
            event.reply("Ce sondage est terminé.").setEphemeral(true).queue();
            return;
        }

        long userId = event.getUser().getIdLong();
        if (pollRepository.findVote(pollId, userId).filter(current -> current == optionIndex).isPresent()) {
            pollRepository.removeVote(pollId, userId);
        } else {
            pollRepository.saveVote(pollId, userId, optionIndex);
        }
        event.editMessageEmbeds(pollService.render(poll.get())).queue();
    }
}
