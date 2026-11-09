package com.hcimroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.BeforeClass;
import org.junit.Test;

/** Controleert route.json tegen de echte RuneLite-API (questnamen, skills) en de voortgangslogica. */
public class RouteGuideTest
{
	private static RouteGuide guide;

	@BeforeClass
	public static void load() throws Exception
	{
		guide = RouteGuide.load(new Gson(), HcimRoutePlugin.class.getResourceAsStream("route.json"));
	}

	@Test
	public void routeHasAllPhases()
	{
		assertTrue(guide.getMainSteps().size() > 300);
		long phases = guide.getChapters().stream().filter(Route.Chapter::isTrackable).count();
		assertEquals(7, phases);
		Route.Step first = guide.getMainSteps().get(0);
		assertEquals("Bank 0", first.getSection().getTitle());
	}

	@Test
	public void stepIdsAreUnique()
	{
		Set<String> ids = new HashSet<>();
		for (Route.Step step : guide.getAllTrackableSteps())
		{
			assertTrue("dubbel id " + step.getId(), ids.add(step.getId()));
			assertFalse("id met komma " + step.getId(), step.getId().contains(","));
		}
	}

	@Test
	public void questNamesExistInRuneLite()
	{
		Set<String> names = new HashSet<>();
		for (Quest q : Quest.values())
		{
			names.add(q.getName());
		}
		for (Route.Step step : guide.getAllTrackableSteps())
		{
			for (String quest : step.getComplete())
			{
				assertTrue("onbekende quest '" + quest + "' in " + step.getId(), names.contains(quest));
			}
		}
	}

	@Test
	public void skillTargetsAreValid()
	{
		for (Route.Step step : guide.getAllTrackableSteps())
		{
			for (Route.SkillTarget t : step.getTargets())
			{
				assertNotNull("onbekende skill " + t.getSkill(), Skill.valueOf(t.getSkill()));
				assertTrue(t.getXp() > 0 || (t.getLevel() >= 1 && t.getLevel() <= 99));
			}
		}
	}

	@Test
	public void upcomingSkipsDoneStepsAndSailing()
	{
		ProgressTracker progress = new ProgressTracker();
		List<Route.Step> main = guide.getMainSteps();
		progress.set(main.get(0).getId(), true);
		List<Route.Step> next = guide.upcoming(progress::isDone, 2);
		assertEquals(main.get(1), next.get(0));
		for (Route.Step s : main)
		{
			assertFalse(s.getSection().getChapter().isParallel());
		}
		assertNotNull(guide.nextParallel(progress::isDone));
	}

	@Test
	public void markUpToAndSerialize()
	{
		ProgressTracker progress = new ProgressTracker();
		Route.Step tenth = guide.getMainSteps().get(9);
		for (Route.Step s : guide.stepsUpTo(tenth))
		{
			progress.set(s.getId(), true);
		}
		assertEquals(10, progress.size());

		ProgressTracker copy = new ProgressTracker();
		copy.load(progress.serialize());
		assertEquals(10, copy.size());
		assertEquals(guide.getMainSteps().get(10), guide.upcoming(copy::isDone, 1).get(0));
	}

	private static Route.Step findStep(String... fragments)
	{
		for (Route.Step step : guide.getAllTrackableSteps())
		{
			boolean all = true;
			for (String f : fragments)
			{
				all &= step.getText().contains(f);
			}
			if (all)
			{
				return step;
			}
		}
		throw new AssertionError("stap niet gevonden: " + String.join(" + ", fragments));
	}

	@Test
	public void compoundStepsAreNotAutoCompleted()
	{
		// "Voltooi Cook's Assistant. Pickpocket een man of vrouw": de quest mag de pickpocket niet afvinken
		Route.Step cooks = findStep("Cook's Assistant", "Pickpocket");
		assertTrue(cooks.getComplete().contains("Cook's Assistant"));
		assertFalse(cooks.isAutoComplete());

		// opdracht na de quest in dezelfde stap
		Route.Step merlin = findStep("Merlin's Crystal", "Start Holy Grail");
		assertFalse(merlin.isAutoComplete());

		// extra opdracht in dezelfde opsomming
		Route.Step rfd = findStep("Recipe for Disaster", "Barrows gloves");
		assertFalse(rfd.isAutoComplete());
	}

	@Test
	public void questOnlyStepsAreAutoCompleted()
	{
		// voorbereiding vóór "Voltooi X" blokkeert niet
		Route.Step waterfall = findStep("Baxtorian Falls", "Waterfall Quest");
		assertTrue(waterfall.isAutoComplete());
		// beloning tussen haakjes blokkeert niet
		Route.Step demon = findStep("Silverlight key", "Demon Slayer");
		assertTrue(demon.isAutoComplete());

		int auto = 0;
		for (Route.Step step : guide.getAllTrackableSteps())
		{
			if (step.isAutoComplete())
			{
				auto++;
				assertFalse(step.getComplete().isEmpty());
			}
		}
		assertTrue("te weinig automatische stappen: " + auto, auto >= 20);
	}

	@Test
	public void banksAreContiguous()
	{
		Pattern bank = Pattern.compile("^Bank (\\d+)[A-Z]?(?:\\s*[–→-]\\s*(\\d+))?");
		int last = -1;
		for (Route.Chapter chapter : guide.getChapters())
		{
			if (!chapter.isTrackable() || chapter.isParallel())
			{
				continue;
			}
			for (Route.Section section : chapter.getSections())
			{
				Matcher m = bank.matcher(section.getTitle());
				if (!m.find())
				{
					continue;
				}
				int from = Integer.parseInt(m.group(1));
				int to = m.group(2) != null ? Integer.parseInt(m.group(2)) : from;
				assertTrue("bank ontbreekt vóór " + section.getTitle() + " (vorige: " + last + ")", from <= last + 1);
				assertTrue("bank gaat terug bij " + section.getTitle(), from >= last);
				last = Math.max(last, to);
			}
		}
		assertEquals(211, last);
	}

	@Test
	public void accountSwitchNeverWritesToOtherProfile()
	{
		AtomicReference<String> active = new AtomicReference<>(null);
		ProfileGuard guard = new ProfileGuard(active::get);
		assertFalse("zonder profiel mag niets", guard.matches());

		active.set("account-a");
		guard.loaded("account-a");
		assertTrue(guard.matches());

		// RuneLite wisselt naar een ander account vóór de plugin opnieuw geladen heeft
		active.set("account-b");
		assertFalse(guard.matches());
		assertTrue(guard.isStale());

		// herladen: de voortgang van account A wordt vervangen door die van B (hier: nieuw account)
		ProgressTracker progress = new ProgressTracker();
		progress.set(guide.getMainSteps().get(0).getId(), true);
		progress.load(null);
		guard.loaded("account-b");
		assertTrue(guard.matches());
		assertEquals(0, progress.size());
	}

	@Test
	public void textRendering()
	{
		String html = RouteText.toHtmlFragment("**Voltooi Tourist Trap.** HCIM: safespot [Demon Slayer] → klaar");
		assertTrue(html.contains("<b>Voltooi Tourist Trap.</b>"));
		assertTrue(html.contains("[Demon Slayer]</font>"));
		assertTrue(html.contains("-&gt; klaar"));
		assertEquals("a -> b - c", RouteText.toPlain("a → b – c", 0));
		assertEquals("https://oldschool.runescape.wiki/w/Cook's_Assistant", RouteText.wikiUrl("Cook's Assistant"));
	}
}
