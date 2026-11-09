package com.hcimroute;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
