package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import java.sql.*;
import java.util.*;

/** Manual review and publishing workflow for generated Wavelength cards. */
public class WavelengthCardDraftDao {
  private final DatabaseConnectionProvider cp;
  public WavelengthCardDraftDao(DatabaseConnectionProvider cp) { this.cp = cp; }

  public long create(String key, String category, String left, String right, int target, String model) throws SQLException {
    String sql = "INSERT INTO wavelength_card_drafts(card_key,category,left_label,right_label,target,model) VALUES(?,?,?,?,?,?)";
    try (Connection c = cp.getConnection(); PreparedStatement s = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
      s.setString(1, key); s.setString(2, category); s.setString(3, left); s.setString(4, right); s.setInt(5, target); s.setString(6, model); s.executeUpdate();
      try (ResultSet keys = s.getGeneratedKeys()) { keys.next(); return keys.getLong(1); }
    }
  }
  public List<Map<String,Object>> drafts() throws SQLException { return list("SELECT * FROM wavelength_card_drafts WHERE status='DRAFT' ORDER BY created_at DESC"); }
  public List<Map<String,Object>> published() throws SQLException { return list("SELECT * FROM wavelength_cards WHERE is_active=TRUE ORDER BY id DESC"); }
  public List<Map<String,Object>> randomPublishedReferences(int limit) throws SQLException {
    List<Map<String,Object>> cards = new ArrayList<>();
    try (Connection c=cp.getConnection(); PreparedStatement s=c.prepareStatement("SELECT category,left_label,right_label FROM wavelength_cards WHERE is_active=TRUE ORDER BY RAND() LIMIT ?")) {
      s.setInt(1, Math.max(0, limit)); try (ResultSet r=s.executeQuery()) { while(r.next()) cards.add(Map.of("category",r.getString(1),"leftLabel",r.getString(2),"rightLabel",r.getString(3))); }
    } return cards;
  }
  public boolean approve(long id) throws SQLException { try(Connection c=cp.getConnection()) { c.setAutoCommit(false); try {
    Map<String,Object> card; try(PreparedStatement s=c.prepareStatement("SELECT * FROM wavelength_card_drafts WHERE id=? AND status='DRAFT' FOR UPDATE")){s.setLong(1,id);try(ResultSet r=s.executeQuery()){if(!r.next()){c.rollback();return false;}card=row(r);}}
    try(PreparedStatement s=c.prepareStatement("INSERT INTO wavelength_cards(card_key,category,left_label,right_label,target,is_active) VALUES(?,?,?,?,?,TRUE)")){s.setString(1,(String)card.get("cardKey"));s.setString(2,(String)card.get("category"));s.setString(3,(String)card.get("leftLabel"));s.setString(4,(String)card.get("rightLabel"));s.setInt(5,(Integer)card.get("target"));s.executeUpdate();}
    try(PreparedStatement s=c.prepareStatement("UPDATE wavelength_card_drafts SET status='APPROVED',reviewed_at=CURRENT_TIMESTAMP WHERE id=?")){s.setLong(1,id);s.executeUpdate();} c.commit(); return true;
  } catch(Exception e){c.rollback();throw e;} } }
  public boolean decline(long id, String reason) throws SQLException { return mutate("UPDATE wavelength_card_drafts SET status='DECLINED',decline_reason=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=? AND status='DRAFT'", reason, id); }
  public boolean deletePublished(long id) throws SQLException { try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement("DELETE FROM wavelength_cards WHERE id=? AND is_active=TRUE")){s.setLong(1,id);return s.executeUpdate()==1;} }
  private boolean mutate(String sql,String reason,long id)throws SQLException{try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(sql)){s.setString(1,reason);s.setLong(2,id);return s.executeUpdate()==1;}}
  private List<Map<String,Object>> list(String sql)throws SQLException{List<Map<String,Object>>out=new ArrayList<>();try(Connection c=cp.getConnection();PreparedStatement s=c.prepareStatement(sql);ResultSet r=s.executeQuery()){while(r.next())out.add(row(r));}return out;}
  private Map<String,Object> row(ResultSet r)throws SQLException{Map<String,Object>m=new LinkedHashMap<>();m.put("id",r.getLong("id"));m.put("cardKey",r.getString("card_key"));m.put("category",r.getString("category"));m.put("leftLabel",r.getString("left_label"));m.put("rightLabel",r.getString("right_label"));m.put("target",r.getInt("target"));try{m.put("status",r.getString("status"));m.put("model",r.getString("model"));}catch(SQLException ignored){m.put("status","PUBLISHED");}return m;}
}
