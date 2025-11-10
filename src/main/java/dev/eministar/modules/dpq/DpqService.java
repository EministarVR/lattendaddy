package dev.eministar.modules.dpq;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.eministar.config.Config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * DPQ Service: läd Fragen, verwaltet laufende Nummer und erstellt Tickets/Embeds.
 */
public class DpqService {
    public record Qa(int id, String question, String solution) {}

    private static String QUESTIONS_URL = "https://star-dev.xyz/bkt/api/politik/questions.json"; // fallback

    private static List<Qa> cached = new ArrayList<>();
    private static int questionIndex = 0;
    private static int lastNumber = 0;
    private static final Map<Integer, String> solutionByNumber = new ConcurrentHashMap<>();
    private static final Map<Integer, String> questionByNumber = new ConcurrentHashMap<>();

    // Map ticket channelId -> dpq number
    private static final Map<String, Integer> ticketToNumber = new ConcurrentHashMap<>();
    private static final File STATE_FILE = new File("dpq-state.json");
    private static final ScheduledThreadPoolExecutor scheduler = new ScheduledThreadPoolExecutor(1);
    private static ScheduledFuture<?> pendingSave;

    private static void markDirty() {
        if (pendingSave != null) pendingSave.cancel(false);
        pendingSave = scheduler.schedule(DpqService::saveState, 1, TimeUnit.SECONDS);
    }

    public static synchronized void loadState() {
        if (!STATE_FILE.exists()) return;
        try (java.io.FileReader fr = new java.io.FileReader(STATE_FILE)) {
            var obj = new Gson().fromJson(fr, java.util.Map.class);
            if (obj != null) {
                Object ln = obj.get("lastNumber");
                Object qi = obj.get("questionIndex");
                if (ln instanceof Number n) lastNumber = n.intValue();
                if (qi instanceof Number n2) questionIndex = n2.intValue();
            }
        } catch (Exception ignored) {}
    }

    private static synchronized void saveState() {
        try (FileWriter fw = new FileWriter(STATE_FILE)) {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("lastNumber", lastNumber);
            m.put("questionIndex", questionIndex);
            new Gson().toJson(m, fw);
        } catch (IOException ignored) {}
    }

    public static synchronized void loadQuestions() throws Exception {
        String cfg = dev.eministar.config.Config.getDpqQaUrl();
        String url = (cfg != null && !cfg.isEmpty()) ? cfg : QUESTIONS_URL;
        try (InputStreamReader r = new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8)) {
            // Versuche QA-Format (Objekte mit id, question, solution). Wenn kein solution vorhanden, wird null eingelesen.
            cached = new Gson().fromJson(r, new TypeToken<List<Qa>>(){}.getType());
            if (cached == null) cached = new ArrayList<>();
            questionIndex = 0;
            solutionByNumber.clear();
            questionByNumber.clear();
        }
    }

    public static synchronized Qa nextQuestion() {
        if (cached.isEmpty()) return null;
        Qa qa = cached.get(questionIndex % cached.size());
        questionIndex++;
        lastNumber++;
        questionByNumber.put(lastNumber, qa.question());
        if (qa.solution() != null && !qa.solution().isBlank()) {
            solutionByNumber.put(lastNumber, qa.solution());
        }
        markDirty();
        return qa;
    }

    public static String getSolutionForNumber(int number) {
        return solutionByNumber.get(number);
    }

    public static void postQuestion(Guild guild, TextChannel channel, Qa qa) {
        String pingRoleId = Config.getDpqPingRoleId();
        String rolePing = pingRoleId != null && !pingRoleId.isEmpty() ? "<@&" + pingRoleId + ">\n\n" : "";

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0x1F8B4C));
        eb.setTitle("🗳️ Daily Politik Question #" + lastNumber);
        eb.setDescription("**Frage des Tages**\n" + qa.question() + "\n\n" +
                "**Wie antwortest du?**\n" +
                "1️⃣ Klicke auf *Antwort abgeben*\n" +
                "2️⃣ Der Bot erstellt deinen privaten Antwort-Channel\n" +
                "3️⃣ Schreibe dort deine Einschätzung (eigene Worte!)\n\n" +
                "**Hinweis:** KI / kopierte Antworten werden aussortiert. Am Ende der Woche erfolgt die Auswertung.");
        if (qa.solution() != null) {
            eb.addField("Interne Musterlösung", "(wird erst nach Auswertung veröffentlicht)", false);
        }
        eb.addField("Ziel", "Förderung politischer Bildung & eigenständigen Denkens", false);
        eb.setFooter("Erstelle deinen Antwort-Channel jetzt • Auswertung am Wochenende", guild.getIconUrl());
        eb.setTimestamp(Instant.now());

        channel.sendMessage(rolePing)
                .addEmbeds(eb.build())
                .setComponents(ActionRow.of(
                        Button.primary("dpq_answer_" + lastNumber, "\uD83D\uDDF3\uFE0F » Antwort abgeben"),
                        Button.secondary("dpq_mute_ping", "\uD83D\uDD07 » Pings deaktivieren")
                ))
                .queue();
    }

    public static void handleAnswerClick(Guild guild, Member member, int number) {
        // Prüfe ob User schon einen Channel für diese Nummer hat
        for (String channelId : ticketToNumber.keySet()) {
            if (ticketToNumber.get(channelId) == number) {
                var ch = guild.getTextChannelById(channelId);
                if (ch != null && ch.getPermissionOverride(member) != null) {
                    ch.sendMessage(member.getAsMention() + " Du hast bereits einen Antwort-Channel für #" + number + ".").queue();
                    return;
                }
            }
        }
        // Kategorie ermitteln/erstellen
        String catId = Config.getDpqCategoryId();
        Category cat = (catId != null && !catId.isEmpty()) ? guild.getCategoryById(catId) : null;
        if (cat == null) {
            guild.createCategory("DPQ").queue(c -> {
                // Speichere neue Kategorie-ID nicht persistent (Config-Datei bleibt unverändert), nur Laufzeit.
                createDpqChannel(guild, c, member, number);
            });
        } else {
            createDpqChannel(guild, cat, member, number);
        }
    }

    private static void createDpqChannel(Guild guild, Category cat, Member member, int number) {
        String base = ("dpq-" + number + "-" + member.getUser().getName()).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+","-");
        guild.createTextChannel(base, cat).queue(tc -> {
            tc.upsertPermissionOverride(guild.getPublicRole())
                    .deny(Permission.VIEW_CHANNEL)
                    .queue();
            tc.upsertPermissionOverride(member)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                    .queue();
            String teamRoleId = Config.getDpqTeamRoleId();
            if (teamRoleId != null && !teamRoleId.isEmpty()) {
                Role team = guild.getRoleById(teamRoleId);
                if (team != null) {
                    tc.upsertPermissionOverride(team)
                            .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)
                            .queue();
                }
            }

            String originalQuestion = questionByNumber.getOrDefault(number, "(Frage konnte nicht geladen werden)" );

            // Begrüßungs-Ping
            tc.sendMessage(member.getAsMention() + " Willkommen zu **DPQ #" + number + "**. Lies die Anleitung unten und formuliere deine eigene Antwort.").queue();

            // Info-Embed
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(new Color(0x2B2D31));
            eb.setTitle("📚 Deine Antwort zu DPQ #" + number);
            eb.setDescription("**Frage:**\n" + originalQuestion + "\n\n" +
                    "✍️ **Deine Aufgabe:** Schreibe deine Sicht / Erklärung. Nutze Quellen kritisch, aber kopiere nicht einfach.\n" +
                    "🤖 **Kein KI-Text:** Automatisierte oder 1:1 übernommene KI-Ausgaben werden entfernt.\n" +
                    "🕒 **Zeitfenster:** Antworten bis zur Wochen-Auswertung möglich.\n" +
                    "🔐 Nur du und das Team sehen diesen Channel.");
            eb.addField("Tipps", "• Eigene Worte\n• Sachlich bleiben\n• Quellen optional verlinken\n• Max. 1–3 Absätze", false);
            eb.addField("Format", "Ein einfacher Text reicht. Keine Embeds nötig.", true);
            eb.addField("Auswertung", "Erfolgt am Wochenende – danach Veröffentlichung einer Musterlösung.", true);
            eb.setFooter("Politische Bildung stärkt demokratische Kultur", guild.getIconUrl());
            eb.setTimestamp(Instant.now());

            tc.sendMessageEmbeds(eb.build()).queue();
            ticketToNumber.put(tc.getId(), number);
        });
    }

    public static boolean hasTeamPermission(Member member) {
        String teamRoleId = Config.getDpqTeamRoleId();
        if (teamRoleId == null || teamRoleId.isEmpty()) return false;
        for (Role r : member.getRoles()) if (r.getId().equals(teamRoleId)) return true;
        return false;
    }

    public static void lockTicketsFor(Guild guild, int number) {
        for (Map.Entry<String, Integer> e : ticketToNumber.entrySet()) {
            if (!Objects.equals(e.getValue(), number)) continue;
            var ch = guild.getTextChannelById(e.getKey());
            if (ch != null) {
                ch.upsertPermissionOverride(guild.getPublicRole()).deny(Permission.MESSAGE_SEND).queue();
                ch.getPermissionOverrides().forEach(po -> {
                    if (po.isMemberOverride() && po.getMember() != null) {
                        ch.upsertPermissionOverride(po.getMember()).deny(Permission.MESSAGE_SEND).queue();
                    }
                });
            }
        }
    }

    public static void announceWinner(TextChannel dpqChannel, int number, User winner) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(new Color(0xFEE75C));
        eb.setTitle("🏆 Gewinner der Daily Politik Question #" + number);
        eb.setDescription("Herzlichen Glückwunsch an " + winner.getAsMention() + "!\nKleine Belohnung wurde gutgeschrieben.");
        eb.setFooter("Politische Bildung – Vielfalt stärkt Demokratie", null);
        eb.setTimestamp(Instant.now());
        dpqChannel.sendMessageEmbeds(eb.build()).queue();
    }

    public static int currentNumber() { return lastNumber; }
    public static int getCurrentNumber() { return lastNumber; }
}
