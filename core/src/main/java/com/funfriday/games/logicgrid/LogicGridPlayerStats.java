package com.funfriday.games.logicgrid;
import com.funfriday.model.*; import lombok.*;
@Getter @Setter public class LogicGridPlayerStats extends PlayerStats { private double progress; private int valuesSolved; private long totalTimeMillis; public LogicGridPlayerStats(GamePlayer p,boolean h){super(p,h);} @Override public double getRankingMetric(){return valuesSolved;} }
