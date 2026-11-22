package com.funfriday.games.logicgrid;
import com.funfriday.model.GameData; import java.util.*; import java.util.concurrent.ConcurrentHashMap; import lombok.*;
@Getter @Setter public class LogicGridData extends GameData<LogicGridConfiguration>{
 private long puzzleId; private String puzzleKey,category,title,story; private List<String> entities; private List<Map<String,Object>> attributes; private List<String> clues; private Map<String,Map<String,String>> solution; private Map<String,Map<String,Map<String,String>>> playerAnswers=new ConcurrentHashMap<>(); private Map<String,Boolean> playerIncorrectSubmissions=new ConcurrentHashMap<>();
 public LogicGridData(long id,String key,String category,String title,String story,List<String> e,List<Map<String,Object>> a,List<String> c,Map<String,Map<String,String>> s){puzzleId=id;puzzleKey=key;this.category=category;this.title=title;this.story=story;entities=e;attributes=a;clues=c;solution=s;}
 public void initializePlayer(String id){playerAnswers.put(id,new HashMap<>());playerIncorrectSubmissions.put(id,false);}
}
