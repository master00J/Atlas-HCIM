package com.hcimguide;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Quest;
import org.junit.Assume;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Runs the HCIM Atlas route through Guide Overlay's own parsers, exactly as an
 * import in the plugin would, and writes a per-step report. Copied into a
 * checkout of RunelitePlugin/guide-overlay by the Atlas CI; not part of
 * guide-overlay itself.
 *
 * Environment:
 *   ATLAS_GUIDE   the guide JSON to import (required, otherwise the test is skipped)
 *   ATLAS_BASE    the B0aty base JSON; steps whose text is not in it count as "new"
 *   ATLAS_REPORT  output directory (default build/atlas-report)
 *   ATLAS_PINS    the Atlas pin import; its keys must exist in the parsed guide
 *   ATLAS_GUIDE_ID the guide id inside ATLAS_PINS (default hcim-atlas-2026)
 *
 * Fails when a NEW step does not produce what its wording promises:
 *   "start X" / "complete X" -> a quest condition, "Train to N Skill" -> a skill
 *   condition, "Talk to / Kill X" -> an NPC target.
 */
public class AtlasGuideReportTest
{
	// Case-sensitive on purpose: a quest name is capitalised, prose ("start with one kill") is not.
	private static final Pattern START = Pattern.compile("\\b[Ss]tart\\s+[A-Z]");
	private static final Pattern COMPLETE = Pattern.compile("^(?:Complete|.*\\band complete)\\s+[A-Z]");
	private static final Pattern TRAIN = Pattern.compile("^Train to \\d{1,2} [A-Z][a-z]+");
	private static final Pattern TALK = Pattern.compile("^(?:Talk to|Kill) ");
	private static final Pattern QUEST_WORDS = Pattern.compile("\\b(?:[Ss]tart|[Cc]omplete)\\s+([A-Z][A-Za-z'’ &-]{2,45})");

	/** One line per actionable step the plugin cannot point at: status, reason, candidates, section, key, text. */
	private static final StringBuilder UNRESOLVED = new StringBuilder();

	@Test
	public void reportAtlasGuide() throws IOException
	{
		String guidePath = System.getenv("ATLAS_GUIDE");
		Assume.assumeNotNull(guidePath);
		Path out = Paths.get(env("ATLAS_REPORT", "build/atlas-report"));
		Files.createDirectories(out);

		Guide guide = new JsonGuideParser().tryParse(read(guidePath));
		assertNotNull("Guide Overlay could not parse " + guidePath, guide);

		Set<String> baseTexts = new HashSet<>();
		String basePath = System.getenv("ATLAS_BASE");
		if (basePath != null)
		{
			Guide base = new JsonGuideParser().tryParse(read(basePath));
			assertNotNull("Guide Overlay could not parse " + basePath, base);
			forEachStep(base, (episode, bank, step) -> baseTexts.add(step.getText()));
		}

		Map<String, Integer> counts = new LinkedHashMap<>();
		Map<String, String> targets = new HashMap<>();
		List<String> misses = new ArrayList<>();
		StringBuilder tsv = new StringBuilder("episode\tsection\tkey\tnew\tcondition\ttarget\tsecondary\tobjects\ttext\n");
		StringBuilder newRows = new StringBuilder();

		forEachStep(guide, (episode, bank, step) ->
		{
			String text = step.getText();
			boolean isNew = !baseTexts.contains(text);
			StepCondition condition = ConditionParser.parse(text);
			String target = TargetExtractor.extract(text);
			String secondary = TargetExtractor.extractSecondary(text);
			Set<String> objects = TargetExtractor.objectWordsIn(text);
			if (target != null)
			{
				targets.put(step.getKey(), target);
			}
			String cond = condition == null ? "" : condition.getType() + ": " + condition.describe();

			bump(counts, "steps");
			bump(counts, condition == null ? "condition: none" : "condition: " + condition.getType());
			bump(counts, target == null ? "target: none" : "target: npc");
			if (isNew)
			{
				bump(counts, "new steps");
				bump(counts, condition == null ? "new condition: none" : "new condition: " + condition.getType());
				if (target != null)
				{
					bump(counts, "new target: npc");
				}
				newRows.append("| ").append(cell(bank.getTitle())).append(" | ").append(cell(cond))
					.append(" | ").append(cell(target)).append(" | ").append(cell(text)).append(" |\n");
				String miss = expectationMiss(text, condition, target);
				if (miss != null)
				{
					misses.add(bank.getTitle() + ": " + miss + " — " + text);
				}
			}
			tsv.append(episode.getTitle()).append('\t').append(bank.getTitle()).append('\t').append(step.getKey())
				.append('\t').append(isNew).append('\t').append(cond).append('\t').append(nz(target))
				.append('\t').append(nz(secondary)).append('\t').append(String.join(",", objects))
				.append('\t').append(text.replace('\t', ' ')).append('\n');
		});

		StringBuilder md = new StringBuilder("# Guide Overlay parse report\n\n");
		md.append("Guide: `").append(guidePath).append("`\n\n");
		for (Map.Entry<String, Integer> e : counts.entrySet())
		{
			md.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
		}
		md.append("\n## Expectation misses in new steps (").append(misses.size()).append(")\n\n");
		for (String miss : misses)
		{
			md.append("- ").append(miss).append('\n');
		}
		md.append("\n## Quests named in new steps that RuneLite does not know\n\n");
		for (String unknown : unknownQuests(newRows.toString()))
		{
			md.append("- ").append(unknown).append('\n');
		}
		md.append("\n## New steps\n\n| Section | Condition | NPC target | Text |\n|---|---|---|---|\n").append(newRows);

		StringBuilder locations = new StringBuilder("# Locations as the plugin resolves them\n\n");
		List<String> orphanPins = new ArrayList<>();
		try
		{
			PlaceDirectory places = new PlaceDirectory(new Gson());
			TransportResolver transports = new TransportResolver(new Gson());
			// Same NPC data the plugin uses in game: bundled seed + the pinned
			// community spawn database it downloads on first start.
			NpcLocationStore npcs = new NpcLocationStore(new Gson());
			npcs.load();
			locations.append("- NPC database: ").append(downloadNpcDatabase(npcs)).append('\n');
			Map<String, StepLocationPlan> plans = StepLocationPlanner.buildPlans(guide, targets, places, transports, npcs::lookup);
			Files.write(out.resolve("location-audit.md"),
				LocationAudit.toMarkdown("hcim-atlas", guide, plans).getBytes(StandardCharsets.UTF_8));
			summariseLocations(guide, plans, readPinKeys(), baseTexts, locations, orphanPins);
		}
		catch (RuntimeException | LinkageError e)
		{
			locations.append("\nLocation audit skipped: ").append(e).append('\n');
		}
		md.insert(md.indexOf("## New steps"), locations + "\n");

		Files.write(out.resolve("unresolved.tsv"), UNRESOLVED.toString().getBytes(StandardCharsets.UTF_8));
		Files.write(out.resolve("steps.tsv"), tsv.toString().getBytes(StandardCharsets.UTF_8));
		Files.write(out.resolve("report.md"), md.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(md.substring(0, md.indexOf("## New steps")));

		assertTrue("New steps whose wording Guide Overlay does not act on:\n" + String.join("\n", misses), misses.isEmpty());
		assertTrue("Pins whose step key does not exist in the imported guide (they would never show):\n"
			+ String.join("\n", orphanPins), orphanPins.isEmpty());
	}

	/** Mirrors LocationAudit's definition of an unresolved actionable step. */
	private static boolean unresolved(StepLocationPlan plan)
	{
		if (plan != null && plan.hasWaypoints())
		{
			return !plan.getUnresolvedCandidates().isEmpty()
				&& plan.getReason() != LocationResolutionReason.RESOLVED
				&& plan.getReason() != LocationResolutionReason.MULTIPLE_DESTINATIONS
				&& plan.getReason() != LocationResolutionReason.NO_LOCATION_NEEDED;
		}
		return plan != null && plan.getReason() != LocationResolutionReason.NO_LOCATION_NEEDED;
	}

	private static void summariseLocations(Guide guide, Map<String, StepLocationPlan> plans, Set<String> pinKeys,
		Set<String> baseTexts, StringBuilder md, List<String> orphanPins)
	{
		Set<String> guideKeys = new HashSet<>();
		Map<String, Integer> reasons = new LinkedHashMap<>();
		int[] n = new int[6]; // resolved, unresolved, unresolved+pin, new unresolved, new unresolved+pin, pinned
		StringBuilder newUnresolved = new StringBuilder();
		forEachStep(guide, (episode, bank, step) ->
		{
			guideKeys.add(step.getKey());
			StepLocationPlan plan = plans.get(step.getKey());
			boolean pinned = pinKeys.contains(step.getKey());
			boolean isNew = !baseTexts.contains(step.getText());
			if (pinned)
			{
				n[5]++;
			}
			if (plan != null && plan.hasWaypoints())
			{
				n[0]++;
			}
			if (unresolved(plan))
			{
				UNRESOLVED.append(pinned ? "pinned" : "open").append('\t').append(plan.getReason().displayName())
					.append('\t').append(String.join(", ", plan.getUnresolvedCandidates()))
					.append('\t').append(bank.getTitle()).append('\t').append(step.getKey())
					.append('\t').append(step.getText().replace('\t', ' ')).append('\n');
				n[1]++;
				reasons.merge(plan.getReason().displayName(), 1, Integer::sum);
				if (!pinned)
				{
					n[2]++;
				}
				if (isNew)
				{
					n[3]++;
					if (!pinned)
					{
						n[4]++;
						newUnresolved.append("| ").append(cell(bank.getTitle())).append(" | ")
							.append(cell(plan.getReason().displayName())).append(" | ")
							.append(cell(String.join(", ", plan.getUnresolvedCandidates()))).append(" | ")
							.append(cell(step.getText())).append(" |\n");
					}
				}
			}
		});
		for (String key : pinKeys)
		{
			if (!guideKeys.contains(key))
			{
				orphanPins.add(key);
			}
		}
		md.append("- Steps with a resolved location: ").append(n[0]).append('\n')
			.append("- Unresolved actionable steps: ").append(n[1]).append(" (").append(n[2]).append(" after importing the Atlas pins)\n")
			.append("- Atlas pins that match a step: ").append(n[5]).append(" of ").append(pinKeys.size())
			.append(" (orphans: ").append(orphanPins.size()).append(")\n")
			.append("- Unresolved new steps: ").append(n[3]).append(" (").append(n[4]).append(" after pins)\n\n")
			.append("Unresolved by reason:\n\n");
		for (Map.Entry<String, Integer> e : reasons.entrySet())
		{
			md.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
		}
		md.append("\n### New steps without a location (after pins)\n\n| Section | Reason | Candidates | Text |\n|---|---|---|---|\n")
			.append(newUnresolved);
	}

	private static String downloadNpcDatabase(NpcLocationStore npcs)
	{
		try
		{
			java.net.URLConnection c = new java.net.URL(LocationDbDownloader.SOURCE_URL).openConnection();
			c.setConnectTimeout(20_000);
			c.setReadTimeout(60_000);
			try (java.io.Reader in = new java.io.InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))
			{
				LocationDbDownloader.Parsed parsed = LocationDbDownloader.parse(new com.google.gson.stream.JsonReader(in));
				int added = npcs.addNormalizedIfAbsent(parsed.locations);
				return "downloaded " + LocationDbDownloader.SOURCE_URL + " (" + added + " names added)";
			}
		}
		catch (IOException | RuntimeException e)
		{
			return "download failed, bundled seed only (" + e + ")";
		}
	}

	/** Step keys from the Atlas pin import (ATLAS_PINS, guide id ATLAS_GUIDE_ID). */
	private static Set<String> readPinKeys() throws IOException
	{
		Set<String> keys = new HashSet<>();
		String path = System.getenv("ATLAS_PINS");
		if (path == null)
		{
			return keys;
		}
		com.google.gson.JsonObject root = new Gson().fromJson(read(path), com.google.gson.JsonObject.class);
		com.google.gson.JsonObject guides = root.getAsJsonObject("guides");
		String id = env("ATLAS_GUIDE_ID", "hcim-atlas-2026");
		if (guides != null && guides.has(id))
		{
			keys.addAll(guides.getAsJsonObject(id).keySet());
		}
		return keys;
	}

	private static String expectationMiss(String text, StepCondition condition, String target)
	{
		StepCondition.Type type = condition == null ? null : condition.getType();
		if (COMPLETE.matcher(text).find() && type != StepCondition.Type.QUEST_FINISHED)
		{
			return "expected QUEST_FINISHED, got " + type;
		}
		if (!COMPLETE.matcher(text).find() && START.matcher(text).find() && type != StepCondition.Type.QUEST_STARTED)
		{
			return "expected QUEST_STARTED, got " + type;
		}
		if (TRAIN.matcher(text).find() && type != StepCondition.Type.SKILL_LEVEL)
		{
			return "expected SKILL_LEVEL, got " + type;
		}
		if (TALK.matcher(text).find() && target == null)
		{
			return "expected an NPC target";
		}
		if (condition != null && condition.getQuest() != null && !namesWholeQuest(text, condition.getQuest().getName()))
		{
			// e.g. "Complete Dragon Slayer II to ..." resolving to Dragon Slayer I
			return "quest resolved to " + condition.getQuest().getName() + ", which is not the quest named";
		}
		return null;
	}

	private static boolean namesWholeQuest(String text, String questName)
	{
		String lower = text.toLowerCase(java.util.Locale.ROOT).replace('’', '\'');
		String name = questName.toLowerCase(java.util.Locale.ROOT).replace('’', '\'');
		int from = 0;
		int idx;
		while ((idx = lower.indexOf(name, from)) >= 0)
		{
			int end = idx + name.length();
			if (end >= lower.length() || !Character.isLetter(lower.charAt(end)))
			{
				return true;
			}
			from = idx + 1;
		}
		return false;
	}

	private static List<String> unknownQuests(String rows)
	{
		Set<String> known = new HashSet<>();
		for (Quest q : Quest.values())
		{
			known.add(Names.normalize(q.getName()));
		}
		List<String> unknown = new ArrayList<>();
		Matcher m = QUEST_WORDS.matcher(rows);
		while (m.find())
		{
			String cand = Names.normalize(m.group(1));
			boolean hit = false;
			for (String k : known)
			{
				if (cand.startsWith(k) || ("the" + cand).startsWith(k))
				{
					hit = true;
					break;
				}
			}
			if (!hit && !unknown.contains(m.group(1).trim()))
			{
				unknown.add(m.group(1).trim());
			}
		}
		return unknown;
	}

	private interface StepVisitor
	{
		void visit(GuideEpisode episode, GuideBank bank, GuideStep step);
	}

	private static void forEachStep(Guide guide, StepVisitor visitor)
	{
		for (GuideEpisode episode : guide.getEpisodes())
		{
			for (GuideBank bank : episode.getBanks())
			{
				for (GuideStep step : bank.getSteps())
				{
					visitor.visit(episode, bank, step);
				}
			}
		}
	}

	private static void bump(Map<String, Integer> counts, String key)
	{
		counts.merge(key, 1, Integer::sum);
	}

	private static String read(String path) throws IOException
	{
		return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
	}

	private static String env(String name, String fallback)
	{
		String v = System.getenv(name);
		return v == null || v.isEmpty() ? fallback : v;
	}

	private static String nz(String s)
	{
		return s == null ? "" : s;
	}

	private static String cell(String s)
	{
		return nz(s).replace("|", "\\|").replace("\n", " ");
	}
}
