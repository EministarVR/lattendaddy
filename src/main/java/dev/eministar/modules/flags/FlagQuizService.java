package dev.eministar.modules.flags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.text.Normalizer;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import dev.eministar.util.EmojiUtil;

/**
 * Zentrale Logik für das Flaggenquiz: Rundenzustand, Punkte, Streaks, Leaderboard, Channel-Bindung und Persistenz.
 */
public class FlagQuizService {
    private static final Logger logger = LoggerFactory.getLogger(FlagQuizService.class);

    // Spielkonstanten
    public static final int TIME_LIMIT_SECONDS = 30;
    public static final int POINTS_NORMAL = 10;
    public static final int POINTS_EASY = 8; // etwas weniger wegen Multiple-Choice
    public static final int POINTS_DAILY_BONUS = 15; // zusätzlich zu NORMAL

    private static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);

    // Datenhaltung pro Guild
    private static final Map<String, GuildData> guilds = new ConcurrentHashMap<>();

    // Persistenz
    private static final File DATA_FILE = new File("flagquiz-stats.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Alias für häufige Sonderfälle
    private static final Map<String, String> ALIAS_TO_CODE = buildAliasMap();

    // ISO-Länderliste (alpha-2)
    private static final List<String> ISO_CODES = List.of(Locale.getISOCountries());

    private static final Map<String, Long> lastDashboardUpdate = new ConcurrentHashMap<>();

    public enum Mode { NORMAL, EASY, DAILY }

    public record ActiveRound(String guildId,
                              String channelId,
                              Mode mode,
                              String code,
                              Set<String> acceptedAnswers,
                              Map<String, String> buttonIdToCode,
                              long messageId,
                              long startEpochMillis,
                              ScheduledFuture<?> timeoutTask,
                              Set<String> answeredUsers,
                              String targetUserId) {
    }

    public static class PlayerStats {
        public int totalPoints;
        public int correct;
        public int wrong;
        public int currentStreak;
        public int bestStreak;
        public String lastDaily; // yyyy-MM-dd
        public Set<String> achievements = new HashSet<>();
    }

    public static class FlagStats {
        public int asked;
        public int correct;
        public int wrong;
    }

    public static class GuildData {
        // pro Channel mehrere Runden: je Ziel-User eine Runde
        public Map<String, Map<String, ActiveRound>> roundsByChannel = new ConcurrentHashMap<>();
        public Map<String, PlayerStats> statsByUser = new ConcurrentHashMap<>();
        public String quizChannelId = null; // in welchem Kanal gespielt wird
        public Long dashboardMessageId = null; // persistente Dashboard-Nachricht
        public Map<String, FlagStats> flagStats = new ConcurrentHashMap<>(); // pro ISO-Code
    }

    // ---- Initialisierung / Persistenz ----

    public static void load() {
        if (!DATA_FILE.exists()) return;
        try (FileReader fr = new FileReader(DATA_FILE)) {
            Type type = new TypeToken<Map<String, GuildData>>() {}.getType();
            Map<String, GuildData> loaded = GSON.fromJson(fr, type);
            if (loaded != null) {
                guilds.clear();
                guilds.putAll(loaded);
            }
            logger.info("FlagQuiz: Stats geladen ({} Guilds)", guilds.size());
        } catch (Exception e) {
            logger.error("FlagQuiz: Konnte Stats nicht laden", e);
        }
    }

    public static void saveAsync() {
        scheduler.schedule(FlagQuizService::saveNow, 500, TimeUnit.MILLISECONDS);
    }

    private static synchronized void saveNow() {
        try (FileWriter fw = new FileWriter(DATA_FILE)) {
            GSON.toJson(guilds, fw);
        } catch (Exception e) {
            logger.error("FlagQuiz: Speichern fehlgeschlagen", e);
        }
    }

    private static GuildData gd(String guildId) {
        return guilds.computeIfAbsent(guildId, k -> new GuildData());
    }

    public static PlayerStats stats(String guildId, String userId) {
        return gd(guildId).statsByUser.computeIfAbsent(userId, k -> new PlayerStats());
    }

    private static FlagStats flagStats(String guildId, String code) {
        return gd(guildId).flagStats.computeIfAbsent(code, k -> new FlagStats());
    }

    // ---- Channel-Bindung / Dashboard ----

    public static void setQuizChannel(String guildId, String channelId) {
        GuildData g = gd(guildId);
        g.quizChannelId = channelId;
        saveAsync();
    }

    public static String getQuizChannelId(String guildId) {
        GuildData g = gd(guildId);
        return g.quizChannelId;
    }

    public static void setDashboardMessageId(String guildId, Long messageId) {
        GuildData g = gd(guildId);
        g.dashboardMessageId = messageId;
        saveAsync();
    }

    public static Long getDashboardMessageId(String guildId) {
        GuildData g = gd(guildId);
        return g.dashboardMessageId;
    }

    public static void bootstrapDashboards(JDA jda) {
        for (Map.Entry<String, GuildData> e : guilds.entrySet()) {
            String guildId = e.getKey();
            GuildData g = e.getValue();
            if (g.quizChannelId == null) continue;
            var guild = jda.getGuildById(guildId);
            if (guild == null) continue;
            TextChannel channel = guild.getTextChannelById(g.quizChannelId);
            if (channel == null) continue;
            tryEnsureDashboardMessage(guildId, channel);
        }
    }

    public static void tryEnsureDashboardMessage(String guildId, TextChannel channel) {
        long now = System.currentTimeMillis();
        long last = lastDashboardUpdate.getOrDefault(guildId, 0L);
        if (now - last < 5000) {
            return; // Throttle Updates auf max. alle 5s
        }
        lastDashboardUpdate.put(guildId, now);
        Long mid = getDashboardMessageId(guildId);
        if (mid != null) {
            channel.retrieveMessageById(mid).queue(
                    msg -> {
                        // aktualisieren
                        msg.editMessageEmbeds(buildDashboardEmbed(channel.getJDA(), guildId).build())
                                .setActionRow(dashboardButtons())
                                .queue();
                    },
                    err -> {
                        // neu senden
                        sendDashboard(guildId, channel);
                    }
            );
        } else {
            sendDashboard(guildId, channel);
        }
    }

    private static void sendDashboard(String guildId, TextChannel channel) {
        var action = channel.sendMessageEmbeds(buildDashboardEmbed(channel.getJDA(), guildId).build())
                .setActionRow(dashboardButtons());
        action.queue(msg -> {
            setDashboardMessageId(guildId, msg.getIdLong());
            // optional pin
            msg.pin().queue(null, t -> {});
        });
    }

    private static EmbedBuilder buildDashboardEmbed(JDA jda, String guildId) {
        String prefix = dev.eministar.config.Config.getPrefix();
        SelfUser self = jda.getSelfUser();
        String avatar = self.getEffectiveAvatarUrl();
        GuildData g = gd(guildId);

        // Live-Stats
        int uniquePlayers = g.statsByUser.size();
        int totalAsked = g.flagStats.values().stream().mapToInt(fs -> fs.asked).sum();
        int bestStreak = g.statsByUser.values().stream().mapToInt(ps -> ps.bestStreak).max().orElse(0);
        var topEntry = g.statsByUser.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().totalPoints)).orElse(null);
        String topLine;
        if (topEntry != null) {
            var topUser = jda.getUserById(topEntry.getKey());
            String topName = topUser != null ? topUser.getName() : ("<" + topEntry.getKey() + ">");
            topLine = "#1 " + topName + " — " + topEntry.getValue().totalPoints + " Punkte";
        } else {
            topLine = "Noch kein Leader";
        }

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x202225));
        eb.setAuthor("Flaggenquiz", null, avatar);
        eb.setTitle("🏴‍☠️ Dashboard");
        eb.setDescription(EmojiUtil.wrap("🧠") + "Antworte mit dem Ländernamen (Deutsch oder Englisch).\n" +
                EmojiUtil.wrap("⏱️") + "Du hast " + TIME_LIMIT_SECONDS + " Sekunden pro Runde.\n" +
                EmojiUtil.wrap("🏅") + "Sammle Streaks & Achievements für Belohnungen.");

        String cmds = String.join("\n", List.of(
                EmojiUtil.wrap("🚩") + "**" + prefix + "flagge** — Neues Flaggenrätsel",
                EmojiUtil.wrap("✨") + "**" + prefix + "flagge easy** — Einfache Runde (Multiple Choice)",
                EmojiUtil.wrap("📆") + "**" + prefix + "dailyflag** — Tägliche Challenge (1x/Tag)",
                EmojiUtil.wrap("🏆") + "**" + prefix + "leaderboard** — Beste Flaggenkenner",
                EmojiUtil.wrap("🔥") + "**" + prefix + "streaks** — Aktuelle Streaks"
        ));
        eb.addField("Befehle", cmds, false);

        String stats = String.join("\n", List.of(
                EmojiUtil.wrap("👥") + "Spieler: **" + uniquePlayers + "**",
                EmojiUtil.wrap("🎮") + "Runden insgesamt: **" + totalAsked + "**",
                EmojiUtil.wrap("⚡") + "Beste Streak: **" + bestStreak + "**",
                EmojiUtil.wrap("🥇") + topLine
        ));
        eb.addField("Live-Stats", stats, false);

        eb.setFooter("Prefix: " + prefix + " • Viel Erfolg!", avatar);
        eb.setTimestamp(java.time.Instant.now());
        return eb;
    }

    private static List<Button> dashboardButtons() {
        return List.of(
                Button.success("flag-act-start", "Neues Rätsel"),
                Button.primary("flag-act-easy", "Easy"),
                Button.primary("flag-act-daily", "Daily"),
                Button.secondary("flag-act-leaderboard", "Leaderboard"),
                Button.secondary("flag-act-streaks", "Streaks")
        );
    }

    // ---- Rundensteuerung ----

    // Helpers für Rundenzugriff
    private static Map<String, ActiveRound> channelRounds(GuildData data, String channelId) {
        return data.roundsByChannel.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
    }

    private static ActiveRound getRound(GuildData data, String channelId, String userId) {
        return channelRounds(data, channelId).get(userId);
    }

    private static void putRound(GuildData data, ActiveRound round) {
        channelRounds(data, round.channelId).put(round.targetUserId, round);
    }

    private static void removeRound(GuildData data, String channelId, String userId) {
        Map<String, ActiveRound> map = data.roundsByChannel.get(channelId);
        if (map != null) map.remove(userId);
    }

    public static synchronized boolean startRound(String guildId, String channelId, MessageChannel channel, Mode mode, String targetUserId) {
        GuildData data = gd(guildId);
        // Channel-Check
        if (data.quizChannelId != null && !Objects.equals(data.quizChannelId, channelId)) {
            channel.sendMessage(EmojiUtil.wrap("❌") + "Dieses Spiel ist in diesem Kanal nicht aktiv. Bitte nutze den vorgesehenen Kanal.")
                    .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS, s -> {}, f -> {}));
            return false;
        }

        // Prüfe nur pro Zielspieler in diesem Channel
        if (getRound(data, channelId, targetUserId) != null) {
            channel.sendMessage(EmojiUtil.wrap("ℹ️") + "Für dich läuft in diesem Kanal bereits eine Runde.")
                    .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS, s -> {}, f -> {}));
            return false;
        }
        String code = (mode == Mode.DAILY) ? dailyCodeForGuild(guildId, LocalDate.now(ZoneOffset.UTC)) : randomCode();
        Set<String> accepted = buildAcceptedAnswers(code);

        // Flaggen-Stat updaten (gestellt)
        FlagStats fs = flagStats(guildId, code);
        fs.asked++;
        saveAsync();

        Map<String, String> buttons = null;
        if (mode == Mode.EASY) {
            // 3 Distraktoren + richtige Antwort
            List<String> options = new ArrayList<>();
            options.add(code);
            while (options.size() < 4) {
                String c = randomCode();
                if (!options.contains(c)) options.add(c);
            }
            Collections.shuffle(options);
            buttons = new LinkedHashMap<>();
            for (String opt : options) {
                String btnId = "flag-easy-" + UUID.randomUUID();
                buttons.put(btnId, opt);
            }
        }

        // Nachricht absenden
        String flag = flagEmojiFor(code);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x2B2D31));
        eb.setAuthor("Flaggenquiz", null, channel.getJDA().getSelfUser().getEffectiveAvatarUrl());
        eb.setTitle(EmojiUtil.wrap("🎯") + "Errate die Flagge");
        eb.setDescription("Antworte mit dem Ländernamen (Deutsch oder Englisch).\nZeit: " + TIME_LIMIT_SECONDS + "s");
        eb.setImage(flagImageUrl(code));
        eb.addField("Für", "<@" + targetUserId + ">", true);
        eb.addField("Flagge", flag, true);
        String hist = "Gestellt: **" + fs.asked + "** • Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**";
        eb.addField("Bisher", hist, false);
        eb.setFooter("Nur die Antworten von <@" + targetUserId + "> zählen • Nachricht wird gelöscht", null);
        eb.setTimestamp(java.time.Instant.now());
        var action = channel.sendMessageEmbeds(eb.build());
        if (buttons != null) {
            List<Button> btns = new ArrayList<>();
            for (Map.Entry<String, String> e : buttons.entrySet()) {
                String optCode = e.getValue();
                String label = countryName(optCode, Locale.GERMAN);
                btns.add(Button.primary(e.getKey(), label));
            }
            action = action.setActionRow(btns);
        }

        long start = System.currentTimeMillis();
        action.queue(msg -> msg.delete().queueAfter(TIME_LIMIT_SECONDS, TimeUnit.SECONDS, s -> {}, f -> {}));

        // Timeout task -> muss gezielt die Runde dieses Users schließen
        ScheduledFuture<?> future = scheduler.schedule(() -> endRoundTimeout(guildId, channelId, targetUserId, channel, code), TIME_LIMIT_SECONDS, TimeUnit.SECONDS);

        // Runde registrieren, ohne blockierend zu warten
        ActiveRound round = new ActiveRound(
                guildId,
                channelId,
                mode,
                code,
                accepted,
                buttons,
                0L,
                start,
                future,
                ConcurrentHashMap.newKeySet(),
                targetUserId
        );
        putRound(data, round);
        return true;
    }

    private static void endRoundTimeout(String guildId, String channelId, String targetUserId, MessageChannel channel, String code) {
        GuildData data = gd(guildId);
        ActiveRound round = getRound(data, channelId, targetUserId);
        if (round == null) return;
        removeRound(data, channelId, targetUserId);
        // Flaggenstats: falsch erhöhen (Timeout)
        FlagStats fs = flagStats(guildId, code);
        fs.wrong++;
        saveAsync();
        String answer = countryName(code, Locale.GERMAN) + " / " + countryName(code, Locale.ENGLISH);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0xED4245));
        eb.setAuthor("Flaggenquiz", null, channel.getJDA().getSelfUser().getEffectiveAvatarUrl());
        eb.setTitle(EmojiUtil.wrap("⏱️") + "Zeit abgelaufen!");
        eb.setDescription("Richtige Antwort: **" + answer + "**");
        eb.setImage(flagImageUrl(code));
        String hist = "Gestellt: **" + fs.asked + "** • Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**";
        eb.addField("Bisher", hist, false);
        eb.setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(eb.build())
                .queue(m -> m.delete().queueAfter(30, java.util.concurrent.TimeUnit.SECONDS, s -> {}, f -> {}));
        if (channel instanceof TextChannel tc) tryEnsureDashboardMessage(guildId, tc);
    }

    public static void handleMessageAnswer(String guildId, String channelId, MessageChannel channel, Member member, String contentRaw) {
        GuildData data = gd(guildId);
        if (member == null || member.getUser().isBot()) return;
        ActiveRound round = getRound(data, channelId, member.getId());
        if (round == null) return; // nur Zielspieler

        String normalized = normalize(contentRaw);
        if (matches(round, normalized)) {
            if (!round.answeredUsers.add(member.getId())) { return; }
            finishRoundWin(data, round, channel, member.getUser());
        } else {
            if (!round.answeredUsers.add(member.getId())) { return; }
            finishRoundLose(data, round, channel, member.getUser());
        }
    }

    public static void handleButton(String guildId, String channelId, MessageChannel channel, Member member, String buttonId) {
        GuildData data = gd(guildId);
        if (buttonId.startsWith("flag-act-")) {
            if (member == null || member.getUser().isBot()) return;
            switch (buttonId) {
                case "flag-act-start" -> startRound(guildId, channelId, channel, Mode.NORMAL, member.getId());
                case "flag-act-easy" -> startRound(guildId, channelId, channel, Mode.EASY, member.getId());
                case "flag-act-daily" -> {
                    if (canDoDaily(guildId, member.getId())) {
                        startRound(guildId, channelId, channel, Mode.DAILY, member.getId());
                    } else {
                        channel.sendMessage(EmojiUtil.wrap("❌") + "Du hast die tägliche Challenge heute bereits gespielt, " + member.getAsMention() + ".")
                                .queue(m -> m.delete().queueAfter(10, TimeUnit.SECONDS, s -> {}, f -> {}));
                    }
                }
                case "flag-act-leaderboard" -> {
                    var eb = new EmbedBuilder();
                    eb.setColor(new Color(0x5865F2));
                    eb.setTitle(EmojiUtil.wrap("🏆") + "Leaderboard");
                    eb.setDescription(leaderboardText(guildId, channel.getJDA(), 10));
                    channel.sendMessageEmbeds(eb.build()).queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS, s -> {}, f -> {}));
                }
                case "flag-act-streaks" -> {
                    var eb = new EmbedBuilder();
                    eb.setColor(Color.ORANGE);
                    eb.setTitle(EmojiUtil.wrap("🔥") + "Streaks");
                    eb.setDescription(streaksText(guildId, channel.getJDA(), 10));
                    channel.sendMessageEmbeds(eb.build()).queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS, s -> {}, f -> {}));
                }
            }
            return;
        }

        // Easy-Buttons: nur Zielspieler, und nur seine Runde im Channel
        if (member == null || member.getUser().isBot()) return;
        ActiveRound round = getRound(data, channelId, member.getId());
        if (round == null) return;
        if (round.buttonIdToCode == null) return;
        String chosen = round.buttonIdToCode.get(buttonId);
        if (chosen == null) return;

        if (!round.answeredUsers.add(member.getId())) { return; }
        if (round.code.equalsIgnoreCase(chosen)) {
            finishRoundWin(data, round, channel, member.getUser());
        } else {
            finishRoundLose(data, round, channel, member.getUser());
        }
    }

    private static void finishRoundLose(GuildData data, ActiveRound round, MessageChannel channel, User offender) {
        if (round.timeoutTask != null) round.timeoutTask.cancel(false);
        removeRound(data, round.channelId, round.targetUserId);
        PlayerStats ps = stats(round.guildId, offender.getId());
        ps.currentStreak = 0;
        ps.wrong++;
        FlagStats fs = flagStats(round.guildId, round.code);
        fs.wrong++;
        saveAsync();

        String answer = countryName(round.code, Locale.GERMAN) + " / " + countryName(round.code, Locale.ENGLISH);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0xED4245));
        eb.setAuthor("Flaggenquiz", null, channel.getJDA().getSelfUser().getEffectiveAvatarUrl());
        eb.setTitle(EmojiUtil.wrap("❌") + "Falsche Antwort – Runde beendet");
        eb.setDescription(offender.getAsMention() + " lag daneben.\nRichtige Antwort: **" + answer + "**");
        eb.setImage(flagImageUrl(round.code));
        String hist = "Gestellt: **" + fs.asked + "** • Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**";
        eb.addField("Bisher", hist, false);
        eb.setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(eb.build()).queue(m -> m.delete().queueAfter(30, java.util.concurrent.TimeUnit.SECONDS, s -> {}, f -> {}));
        if (channel instanceof TextChannel tc) tryEnsureDashboardMessage(round.guildId, tc);
    }

    private static void finishRoundWin(GuildData data, ActiveRound round, MessageChannel channel, User winner) {
        if (round.timeoutTask != null) round.timeoutTask.cancel(false);
        removeRound(data, round.channelId, round.targetUserId);

        PlayerStats ps = stats(round.guildId, winner.getId());
        int gained = switch (round.mode) {
            case EASY -> POINTS_EASY;
            case DAILY -> POINTS_NORMAL + POINTS_DAILY_BONUS;
            default -> POINTS_NORMAL;
        };
        if (round.mode == Mode.DAILY) {
            ps.lastDaily = LocalDate.now(ZoneOffset.UTC).toString();
        }
        ps.totalPoints += gained;
        ps.correct++;
        ps.currentStreak++;
        ps.bestStreak = Math.max(ps.bestStreak, ps.currentStreak);
        FlagStats fs = flagStats(round.guildId, round.code);
        fs.correct++;
        checkAchievements(ps, channel, winner);
        saveAsync();

        String answer = countryName(round.code, Locale.GERMAN) + " / " + countryName(round.code, Locale.ENGLISH);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x57F287));
        eb.setAuthor("Flaggenquiz", null, channel.getJDA().getSelfUser().getEffectiveAvatarUrl());
        eb.setTitle(EmojiUtil.wrap("✅") + "Richtig!");
        eb.setDescription(winner.getAsMention() + " hat korrekt geantwortet.\n" +
                "Lösung: **" + answer + "**\n" +
                "Punkte: +" + gained + " (Streak: " + ps.currentStreak + ")");
        eb.setImage(flagImageUrl(round.code));
        String hist = "Gestellt: **" + fs.asked + "** • Richtig: **" + fs.correct + "** • Falsch: **" + fs.wrong + "**";
        eb.addField("Bisher", hist, false);
        eb.setTimestamp(java.time.Instant.now());
        channel.sendMessageEmbeds(eb.build()).queue(m -> m.delete().queueAfter(30, java.util.concurrent.TimeUnit.SECONDS, s -> {}, f -> {}));
        if (channel instanceof TextChannel tc) tryEnsureDashboardMessage(round.guildId, tc);
    }

    // ---- Helper ----

    private static boolean matches(ActiveRound round, String normalizedUser) {
        if (round == null) return false;
        if (round.acceptedAnswers.contains(normalizedUser)) return true;
        // auch ISO Code direkt erlauben (z.B. DE, US)
        return normalizedUser.equalsIgnoreCase(round.code);
    }

    private static Set<String> buildAcceptedAnswers(String code) {
        Set<String> set = new HashSet<>();
        String de = countryName(code, Locale.GERMAN);
        String en = countryName(code, Locale.ENGLISH);
        set.add(normalize(de));
        set.add(normalize(en));
        // häufige Aliase für diesen Code
        for (Map.Entry<String, String> e : ALIAS_TO_CODE.entrySet()) {
            if (e.getValue().equalsIgnoreCase(code)) {
                set.add(normalize(e.getKey()));
            }
        }
        return set;
    }

    public static String countryName(String iso2, Locale locale) {
        try {
            return new Locale("", iso2).getDisplayCountry(locale);
        } catch (Exception e) {
            return iso2.toUpperCase(Locale.ROOT);
        }
    }

    public static String normalize(String s) {
        String x = s.trim().toLowerCase(Locale.ROOT);
        x = Normalizer.normalize(x, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        x = x.replaceAll("[^a-z0-9 ]", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private static String randomCode() {
        return ISO_CODES.get(ThreadLocalRandom.current().nextInt(ISO_CODES.size()));
    }

    private static String dailyCodeForGuild(String guildId, LocalDate date) {
        int base = Math.abs(Objects.hash(guildId, date.toString()));
        return ISO_CODES.get(base % ISO_CODES.size());
    }

    public static boolean canDoDaily(String guildId, String userId) {
        PlayerStats ps = stats(guildId, userId);
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        return ps.lastDaily == null || !ps.lastDaily.equals(today);
    }


    public static String flagEmojiFor(String iso2) {
        // Wandelt ISO2 (A-Z) in Regional Indicator Symbols um
        String up = iso2.toUpperCase(Locale.ROOT);
        if (up.length() != 2) return iso2;
        int base = 0x1F1E6;
        int c1 = base + (up.charAt(0) - 'A');
        int c2 = base + (up.charAt(1) - 'A');
        return new String(Character.toChars(c1)) + new String(Character.toChars(c2));
    }

    private static String flagImageUrl(String iso2) {
        // Verlässlich: flagcdn.com, ISO2 lower-case, größere Auflösung
        String c = iso2.toLowerCase(java.util.Locale.ROOT);
        return "https://flagcdn.com/w1024/" + c + ".png";
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> m = new HashMap<>();
        // USA / Vereinigtes Königreich / etc.
        m.put("usa", "US");
        m.put("u s a", "US");
        m.put("united states", "US");
        m.put("vereinigte staaten", "US");
        m.put("vereinigte staaten von amerika", "US");
        m.put("america", "US");
        m.put("amerika", "US");

        m.put("uk", "GB");
        m.put("u k", "GB");
        m.put("united kingdom", "GB");
        m.put("great britain", "GB");
        m.put("grossbritannien", "GB");
        m.put("großbritannien", "GB");
        m.put("england", "GB"); // toleriert oft

        m.put("south korea", "KR");
        m.put("sudkorea", "KR");
        m.put("südkorea", "KR");
        m.put("north korea", "KP");
        m.put("nordkorea", "KP");

        m.put("russia", "RU");
        m.put("russland", "RU");

        m.put("czechia", "CZ");
        m.put("tschechien", "CZ");

        m.put("the netherlands", "NL");
        m.put("niederlande", "NL");
        m.put("holland", "NL");

        m.put("ivory coast", "CI");
        m.put("cote d ivoire", "CI");

        m.put("vatican", "VA");
        m.put("vatikan", "VA");

        m.put("cape verde", "CV");
        m.put("kap verde", "CV");

        m.put("u a e", "AE");
        m.put("uae", "AE");
        m.put("vereinigte arabische emirate", "AE");
        m.put("united arab emirates", "AE");

        m.put("dr congo", "CD");
        m.put("demokratische republik kongo", "CD");
        m.put("republic of the congo", "CG");
        m.put("kongo", "CG");

        m.put("laos", "LA");
        m.put("myanmar", "MM");
        m.put("burma", "MM");

        m.put("timor leste", "TL");
        m.put("osttimor", "TL");

        return m;
    }

    private static void checkAchievements(PlayerStats ps, MessageChannel channel, User user) {
        int[] thresholds = {5, 10, 25, 50};
        for (int t : thresholds) {
            String key = "streak-" + t;
            if (ps.currentStreak >= t && ps.achievements.add(key)) {
                channel.sendMessage(EmojiUtil.wrap("🏅") + "Achievement freigeschaltet für " + user.getAsMention() + ": " + t + "-er Streak!")
                        .queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS, s -> {}, f -> {}));
            }
        }
    }

    // ---- Leaderboards / Streaks ----

    public static String leaderboardText(String guildId, JDA jda, int limit) {
        Map<String, PlayerStats> map = gd(guildId).statsByUser;
        List<Map.Entry<String, PlayerStats>> top = map.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue().totalPoints, a.getValue().totalPoints))
                .limit(limit)
                .toList();
        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (var e : top) {
            String userId = e.getKey();
            PlayerStats ps = e.getValue();
            User u = jda.getUserById(userId);
            String name = u != null ? u.getName() : ("<" + userId + ">");
            sb.append("#").append(rank++).append(" ").append(name)
                    .append(" — ").append(ps.totalPoints).append(" Punkte").append("\n");
        }
        if (sb.isEmpty()) sb.append("Noch keine Einträge.");
        return sb.toString();
    }

    public static String streaksText(String guildId, JDA jda, int limit) {
        Map<String, PlayerStats> map = gd(guildId).statsByUser;
        List<Map.Entry<String, PlayerStats>> top = map.entrySet().stream()
                .sorted((a,b) -> Integer.compare(b.getValue().currentStreak, a.getValue().currentStreak))
                .limit(limit)
                .toList();
        StringBuilder sb = new StringBuilder();
        int rank = 1;
        for (var e : top) {
            String userId = e.getKey();
            PlayerStats ps = e.getValue();
            User u = jda.getUserById(userId);
            String name = u != null ? u.getName() : ("<" + userId + ">");
            sb.append("#").append(rank++).append(" ").append(name)
                    .append(" — Streak ").append(ps.currentStreak)
                    .append(" (Best: ").append(ps.bestStreak).append(")\n");
        }
        if (sb.isEmpty()) sb.append("Noch keine Einträge.");
        return sb.toString();
    }

    public static PlayerStats getPlayerStatsPublic(String guildId, String userId) {
        return stats(guildId, userId);
    }

    public static FlagStats getFlagStatsPublic(String guildId, String code) {
        return flagStats(guildId, code);
    }

    public static Optional<String> resolveToCode(String userInput) {
        String norm = normalize(userInput);
        if (norm.length() == 2) {
            String up = norm.toUpperCase(Locale.ROOT);
            if (ISO_CODES.contains(up)) return Optional.of(up);
        }
        // Alias direkt
        if (ALIAS_TO_CODE.containsKey(norm)) return Optional.of(ALIAS_TO_CODE.get(norm));
        // Namen de/en durchgehen
        for (String iso : ISO_CODES) {
            String de = normalize(countryName(iso, Locale.GERMAN));
            String en = normalize(countryName(iso, Locale.ENGLISH));
            if (norm.equals(de) || norm.equals(en)) return Optional.of(iso);
        }
        return Optional.empty();
    }
}
