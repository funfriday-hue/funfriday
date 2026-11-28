package com.funfriday.db.dao;

import com.funfriday.db.DatabaseConnectionProvider;
import com.funfriday.db.model.WavelengthCardRecord;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/** Active, player-facing Wavelength cards. The target is never exposed by this DAO to a public API. */
public class WavelengthCardDao {
  private final DatabaseConnectionProvider cp;
  public WavelengthCardDao(DatabaseConnectionProvider cp) { this.cp = cp; }

  public List<WavelengthCardRecord> randomActive(int limit) throws Exception {
    List<WavelengthCardRecord> cards = new ArrayList<>();
    try (Connection connection = cp.getConnection();
         PreparedStatement statement = connection.prepareStatement(
             "SELECT id,card_key,category,left_label,right_label,target FROM wavelength_cards WHERE is_active=TRUE ORDER BY RAND() LIMIT ?")) {
      statement.setInt(1, Math.max(1, limit));
      try (ResultSet result = statement.executeQuery()) {
        while (result.next()) cards.add(new WavelengthCardRecord(result.getLong(1), result.getString(2),
            result.getString(3), result.getString(4), result.getString(5), result.getInt(6)));
      }
    }
    return cards;
  }
}
