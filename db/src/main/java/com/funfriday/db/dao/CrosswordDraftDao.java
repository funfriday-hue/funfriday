package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import java.sql.*;
import java.util.*;

/** Draft-only staging for generated crosswords. Promotion is an explicit admin action. */
public class CrosswordDraftDao {
  private final DatabaseConnectionProvider cp;

  public CrosswordDraftDao(DatabaseConnectionProvider cp) {
    this.cp = cp;
  }

  public List<Map<String, Object>> list(String status) throws SQLException {
    String sql =
        "SELECT"
            + " id,draft_key,category,title,width,height,grid_json,entries_json,model,status,decline_reason,created_at"
            + " FROM crossword_drafts WHERE status=? ORDER BY created_at DESC";
    List<Map<String, Object>> out = new ArrayList<>();
    try (Connection c = cp.getConnection();
        PreparedStatement s = c.prepareStatement(sql)) {
      s.setString(1, status);
      try (ResultSet r = s.executeQuery()) {
        while (r.next()) out.add(row(r));
      }
    }
    return out;
  }

  public long create(
      String key,
      String category,
      String title,
      int width,
      int height,
      String grid,
      String entries,
      String model)
      throws SQLException {
    try (Connection c = cp.getConnection();
        PreparedStatement s =
            c.prepareStatement(
                "INSERT INTO"
                    + " crossword_drafts(draft_key,category,title,width,height,grid_json,entries_json,model)"
                    + " VALUES(?,?,?,?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
      s.setString(1, key);
      s.setString(2, category);
      s.setString(3, title);
      s.setInt(4, width);
      s.setInt(5, height);
      s.setString(6, grid);
      s.setString(7, entries);
      s.setString(8, model);
      s.executeUpdate();
      try (ResultSet k = s.getGeneratedKeys()) {
        k.next();
        return k.getLong(1);
      }
    }
  }

  public boolean update(long id, String title, String grid, String entries) throws SQLException {
    try (Connection c = cp.getConnection();
        PreparedStatement s =
            c.prepareStatement(
                "UPDATE crossword_drafts SET title=?,grid_json=?,entries_json=? WHERE id=? AND"
                    + " status='DRAFT'")) {
      s.setString(1, title);
      s.setString(2, grid);
      s.setString(3, entries);
      s.setLong(4, id);
      return s.executeUpdate() == 1;
    }
  }

  public boolean decline(long id, String reason) throws SQLException {
    try (Connection c = cp.getConnection();
        PreparedStatement s =
            c.prepareStatement(
                "UPDATE crossword_drafts SET"
                    + " status='DECLINED',decline_reason=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?"
                    + " AND status='DRAFT'")) {
      s.setString(1, reason);
      s.setLong(2, id);
      return s.executeUpdate() == 1;
    }
  }

  public List<Map<String, Object>> listPublished() throws SQLException {
    List<Map<String, Object>> out = new ArrayList<>();
    try (Connection c = cp.getConnection();
        PreparedStatement s =
            c.prepareStatement(
                "SELECT id,puzzle_key,category,title,width,height,grid_json,created_at FROM"
                    + " crossword_puzzles WHERE is_active=TRUE ORDER BY id DESC");
        ResultSet r = s.executeQuery()) {
      while (r.next()) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getLong("id"));
        m.put("puzzleKey", r.getString("puzzle_key"));
        m.put("category", r.getString("category"));
        m.put("title", r.getString("title"));
        m.put("width", r.getInt("width"));
        m.put("height", r.getInt("height"));
        m.put("gridJson", r.getString("grid_json"));
        m.put("createdAt", r.getTimestamp("created_at"));
        out.add(m);
      }
    }
    return out;
  }

  public boolean updatePublished(
      long id, String title, String grid, List<Map<String, Object>> entries) throws SQLException {
    try (Connection c = cp.getConnection()) {
      c.setAutoCommit(false);
      try {
        try (PreparedStatement s =
            c.prepareStatement(
                "UPDATE crossword_puzzles SET title=?,grid_json=? WHERE id=? AND is_active=TRUE")) {
          s.setString(1, title);
          s.setString(2, grid);
          s.setLong(3, id);
          if (s.executeUpdate() != 1) {
            c.rollback();
            return false;
          }
        }
        if (entries != null) {
          try (PreparedStatement del =
              c.prepareStatement("DELETE FROM crossword_entries WHERE puzzle_id=?")) {
            del.setLong(1, id);
            del.executeUpdate();
          }
          try (PreparedStatement s =
              c.prepareStatement(
                  "INSERT INTO"
                      + " crossword_entries(puzzle_id,clue_number,direction,row_index,column_index,answer,clue)"
                      + " VALUES(?,?,?,?,?,?,?)")) {
            for (Map<String, Object> e : entries) {
              s.setLong(1, id);
              s.setInt(2, ((Number) e.get("number")).intValue());
              s.setString(3, String.valueOf(e.get("direction")));
              s.setInt(4, ((Number) e.get("row")).intValue());
              s.setInt(5, ((Number) e.get("column")).intValue());
              s.setString(6, String.valueOf(e.get("answer")));
              s.setString(7, String.valueOf(e.get("clue")));
              s.addBatch();
            }
            s.executeBatch();
          }
        }
        c.commit();
        return true;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  public boolean approve(long id, List<Map<String, Object>> entries) throws SQLException {
    try (Connection c = cp.getConnection()) {
      c.setAutoCommit(false);
      try {
        Map<String, Object> d = null;
        try (PreparedStatement s =
            c.prepareStatement(
                "SELECT draft_key,category,title,width,height,grid_json,entries_json FROM"
                    + " crossword_drafts WHERE id=? AND status='DRAFT' FOR UPDATE")) {
          s.setLong(1, id);
          try (ResultSet r = s.executeQuery()) {
            if (!r.next()) {
              c.rollback();
              return false;
            }
            d = row(r);
          }
        }
        long puzzle;
        try (PreparedStatement s =
            c.prepareStatement(
                "INSERT INTO"
                    + " crossword_puzzles(puzzle_key,category,title,width,height,grid_json,is_active)"
                    + " VALUES(?,?,?,?,?,?,TRUE)",
                Statement.RETURN_GENERATED_KEYS)) {
          s.setString(1, (String) d.get("draftKey"));
          s.setString(2, (String) d.get("category"));
          s.setString(3, (String) d.get("title"));
          s.setInt(4, ((Number) d.get("width")).intValue());
          s.setInt(5, ((Number) d.get("height")).intValue());
          s.setString(6, (String) d.get("gridJson"));
          s.executeUpdate();
          try (ResultSet k = s.getGeneratedKeys()) {
            k.next();
            puzzle = k.getLong(1);
          }
        }
        try (PreparedStatement s =
            c.prepareStatement(
                "INSERT INTO"
                    + " crossword_entries(puzzle_id,clue_number,direction,row_index,column_index,answer,clue)"
                    + " VALUES(?,?,?,?,?,?,?)")) {
          for (Map<String, Object> e : entries) {
            s.setLong(1, puzzle);
            s.setInt(2, ((Number) e.get("number")).intValue());
            s.setString(3, String.valueOf(e.get("direction")));
            s.setInt(4, ((Number) e.get("row")).intValue());
            s.setInt(5, ((Number) e.get("column")).intValue());
            s.setString(6, String.valueOf(e.get("answer")));
            s.setString(7, String.valueOf(e.get("clue")));
            s.addBatch();
          }
          s.executeBatch();
        }
        try (PreparedStatement s =
            c.prepareStatement(
                "UPDATE crossword_drafts SET status='APPROVED',reviewed_at=CURRENT_TIMESTAMP WHERE"
                    + " id=?")) {
          s.setLong(1, id);
          s.executeUpdate();
        }
        c.commit();
        return true;
      } catch (Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  private Map<String, Object> row(ResultSet r) throws SQLException {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getLong("id"));
    for (String[] p :
        new String[][] {
          {"draft_key", "draftKey"},
          {"category", "category"},
          {"title", "title"},
          {"grid_json", "gridJson"},
          {"entries_json", "entriesJson"}
        }) m.put(p[1], r.getString(p[0]));
    try {
      m.put("width", r.getInt("width"));
      m.put("height", r.getInt("height"));
    } catch (SQLException ignored) {
    }
    try {
      m.put("model", r.getString("model"));
      m.put("status", r.getString("status"));
      m.put("createdAt", r.getTimestamp("created_at"));
    } catch (SQLException ignored) {
    }
    return m;
  }
}
