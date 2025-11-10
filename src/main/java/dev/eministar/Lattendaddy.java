package dev.eministar;

import dev.eministar.command.Command;
import dev.eministar.command.CommandManager;
import dev.eministar.config.Config;
import dev.eministar.modules.ModuleLoader;
import dev.eministar.modules.goodbye.GoodbyeListener;
import dev.eministar.modules.welcome.WelcomeListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Lattendaddy {
    private static final Logger logger = LoggerFactory.getLogger(Lattendaddy.class);

    public static void main(String[] args) {
        logger.info("Starte Lattendaddy...");

        if (Config.needsSetup()) {
            logger.info("Setup erforderlich. Starte Wizard...");
            Config.runSetupWizard();
            return; // Exit after setup
        }

        String token = Config.getToken();
        if (token == null || token.isEmpty()) {
            logger.error("Kein gültiger Token gefunden. Bitte führe den Setup erneut aus.");
            return;
        }

        try {
            // FlagQuiz Persistenz laden
            dev.eministar.modules.flags.FlagQuizService.load();
            // DPQ Zustand laden (laufende Nummer etc.)
            dev.eministar.modules.dpq.DpqService.loadState();

            JDABuilder builder = JDABuilder.createDefault(token);
            builder.enableIntents(
                    GatewayIntent.GUILD_MESSAGES,
                    GatewayIntent.MESSAGE_CONTENT,
                    GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_VOICE_STATES,
                    GatewayIntent.GUILD_PRESENCES
            );
            // WICHTIG: Präsenzdaten und vollständiges Member-Chaunking aktivieren, sonst bleibt Online-Count 0
            builder.enableCache(CacheFlag.ONLINE_STATUS);
            builder.setMemberCachePolicy(MemberCachePolicy.ALL);
            builder.setChunkingFilter(ChunkingFilter.ALL);

            CommandManager manager = new CommandManager(Config.getPrefix());

            // Load modules automatically
            Set<Class<? extends Command>> commandClasses = ModuleLoader.loadCommands();
            for (Class<? extends Command> clazz : commandClasses) {
                try {
                    Command cmd = clazz.getDeclaredConstructor().newInstance();
                    manager.register(cmd);
                    logger.info("Command geladen: {}", cmd.name());
                } catch (Exception e) {
                    logger.error("Fehler beim Laden des Commands: {}", clazz.getSimpleName(), e);
                }
            }

            // Register core listeners (welcome/goodbye)
            WelcomeListener welcome = new WelcomeListener();
            GoodbyeListener goodbye = new GoodbyeListener();
            dev.eministar.modules.birthday.BirthdayListener birthday = new dev.eministar.modules.birthday.BirthdayListener();
            dev.eministar.modules.ticket.TicketListener ticket = new dev.eministar.modules.ticket.TicketListener();
            dev.eministar.modules.suggestion.SuggestionListener suggestion = new dev.eministar.modules.suggestion.SuggestionListener();
            dev.eministar.modules.tempvoice.TempVoiceModule tempVoice = new dev.eministar.modules.tempvoice.TempVoiceModule();
            dev.eministar.modules.hymn.HymnModule hymn = new dev.eministar.modules.hymn.HymnModule();
            dev.eministar.modules.counting.CountingListener counting = new dev.eministar.modules.counting.CountingListener();
            dev.eministar.modules.misc.PingReactionListener pingReaction = new dev.eministar.modules.misc.PingReactionListener();
            dev.eministar.modules.channelcounts.ChannelCountListener channelCounts = new dev.eministar.modules.channelcounts.ChannelCountListener();
            dev.eministar.modules.flags.FlagQuizListener flagQuiz = new dev.eministar.modules.flags.FlagQuizListener();
            dev.eministar.modules.dpq.DpqAnswerListener dpqAnswer = new dev.eministar.modules.dpq.DpqAnswerListener();
            builder.addEventListeners(manager, welcome, goodbye, birthday, ticket, suggestion, tempVoice, hymn, counting, pingReaction, channelCounts, flagQuiz, dpqAnswer);

            JDA jda = builder.build().awaitReady();
            manager.registerToJda(jda);

            // FlagQuiz Dashboard sicherstellen
            dev.eministar.modules.flags.FlagQuizService.bootstrapDashboards(jda);

            // Auto-start Hymn module if enabled
            if (Config.getHymnEnabled()) {
                String guildId = Config.getGuildId();
                if (!guildId.isEmpty()) {
                    try {
                        hymn.startPlaying(jda.getGuildById(guildId));
                        logger.info("Auto-started Hymn module");
                    } catch (Exception e) {
                        logger.error("Failed to auto-start Hymn module", e);
                    }
                }
            }

            logger.info("Lattendaddy erfolgreich gestartet mit {} Commands.", commandClasses.size());
        } catch (Exception e) {
            logger.error("Fehler beim Starten des Bots", e);
        }
    }
}