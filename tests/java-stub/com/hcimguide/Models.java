package com.hcimguide;
import java.util.*;
class Guide {List<GuideEpisode> episodes=new ArrayList<>(); List<GuideEpisode> getEpisodes(){return episodes;}}
class GuideEpisode {String title; List<GuideBank> banks=new ArrayList<>();GuideEpisode(String s){title=s;}String getTitle(){return title;}List<GuideBank> getBanks(){return banks;}}
class GuideBank {String title;List<GuideStep> steps=new ArrayList<>();GuideBank(String s){title=s;}String getTitle(){return title;}List<GuideStep> getSteps(){return steps;}}
class GuideStep {String key,text;GuideStep(String k,String t){key=k;text=t;}String getKey(){return key;}String getText(){return text;}}
