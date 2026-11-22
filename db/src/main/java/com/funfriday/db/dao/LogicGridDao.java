package com.funfriday.db.dao;
import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.LogicGridPuzzle;
import java.sql.*; import java.util.*;
public class LogicGridDao {
  private final DatabaseConnectionProvider cp; public LogicGridDao(DatabaseConnectionProvider cp){this.cp=cp;}
  public Optional<LogicGridPuzzle> active(Long id) throws SQLException { String q="SELECT * FROM logic_grid_puzzles WHERE is_active=TRUE"+(id==null?" ORDER BY RAND() LIMIT 1":" AND id=?"); try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(q)){if(id!=null)s.setLong(1,id);try(ResultSet r=s.executeQuery()){return r.next()?Optional.of(row(r)):Optional.empty();}} }
  public List<Map<String,Object>> catalogue() throws SQLException { List<Map<String,Object>> out=new ArrayList<>();try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement("SELECT id,puzzle_key,category,title,JSON_LENGTH(clues_json) clue_count FROM logic_grid_puzzles WHERE is_active=TRUE ORDER BY id");ResultSet r=s.executeQuery()){while(r.next())out.add(Map.of("id",r.getLong(1),"puzzleKey",r.getString(2),"category",r.getString(3),"title",r.getString(4),"clueCount",r.getInt(5)));}return out;}
  private LogicGridPuzzle row(ResultSet r)throws SQLException{return new LogicGridPuzzle(r.getLong("id"),r.getString("puzzle_key"),r.getString("category"),r.getString("title"),r.getString("story"),r.getString("entities_json"),r.getString("attributes_json"),r.getString("clues_json"),r.getString("solution_json"));}
}
