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
 *
 * Fails when a NEW step does not produce what its wording promises:
 *   "start X" / "complete X" -> a quest condition, "Train to N Skill" -> a skill
 *   condition, "Talk to / Kill X" -> an NPC target.
 */
public class AtlasGuideReportTest
{
	private static final Pattern START = Pattern.compile("(?i)\\bstart\\s+(?!a\\b|an\\b|the first\\b)[A-Z]");
	private static final Pattern COMPLETE = Pattern.compile("^(?:Complete|.*\\band complete)\\s+[A-Z]");
	private static final Pattern TRAIN = Pattern.compile("^Train to \\d{1,2} [A-Z][a-z]+");
	private static final Pattern TALK = Pattern.compile("^(?:Talk to|Kill) ");
	private static final Pattern QUEST_WORDS = Pattern.compile("(?i)\\b(?:start|complete)\\s+([A-Za-z][A-Za-z'’ &-]{2,45})");

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

		try
		{
			PlaceDirectory places = new PlaceDirectory(new Gson());
			TransportResolver transports = new TransportResolver(new Gson());
			Map<String, StepLocationPlan> plans = StepLocationPlanner.buildPlans(guide, targets, places, transports, name -> null);
			Files.write(out.resolve("location-audit.md"),
				LocationAudit.toMarkdown("hcim-atlas", guide, plans).getBytes(StandardCharsets.UTF_8));
		}
		catch (RuntimeException | LinkageError e)
		{
			md.append("\nLocation audit skipped: ").append(e).append('\n');
		}

		Files.write(out.resolve("steps.tsv"), tsv.toString().getBytes(StandardCharsets.UTF_8));
		Files.write(out.resolve("report.md"), md.toString().getBytes(StandardCharsets.UTF_8));
		System.out.println(md.substring(0, md.indexOf("## New steps")));

		assertTrue("New steps whose wording Guide Overlay does not act on:\n" + String.join("\n", misses), misses.isEmpty());
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
		return null;
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
