package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import java.sql.*;
import java.util.*;

/** Review staging and publishing for Logic Grid puzzles. */
public class LogicGridDraftDao {
  private final DatabaseConnectionProvider cp;
  public LogicGridDraftDao(DatabaseConnectionProvider cp) { this.cp = cp; }

  public long create(String key, String category, String title, String story, String entities, String attributes, String clues, String solution, String model) throws SQLException {
    String sql = "INSERT INTO logic_grid_drafts(puzzle_key,category,title,story,entities_json,attributes_json,clues_json,solution_json,model) VALUES(?,?,?,?,?,?,?,?,?)";
    try (Connection c=cp.getConnection(); PreparedStatement s=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      s.setString(1,key); s.setString(2,category); s.setString(3,title); s.setString(4,story); s.setString(5,entities); s.setString(6,attributes); s.setString(7,clues); s.setString(8,solution); s.setString(9,model); s.executeUpdate();
      try(ResultSet k=s.getGeneratedKeys()){k.next(); return k.getLong(1);}
    }
  }

  public List<Map<String,Object>> drafts(String status) throws SQLException { return list("SELECT * FROM logic_grid_drafts WHERE status=? ORDER BY created_at DESC", status); }
  public List<Map<String,Object>> published() throws SQLException { return list("SELECT * FROM logic_grid_puzzles WHERE is_active=TRUE ORDER BY id DESC", null); }

  /**
   * Returns visible puzzle data for novelty checks. Solution JSON is intentionally never selected.
   */
  public List<Map<String,Object>> randomPublishedReferences(int limit) throws SQLException {
    List<Map<String,Object>> references = new ArrayList<>();
    String sql = "SELECT category,title,story,entities_json,attributes_json,clues_json "
        + "FROM logic_grid_puzzles WHERE is_active=TRUE ORDER BY RAND() LIMIT ?";
    try (Connection connection = cp.getConnection();
         PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setInt(1, Math.max(0, limit));
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) {
          Map<String,Object> reference = new LinkedHashMap<>();
          reference.put("category", result.getString("category"));
          reference.put("title", result.getString("title"));
          reference.put("story", result.getString("story"));
          reference.put("entitiesJson", result.getString("entities_json"));
          reference.put("attributesJson", result.getString("attributes_json"));
          reference.put("cluesJson", result.getString("clues_json"));
          references.add(reference);
        }
      }
    }
    return references;
  }

  public boolean approve(long id) throws SQLException {
    try(Connection c=cp.getConnection()) { c.setAutoCommit(false); try {
      Map<String,Object> draft;
      try(PreparedStatement s=c.prepareStatement("SELECT * FROM logic_grid_drafts WHERE id=? AND status='DRAFT' FOR UPDATE")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next()){c.rollback();return false;} draft=row(r);}}
      try(PreparedStatement s=c.prepareStatement("INSERT INTO logic_grid_puzzles(puzzle_key,category,title,story,entities_json,attributes_json,clues_json,solution_json,is_active) VALUES(?,?,?,?,?,?,?,?,TRUE)")){
        s.setString(1,(String)draft.get("puzzleKey"));s.setString(2,(String)draft.get("category"));s.setString(3,(String)draft.get("title"));s.setString(4,(String)draft.get("story"));s.setString(5,(String)draft.get("entitiesJson"));s.setString(6,(String)draft.get("attributesJson"));s.setString(7,(String)draft.get("cluesJson"));s.setString(8,(String)draft.get("solutionJson"));s.executeUpdate();}
      try(PreparedStatement s=c.prepareStatement("UPDATE logic_grid_drafts SET status='APPROVED',reviewed_at=CURRENT_TIMESTAMP WHERE id=?")){s.setLong(1,id);s.executeUpdate();}
      c.commit(); return true;
    }catch(Exception e){c.rollback();throw e;}}
  }
  public boolean decline(long id,String reason)throws SQLException{return mutate("UPDATE logic_grid_drafts SET status='DECLINED',decline_reason=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='DRAFT'",reason,id);}
  public boolean updateDraft(long id, Map<String,String> b)throws SQLException{return update("logic_grid_drafts","id=? AND status='DRAFT'",id,b);}
  public boolean updatePublished(long id, Map<String,String> b)throws SQLException{return update("logic_grid_puzzles","id=? AND is_active=TRUE",id,b);}
  public boolean deletePublished(long id)throws SQLException{try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement("DELETE FROM logic_grid_puzzles WHERE id=? AND is_active=TRUE")){s.setLong(1,id);return s.executeUpdate()==1;}}
  private boolean update(String table,String condition,long id,Map<String,String>b)throws SQLException{String sql="UPDATE "+table+" SET title=?,story=?,entities_json=?,attributes_json=?,clues_json=?,solution_json=? WHERE "+condition;try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,b.get("title"));s.setString(2,b.get("story"));s.setString(3,b.get("entitiesJson"));s.setString(4,b.get("attributesJson"));s.setString(5,b.get("cluesJson"));s.setString(6,b.get("solutionJson"));s.setLong(7,id);return s.executeUpdate()==1;}}
  private boolean mutate(String sql,String reason,long id)throws SQLException{try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,reason);s.setLong(2,id);return s.executeUpdate()==1;}}
  private List<Map<String,Object>> list(String sql,String status)throws SQLException{List<Map<String,Object>> out=new ArrayList<>();try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(sql)){if(status!=null)s.setString(1,status);try(ResultSet r=s.executeQuery()){while(r.next())out.add(row(r));}}return out;}
  private Map<String,Object> row(ResultSet r)throws SQLException{Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getLong("id"));m.put("puzzleKey",r.getString("puzzle_key"));m.put("category",r.getString("category"));m.put("title",r.getString("title"));m.put("story",r.getString("story"));m.put("entitiesJson",r.getString("entities_json"));m.put("attributesJson",r.getString("attributes_json"));m.put("cluesJson",r.getString("clues_json"));m.put("solutionJson",r.getString("solution_json"));try{m.put("status",r.getString("status"));m.put("model",r.getString("model"));}catch(SQLException ignored){m.put("status","PUBLISHED");}return m;}
}
