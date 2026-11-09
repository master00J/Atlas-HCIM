package com.hcimroute;

import java.util.ArrayList;
import java.util.List;

/**
 * De volledige route zoals ze in route.json staat. Gson vult deze klassen;
 * de transient velden worden na het laden door {@link RouteGuide} ingevuld.
 */
public class Route
{
	String title;
	String version;
	List<Chapter> chapters = new ArrayList<>();

	public static class Chapter
	{
		String id;
		String title;
		boolean trackable;
		boolean parallel;
		List<String> intro = new ArrayList<>();
		List<Section> sections = new ArrayList<>();

		public String getId()
		{
			return id;
		}

		public String getTitle()
		{
			return title;
		}

		public boolean isTrackable()
		{
			return trackable;
		}

		public boolean isParallel()
		{
			return parallel;
		}

		public List<String> getIntro()
		{
			return intro == null ? new ArrayList<>() : intro;
		}

		public List<Section> getSections()
		{
			return sections == null ? new ArrayList<>() : sections;
		}

		/** Korte titel voor de keuzelijst, zonder het bank-bereik tussen haakjes. */
		public String getShortTitle()
		{
			String t = title == null ? "" : title;
			int paren = t.indexOf(" (");
			if (paren > 0)
			{
				t = t.substring(0, paren);
			}
			t = RouteText.sanitize(t);
			return t.length() > 34 ? t.substring(0, 31) + "..." : t;
		}
	}

	public static class Section
	{
		String id;
		String title;
		String subtitle;
		String withdraw;
		String group;
		boolean info;
		List<String> notes = new ArrayList<>();
		List<Step> steps = new ArrayList<>();
		List<Table> tables = new ArrayList<>();
		/** Notities die in de guide na de stappen of de tabel staan. */
		List<String> footer = new ArrayList<>();
		List<Link> links = new ArrayList<>();

		transient Chapter chapter;

		public String getId()
		{
			return id;
		}

		public String getTitle()
		{
			return title;
		}

		public String getSubtitle()
		{
			return subtitle;
		}

		public String getWithdraw()
		{
			return withdraw;
		}

		public String getGroup()
		{
			return group;
		}

		public boolean isInfo()
		{
			return info;
		}

		public List<String> getNotes()
		{
			return notes == null ? new ArrayList<>() : notes;
		}

		public List<Step> getSteps()
		{
			return steps == null ? new ArrayList<>() : steps;
		}

		public List<Table> getTables()
		{
			return tables == null ? new ArrayList<>() : tables;
		}

		public List<String> getFooter()
		{
			return footer == null ? new ArrayList<>() : footer;
		}

		public Chapter getChapter()
		{
			return chapter;
		}

		/** "Bank 28–29 · Kourend en Varlamore" */
		public String getDisplayTitle()
		{
			if (subtitle == null || subtitle.isEmpty())
			{
				return title;
			}
			return title + " · " + subtitle;
		}
	}

	public static class Step
	{
		String id;
		String text;
		List<String> complete = new ArrayList<>();
		/** Alleen true als de stap niets anders vraagt dan de quest(s) in complete af te werken. */
		boolean autoComplete;
		List<SkillTarget> targets = new ArrayList<>();
		List<String> tags = new ArrayList<>();
		List<String> flags = new ArrayList<>();
		List<Link> links = new ArrayList<>();

		transient Section section;

		public String getId()
		{
			return id;
		}

		public String getText()
		{
			return text;
		}

		/** Quests die in deze stap voltooid worden (namen uit net.runelite.api.Quest). */
		public List<String> getComplete()
		{
			return complete == null ? new ArrayList<>() : complete;
		}

		/**
		 * Mag de stap automatisch afgevinkt worden zodra de quests klaar zijn?
		 * False bij samengestelde stappen ("Voltooi X. Pickpocket een man"):
		 * die krijgen alleen een "quest klaar"-markering.
		 */
		public boolean isAutoComplete()
		{
			return autoComplete && !getComplete().isEmpty();
		}

		public List<SkillTarget> getTargets()
		{
			return targets == null ? new ArrayList<>() : targets;
		}

		public List<String> getTags()
		{
			return tags == null ? new ArrayList<>() : tags;
		}

		public List<Link> getLinks()
		{
			return links == null ? new ArrayList<>() : links;
		}

		public boolean hasFlag(String flag)
		{
			return flags != null && flags.contains(flag);
		}

		public Section getSection()
		{
			return section;
		}
	}

	public static class SkillTarget
	{
		/** Naam van een net.runelite.api.Skill-constante, bv. "THIEVING". */
		String skill;
		/** Doel-level (0 als het doel in XP staat). */
		int level;
		/** Doel-XP (0 als het doel een level is). */
		int xp;

		public String getSkill()
		{
			return skill;
		}

		public int getLevel()
		{
			return level;
		}

		public int getXp()
		{
			return xp;
		}
	}

	public static class Table
	{
		List<String> header = new ArrayList<>();
		List<List<String>> rows = new ArrayList<>();

		public List<String> getHeader()
		{
			return header == null ? new ArrayList<>() : header;
		}

		public List<List<String>> getRows()
		{
			return rows == null ? new ArrayList<>() : rows;
		}
	}

	public static class Link
	{
		String title;
		String url;

		public String getTitle()
		{
			return title;
		}

		public String getUrl()
		{
			return url;
		}
	}
}
